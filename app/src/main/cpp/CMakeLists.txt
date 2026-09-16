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

# The pinned ggml-vulkan backend has exactly two header-only build dependencies that
# are not fully supplied by the Android NDK toolchain used by AGP: Vulkan-Hpp and
# SPIR-V Headers. CI exposes both roots. Validate them before the expensive native
# build so failures are immediate and deterministic.
set(GAMEBROTH_VULKAN_INCLUDE "")
set(GAMEBROTH_SPIRV_INCLUDE "")

if(DEFINED ENV{VULKAN_HEADERS_INCLUDE_DIR})
    set(GAMEBROTH_VULKAN_INCLUDE "$ENV{VULKAN_HEADERS_INCLUDE_DIR}")
    if(NOT EXISTS "${GAMEBROTH_VULKAN_INCLUDE}/vulkan/vulkan.hpp")
        message(FATAL_ERROR "VULKAN_HEADERS_INCLUDE_DIR does not contain vulkan/vulkan.hpp")
    endif()
endif()

if(DEFINED ENV{SPIRV_HEADERS_INCLUDE_DIR})
    set(GAMEBROTH_SPIRV_INCLUDE "$ENV{SPIRV_HEADERS_INCLUDE_DIR}")
    if(NOT EXISTS "${GAMEBROTH_SPIRV_INCLUDE}/spirv/unified1/spirv.hpp")
        message(FATAL_ERROR "SPIRV_HEADERS_INCLUDE_DIR does not contain spirv/unified1/spirv.hpp")
    endif()
endif()

# Directory-scope includes propagate into the stable-diffusion/ggml subdirectories.
# We additionally attach them to ggml-vulkan below after the target exists, so this
# remains robust if upstream changes include propagation.
if(GAMEBROTH_VULKAN_INCLUDE)
    include_directories(SYSTEM "${GAMEBROTH_VULKAN_INCLUDE}")
    message(STATUS "Using Vulkan-Hpp headers from ${GAMEBROTH_VULKAN_INCLUDE}")
endif()
if(GAMEBROTH_SPIRV_INCLUDE)
    include_directories(SYSTEM "${GAMEBROTH_SPIRV_INCLUDE}")
    message(STATUS "Using SPIR-V headers from ${GAMEBROTH_SPIRV_INCLUDE}")
endif()

set(SDCPP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/stable-diffusion.cpp)
if(NOT EXISTS ${SDCPP_DIR}/CMakeLists.txt)
    message(FATAL_ERROR "stable-diffusion.cpp submodule is missing. Checkout with submodules recursively.")
endif()

add_subdirectory(${SDCPP_DIR} ${CMAKE_BINARY_DIR}/stable-diffusion EXCLUDE_FROM_ALL)

if(TARGET ggml-vulkan)
    if(GAMEBROTH_VULKAN_INCLUDE)
        target_include_directories(ggml-vulkan SYSTEM PRIVATE "${GAMEBROTH_VULKAN_INCLUDE}")
    endif()
    if(GAMEBROTH_SPIRV_INCLUDE)
        target_include_directories(ggml-vulkan SYSTEM PRIVATE "${GAMEBROTH_SPIRV_INCLUDE}")
    endif()
endif()

add_library(gamebroth_diffusion SHARED gamebroth_diffusion_jni.cpp)
target_include_directories(gamebroth_diffusion PRIVATE ${SDCPP_DIR}/include)

find_library(log-lib log)
target_link_libraries(gamebroth_diffusion PRIVATE stable-diffusion ${log-lib})
