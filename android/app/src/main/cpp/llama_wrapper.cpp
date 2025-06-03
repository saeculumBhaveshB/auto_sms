#include "llama_wrapper.h"
#include "llama.h"
#include "common.h"
#include <string>
#include <vector>
#include <thread>
#include <mutex>
#include <condition_variable>
#include <queue>
#include <android/log.h>

// Logging macros
#define TAG "PhiLLM"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Global variables to manage the model state
namespace {
    std::mutex g_mutex;
    bool g_modelLoaded = false;
    llama_context* g_ctx = nullptr;
    llama_model* g_model = nullptr;
    std::string g_generatedText;
    bool g_generationComplete = false;
    std::condition_variable g_cv;
    
    // Worker thread for generation
    std::thread g_worker;
    bool g_workerRunning = false;
    std::queue<std::string> g_promptQueue;
    std::mutex g_queueMutex;
    std::condition_variable g_queueCv;
    
    // Generation parameters
    int32_t g_maxTokens = 512;
    float g_temperature = 0.7f;
    float g_topP = 0.9f;
    
    // Function to convert jstring to std::string
    std::string jstring_to_string(JNIEnv* env, jstring jStr) {
        if (!jStr) return "";
        
        const char* chars = env->GetStringUTFChars(jStr, nullptr);
        std::string result(chars);
        env->ReleaseStringUTFChars(jStr, chars);
        
        return result;
    }
    
    // Worker thread function for text generation
    void worker_thread() {
        LOGI("Worker thread started");
        
        while (g_workerRunning) {
            std::string prompt;
            
            {
                std::unique_lock<std::mutex> lock(g_queueMutex);
                g_queueCv.wait(lock, []{ return !g_promptQueue.empty() || !g_workerRunning; });
                
                if (!g_workerRunning) {
                    LOGI("Worker thread stopping");
                    break;
                }
                
                prompt = g_promptQueue.front();
                g_promptQueue.pop();
            }
            
            if (g_ctx == nullptr || g_model == nullptr) {
                LOGE("Model not initialized");
                continue;
            }
            
            LOGI("Generating text for prompt: %s", prompt.c_str());
            
            // Reset the model state for a new generation
            llama_reset_timings(g_ctx);
            
            // Tokenize the prompt
            auto tokens = llama_tokenize(g_model, prompt.c_str(), prompt.length(), true);
            
            if (tokens.empty()) {
                LOGE("Failed to tokenize prompt");
                continue;
            }
            
            LOGI("Prompt has %d tokens", tokens.size());
            
            // Evaluate the prompt
            if (llama_eval(g_ctx, tokens.data(), tokens.size(), 0, 1)) {
                LOGE("Failed to evaluate prompt");
                continue;
            }
            
            // Generate tokens
            std::string result;
            int32_t n_gen = 0;
            
            llama_token id = 0;
            
            // Generation parameters
            llama_sampling_params params;
            params.temp = g_temperature;
            params.top_p = g_topP;
            params.repeat_penalty = 1.1f;
            params.mirostat = 0;
            
            llama_sampling_context* ctx_sampling = llama_sampling_init(params);
            
            while (n_gen < g_maxTokens) {
                // Sample a token
                id = llama_sampling_sample(ctx_sampling, g_ctx, {});
                
                // Check for end of generation
                if (id == llama_token_eos(g_model)) {
                    LOGI("End of generation");
                    break;
                }
                
                // Convert token to string
                const char* token_str = llama_token_to_piece(g_ctx, id);
                if (token_str == nullptr) {
                    LOGE("Failed to convert token to string");
                    continue;
                }
                
                // Append token text to result
                result += token_str;
                
                // Log every few tokens
                if (n_gen % 10 == 0) {
                    LOGD("Generated %d tokens: %s", n_gen, result.c_str());
                }
                
                // Feed the token back to the model
                if (llama_eval(g_ctx, &id, 1, tokens.size() + n_gen, 1)) {
                    LOGE("Failed to evaluate token");
                    break;
                }
                
                n_gen++;
            }
            
            llama_sampling_free(ctx_sampling);
            
            {
                std::lock_guard<std::mutex> lock(g_mutex);
                g_generatedText = result;
                g_generationComplete = true;
            }
            
            LOGI("Text generation complete: %s", result.c_str());
            g_cv.notify_all();
        }
        
        LOGI("Worker thread exited");
    }
}

