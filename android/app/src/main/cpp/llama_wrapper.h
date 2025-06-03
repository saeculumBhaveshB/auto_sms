#ifndef LLAMA_WRAPPER_H
#define LLAMA_WRAPPER_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Initialize the Phi-3-mini model
 * 
 * @param env JNI environment
 * @param thiz The Java object instance
 * @param modelPath Path to the GGUF model file
 * @return true if successful, false otherwise
 */
JNIEXPORT jboolean JNICALL
Java_com_auto_1sms_llm_Phi3MiniModule_initModel(JNIEnv *env, jobject thiz, jstring modelPath);

/**
 * Generate text from the model
 * 
 * @param env JNI environment
 * @param thiz The Java object instance
 * @param prompt The input prompt
 * @param maxTokens Maximum tokens to generate
 * @param temperature Sampling temperature (0.0-1.0)
 * @param topP Top-p sampling parameter (0.0-1.0)
 * @return Generated text as Java string
 */
JNIEXPORT jstring JNICALL
Java_com_auto_1sms_llm_Phi3MiniModule_generateText(JNIEnv *env, jobject thiz,
                                                   jstring prompt, jint maxTokens,
                                                   jfloat temperature, jfloat topP);

/**
 * Free model resources
 * 
 * @param env JNI environment
 * @param thiz The Java object instance
 */
JNIEXPORT void JNICALL
Java_com_auto_1sms_llm_Phi3MiniModule_freeModel(JNIEnv *env, jobject thiz);

/**
 * Check if the model is loaded
 * 
 * @param env JNI environment
 * @param thiz The Java object instance
 * @return true if model is loaded, false otherwise
 */
JNIEXPORT jboolean JNICALL
Java_com_auto_1sms_llm_Phi3MiniModule_isModelLoaded(JNIEnv *env, jobject thiz);

#ifdef __cplusplus
}
#endif

#endif // LLAMA_WRAPPER_H 