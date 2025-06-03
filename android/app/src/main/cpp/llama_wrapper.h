#pragma once

#include <jni.h>
#include <string>

// JNI function declarations for Phi3MiniModule
extern "C" {
    JNIEXPORT jboolean JNICALL Java_com_auto_1sms_llm_Phi3MiniModule_initModel(JNIEnv* env, jobject thiz, jstring modelPath);
    
    JNIEXPORT jstring JNICALL Java_com_auto_1sms_llm_Phi3MiniModule_generateText(JNIEnv* env, jobject thiz, 
                                                                               jstring prompt, jint maxTokens,
                                                                               jfloat temperature, jfloat topP);
    
    JNIEXPORT void JNICALL Java_com_auto_1sms_llm_Phi3MiniModule_freeModel(JNIEnv* env, jobject thiz);
    
    JNIEXPORT jboolean JNICALL Java_com_auto_1sms_llm_Phi3MiniModule_isModelLoaded(JNIEnv* env, jobject thiz);
} 