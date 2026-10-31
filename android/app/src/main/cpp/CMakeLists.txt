cmake_minimum_required(VERSION 3.22.1)
project(handywhisper LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# whisper.cpp is fetched by android/scripts/fetch-whisper.sh (pinned tag).
set(WHISPER_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/whisper.cpp")
if(NOT EXISTS "${WHISPER_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "whisper.cpp missing. Run android/scripts/fetch-whisper.sh first.")
endif()

# Shared whisper + ggml with runtime-selected CPU backend variants: ggml builds
# several libggml-cpu-android_armv8.*.so modules (dotprod, fp16, i8mm, ...) and
# picks the best one the device supports when they are loaded (CPU only).
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL ON CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)

add_subdirectory("${WHISPER_DIR}" whisper.cpp-build)

add_library(handywhisper SHARED whisper_jni.cpp)
target_link_libraries(handywhisper whisper log)
