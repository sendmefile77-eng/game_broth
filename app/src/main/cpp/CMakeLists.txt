cmake_minimum_required(VERSION 3.22.1)
project(gamebroth_diffusion LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# We embed only the inference library. Example servers/image codecs are unnecessary in the APK.
set(SD_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(SD_BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(SD_BUILD_SHARED_GGML_LIB OFF CACHE BOOL "" FORCE)
set(SD_WEBP OFF CACHE BOOL "" FORCE)
set(SD_WEBM OFF CACHE BOOL "" FORCE)
set(SD_CUDA OFF CACHE BOOL "" FORCE)
set(SD_METAL OFF CACHE BOOL "" FORCE)
# Android fast path: use the Adreno GPU through Vulkan instead of CPU-only inference.
set(SD_VULKAN ON CACHE BOOL "" FORCE)
set(SD_OPENCL OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN_RUN_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN_VALIDATE OFF CACHE BOOL "" FORCE)
set(VK_USE_PLATFORM_ANDROID_KHR ON CACHE BOOL "" FORCE)

# The Android NDK provides libvulkan and the C API headers but not the header-only
# Vulkan-Hpp C++ wrapper used by the pinned ggml-vulkan backend. CI installs the
# official Khronos Vulkan-Headers checkout and exposes its include root here.
if(DEFINED ENV{VULKAN_HEADERS_INCLUDE_DIR})
    if(NOT EXISTS "$ENV{VULKAN_HEADERS_INCLUDE_DIR}/vulkan/vulkan.hpp")
        message(FATAL_ERROR "VULKAN_HEADERS_INCLUDE_DIR does not contain vulkan/vulkan.hpp")
    endif()
    include_directories(SYSTEM "$ENV{VULKAN_HEADERS_INCLUDE_DIR}")
    message(STATUS "Using Vulkan-Hpp headers from $ENV{VULKAN_HEADERS_INCLUDE_DIR}")
endif()

set(SDCPP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/stable-diffusion.cpp)
if(NOT EXISTS ${SDCPP_DIR}/CMakeLists.txt)
    message(FATAL_ERROR "stable-diffusion.cpp submodule is missing. Checkout with submodules recursively.")
endif()

add_subdirectory(${SDCPP_DIR} ${CMAKE_BINARY_DIR}/stable-diffusion EXCLUDE_FROM_ALL)

add_library(gamebroth_diffusion SHARED gamebroth_diffusion_jni.cpp)
target_include_directories(gamebroth_diffusion PRIVATE ${SDCPP_DIR}/include)

find_library(log-lib log)
target_link_libraries(gamebroth_diffusion PRIVATE stable-diffusion ${log-lib})
