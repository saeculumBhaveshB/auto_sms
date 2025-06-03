cmake_minimum_required(VERSION 3.18.0)
project(phi3mini C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_C_STANDARD 11)

# Define source files directories
set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama)

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

# Add defines for llama.cpp
add_definitions(
    -DGGML_USE_K_QUANTS
    -DGGML_USE_ACCELERATE
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

# Enable optimizations
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -ffast-math")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math")

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