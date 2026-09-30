cmake_minimum_required(VERSION 3.22.1)
project("llama_jni")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Build type
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

# Optimization flags for Android
set(CMAKE_CXX_FLAGS_RELEASE "-O3 -DNDEBUG -fPIC")
set(CMAKE_C_FLAGS_RELEASE "-O3 -DNDEBUG -fPIC")

# 16 KB page size alignment (required for Google Play / Android 15+)
add_link_options(-Wl,-z,max-page-size=16384)

# Find required Android libraries
find_library(log-lib log)
find_library(android-lib android)

# ============================================================
# llama.cpp configuration
# ============================================================
set(LLAMA_CPP_DIR "${CMAKE_SOURCE_DIR}/../../../../lib/llama.cpp")

# Disable unused features, enable Vulkan for GPU acceleration
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(LLAMA_METAL OFF CACHE BOOL "" FORCE)
set(LLAMA_CUDA OFF CACHE BOOL "" FORCE)
set(LLAMA_HIPBLAS OFF CACHE BOOL "" FORCE)
set(LLAMA_NATIVE OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# Disable OpenMP — eliminates libomp.so (marginal benefit on mobile, avoids 16KB alignment issues)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

# GPU backends — all disabled; CPU-only for small models on budget phones
set(LLAMA_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(LLAMA_METAL OFF CACHE BOOL "" FORCE)
set(LLAMA_CUDA OFF CACHE BOOL "" FORCE)

# Add llama.cpp as subdirectory
add_subdirectory(${LLAMA_CPP_DIR} llama.cpp.build)

# ============================================================
# JNI Bridge Library
# ============================================================
add_library(llama_jni SHARED
    llama_jni.cpp
)

target_include_directories(llama_jni PRIVATE
    ${CMAKE_SOURCE_DIR}
    ${LLAMA_CPP_DIR}/include
    ${LLAMA_CPP_DIR}/ggml/include
)

target_link_libraries(llama_jni
    ${log-lib}
    ${android-lib}
    llama
)

# Compiler warnings
target_compile_options(llama_jni PRIVATE
    -Wall
    -Wextra
)