// Implementation for JNI functions

JNIEXPORT jboolean JNICALL
Java_com_auto_1sms_llm_Phi3MiniModule_initModel(JNIEnv *env, jobject thiz, jstring modelPath) {
    std::lock_guard<std::mutex> lock(g_mutex);
    
    // Convert Java string to C++ string
    const std::string path = jstring_to_string(env, modelPath);
    
    LOGI("Loading model from %s", path.c_str());
    
    // Free any existing model
    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    
    if (g_model != nullptr) {
        llama_free_model(g_model);
        g_model = nullptr;
    }
    
    // Set up model parameters
    llama_model_params model_params = llama_model_default_params();
    
    // Load the model
    g_model = llama_load_model_from_file(path.c_str(), model_params);
    
    if (!g_model) {
        LOGE("Failed to load model from %s", path.c_str());
        return JNI_FALSE;
    }
    
    // Set up context parameters
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 4096; // Context size, Phi-3-mini has 4K context
    ctx_params.n_batch = 512; // Batch size for processing
    ctx_params.n_threads = 4; // Number of threads to use
    
    // Create the context
    g_ctx = llama_new_context_with_model(g_model, ctx_params);
    
    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_free_model(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }
    
    g_modelLoaded = true;
    
    // Start the worker thread if not already running
    if (!g_workerRunning) {
        g_workerRunning = true;
        g_worker = std::thread(worker_thread);
    }
    
    LOGI("Model loaded successfully");
    
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_auto_1sms_llm_Phi3MiniModule_generateText(JNIEnv *env, jobject thiz,
                                                 jstring prompt, jint maxTokens,
                                                 jfloat temperature, jfloat topP) {
    if (!g_modelLoaded || !g_ctx || !g_model) {
        LOGE("Model not loaded");
        return env->NewStringUTF("Error: Model not loaded");
    }
    
    // Convert Java string to C++ string
    const std::string promptStr = jstring_to_string(env, prompt);
    
    LOGI("Generating text with prompt: %s, maxTokens: %d, temp: %f, topP: %f",
         promptStr.c_str(), maxTokens, temperature, topP);
    
    // Update generation parameters
    g_maxTokens = maxTokens;
    g_temperature = temperature;
    g_topP = topP;
    
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        g_generatedText = "";
        g_generationComplete = false;
    }
    
    // Add prompt to the queue
    {
        std::lock_guard<std::mutex> lock(g_queueMutex);
        g_promptQueue.push(promptStr);
        g_queueCv.notify_one();
    }
    
    // Wait for generation to complete with a timeout
    {
        std::unique_lock<std::mutex> lock(g_mutex);
        if (!g_cv.wait_for(lock, std::chrono::seconds(30), []{ return g_generationComplete; })) {
            LOGE("Text generation timed out");
            return env->NewStringUTF("AI: Generation timed out. Please try again.");
        }
    }
    
    // Format the response with "AI: " prefix if it doesn't already have it
    std::string result = g_generatedText;
    if (result.substr(0, 4) != "AI: " && result.substr(0, 3) != "AI:") {
        result = "AI: " + result;
    }
    
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_auto_1sms_llm_Phi3MiniModule_freeModel(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_mutex);
    
    LOGI("Freeing model resources");
    
    // Stop the worker thread
    if (g_workerRunning) {
        g_workerRunning = false;
        g_queueCv.notify_one();
        if (g_worker.joinable()) {
            g_worker.join();
        }
    }
    
    // Free the context and model
    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    
    if (g_model != nullptr) {
        llama_free_model(g_model);
        g_model = nullptr;
    }
    
    g_modelLoaded = false;
    LOGI("Model resources freed");
}

JNIEXPORT jboolean JNICALL
Java_com_auto_1sms_llm_Phi3MiniModule_isModelLoaded(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return static_cast<jboolean>(g_modelLoaded);
} 