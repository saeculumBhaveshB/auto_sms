cmake_minimum_required(VERSION 3.18.0)
project(phi3mini C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_C_STANDARD 11)

# Define source files directories
set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama)

# Check if llama directory exists and create stub files if necessary
if(NOT EXISTS ${LLAMA_DIR})
    message(WARNING "Llama directory not found at ${LLAMA_DIR}. Creating stub files.")
    file(MAKE_DIRECTORY ${LLAMA_DIR})
    file(MAKE_DIRECTORY ${LLAMA_DIR}/common)
    
    # Create stub C/C++ files
    file(WRITE ${LLAMA_DIR}/ggml.c "#include <stdint.h>\n#include <stdio.h>\n\nvoid ggml_stub() { printf(\"ggml stub\\n\"); }\n")
    file(WRITE ${LLAMA_DIR}/llama.cpp "#include <stdint.h>\n#include <stdio.h>\n\nvoid llama_stub() { printf(\"llama stub\\n\"); }\n")
    file(WRITE ${LLAMA_DIR}/common/common.cpp "#include <stdint.h>\n#include <stdio.h>\n\nvoid common_stub() { printf(\"common stub\\n\"); }\n")
    file(WRITE ${LLAMA_DIR}/common/sampling.cpp "#include <stdint.h>\n#include <stdio.h>\n\nvoid sampling_stub() { printf(\"sampling stub\\n\"); }\n")
    
    # Create stub header files
    file(WRITE ${LLAMA_DIR}/llama.h "#pragma once\n#ifdef __cplusplus\nextern \"C\" {\n#endif\n\nvoid llama_stub();\n\n#ifdef __cplusplus\n}\n#endif\n")
    file(WRITE ${LLAMA_DIR}/ggml.h "#pragma once\n#ifdef __cplusplus\nextern \"C\" {\n#endif\n\nvoid ggml_stub();\n\n#ifdef __cplusplus\n}\n#endif\n")
    file(WRITE ${LLAMA_DIR}/common/common.h "#pragma once\n\nvoid common_stub();\n")
    file(WRITE ${LLAMA_DIR}/common/sampling.h "#pragma once\n\nvoid sampling_stub();\n")
endif()

# Include directories
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${LLAMA_DIR}
)

# Define source files
set(LLAMA_SOURCES
    ${LLAMA_DIR}/ggml.c
    ${LLAMA_DIR}/llama.cpp
    ${LLAMA_DIR}/common/common.cpp
    ${LLAMA_DIR}/common/sampling.cpp
)

# Create a stub implementation of llama_wrapper.cpp if it doesn't exist
if(NOT EXISTS ${CMAKE_CURRENT_SOURCE_DIR}/llama_wrapper.cpp)
    message(WARNING "llama_wrapper.cpp not found. Creating stub file.")
    file(WRITE ${CMAKE_CURRENT_SOURCE_DIR}/llama_wrapper.cpp 
        "#include <jni.h>\n#include <string>\n#include <android/log.h>\n\n"
        "#define TAG \"PhiLLM_Stub\"\n"
        "#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)\n\n"
        "extern \"C\" {\n"
        "    JNIEXPORT jboolean JNICALL Java_com_auto_1sms_llm_Phi3MiniModule_initModel(JNIEnv* env, jobject thiz, jstring modelPath) {\n"
        "        LOGE(\"Native library stub called: initModel\");\n"
        "        return JNI_FALSE;\n"
        "    }\n\n"
        "    JNIEXPORT jstring JNICALL Java_com_auto_1sms_llm_Phi3MiniModule_generateText(JNIEnv* env, jobject thiz, jstring prompt, jint maxTokens, jfloat temperature, jfloat topP) {\n"
        "        LOGE(\"Native library stub called: generateText\");\n"
        "        return env->NewStringUTF(\"Error: Native library not properly implemented\");\n"
        "    }\n\n"
        "    JNIEXPORT void JNICALL Java_com_auto_1sms_llm_Phi3MiniModule_freeModel(JNIEnv* env, jobject thiz) {\n"
        "        LOGE(\"Native library stub called: freeModel\");\n"
        "    }\n\n"
        "    JNIEXPORT jboolean JNICALL Java_com_auto_1sms_llm_Phi3MiniModule_isModelLoaded(JNIEnv* env, jobject thiz) {\n"
        "        LOGE(\"Native library stub called: isModelLoaded\");\n"
        "        return JNI_FALSE;\n"
        "    }\n"
        "}\n"
    )
endif()

# Add define for stub implementation so we can check in code
add_definitions(
    -DUSING_LLAMA_STUB_IMPLEMENTATION=1
    -DGGML_USE_K_QUANTS
)

# Define llama library
add_library(llama SHARED
    ${LLAMA_SOURCES}
    llama_wrapper.cpp
)

# Find and link required system libraries
find_library(log-lib log)
find_library(android-lib android)

# Link libraries
target_link_libraries(llama
    ${log-lib}
    ${android-lib}
)

# Enable optimizations for release builds
if(CMAKE_BUILD_TYPE MATCHES Release)
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -ffast-math")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math")
else()
    # Use more debug-friendly flags for debug builds
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O0 -g")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O0 -g")
endif()

# Support for ARM NEON instructions
if(ANDROID_ABI STREQUAL "armeabi-v7a" OR ANDROID_ABI STREQUAL "arm64-v8a")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -mfpu=neon")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -mfpu=neon")
endif()

# Support for x86 SIMD
if(ANDROID_ABI STREQUAL "x86" OR ANDROID_ABI STREQUAL "x86_64")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -msse3")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -msse3")
endif()

message(STATUS "Building library with flags: ${CMAKE_CXX_FLAGS}") 