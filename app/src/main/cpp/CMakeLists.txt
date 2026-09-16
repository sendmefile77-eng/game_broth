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

# Android API 26's libvulkan link stub does not export the Vulkan 1.1 core symbol
# vkGetPhysicalDeviceFeatures2, while the pinned ggml-vulkan source calls it directly
# in three places. The backend already owns and initializes a Vulkan-Hpp dynamic
# dispatcher through vkGetInstanceProcAddr; route those three feature queries through
# that dispatcher as well. This keeps our minSdk unchanged and avoids binding a 1.1
# entry point at link/load time. The source patch is intentionally strict and
# idempotent so an upstream change cannot silently produce a broken APK.
if(ANDROID AND SD_VULKAN)
    set(GGML_VULKAN_CPP "${SDCPP_DIR}/ggml/src/ggml-vulkan/ggml-vulkan.cpp")
    if(NOT EXISTS "${GGML_VULKAN_CPP}")
        message(FATAL_ERROR "Pinned ggml-vulkan source is missing: ${GGML_VULKAN_CPP}")
    endif()

    file(READ "${GGML_VULKAN_CPP}" GGML_VULKAN_SOURCE)
    string(REGEX MATCHALL "[ \t\n]vkGetPhysicalDeviceFeatures2\\(" GGML_VULKAN_DIRECT_FEATURE_CALLS "${GGML_VULKAN_SOURCE}")
    list(LENGTH GGML_VULKAN_DIRECT_FEATURE_CALLS GGML_VULKAN_DIRECT_FEATURE_CALL_COUNT)
    string(REGEX MATCHALL "VULKAN_HPP_DEFAULT_DISPATCHER\\.vkGetPhysicalDeviceFeatures2\\(" GGML_VULKAN_DISPATCH_FEATURE_CALLS "${GGML_VULKAN_SOURCE}")
    list(LENGTH GGML_VULKAN_DISPATCH_FEATURE_CALLS GGML_VULKAN_DISPATCH_FEATURE_CALL_COUNT)

    if(GGML_VULKAN_DIRECT_FEATURE_CALL_COUNT EQUAL 3)
        string(REPLACE
            "vkGetPhysicalDeviceFeatures2("
            "VULKAN_HPP_DEFAULT_DISPATCHER.vkGetPhysicalDeviceFeatures2("
            GGML_VULKAN_SOURCE_PATCHED
            "${GGML_VULKAN_SOURCE}"
        )
        file(WRITE "${GGML_VULKAN_CPP}" "${GGML_VULKAN_SOURCE_PATCHED}")
        message(STATUS "Patched 3 Android Vulkan feature queries to use the dynamic dispatcher")
    elseif(GGML_VULKAN_DIRECT_FEATURE_CALL_COUNT EQUAL 0 AND GGML_VULKAN_DISPATCH_FEATURE_CALL_COUNT EQUAL 3)
        message(STATUS "Android Vulkan dynamic-dispatch patch already applied")
    else()
        message(FATAL_ERROR
            "Unexpected ggml-vulkan feature-query layout: direct=${GGML_VULKAN_DIRECT_FEATURE_CALL_COUNT}, "
            "dispatched=${GGML_VULKAN_DISPATCH_FEATURE_CALL_COUNT}. Refusing an unsafe build."
        )
    endif()
endif()

# The selected diffusion model uses the ComfyUI-GGUF dialect: tensors are flattened
# into 256-element storage rows and their logical SDXL shapes are kept in per-tensor
# metadata. Apply one reviewed compatibility patch to the pinned engine instead of
# accumulating shape-specific source rewrites.
if(ANDROID)
    find_package(Git REQUIRED)
    set(SDCPP_EXPECTED_REVISION "59c23bce0d82be3a922023ab811194f05b3e2faa")
    set(SDCPP_COMPAT_PATCH
        "${CMAKE_CURRENT_SOURCE_DIR}/patches/stable-diffusion-comfy-gguf-sdxl.patch"
    )

    execute_process(
        COMMAND "${GIT_EXECUTABLE}" rev-parse HEAD
        WORKING_DIRECTORY "${SDCPP_DIR}"
        OUTPUT_VARIABLE SDCPP_REVISION
        OUTPUT_STRIP_TRAILING_WHITESPACE
        RESULT_VARIABLE SDCPP_REVISION_RESULT
    )
    if(NOT SDCPP_REVISION_RESULT EQUAL 0 OR NOT SDCPP_REVISION STREQUAL SDCPP_EXPECTED_REVISION)
        message(FATAL_ERROR
            "Unexpected stable-diffusion.cpp revision '${SDCPP_REVISION}'; "
            "expected ${SDCPP_EXPECTED_REVISION}"
        )
    endif()
    if(NOT EXISTS "${SDCPP_COMPAT_PATCH}")
        message(FATAL_ERROR "Missing stable-diffusion.cpp compatibility patch: ${SDCPP_COMPAT_PATCH}")
    endif()

    execute_process(
        COMMAND "${GIT_EXECUTABLE}" apply --check "${SDCPP_COMPAT_PATCH}"
        WORKING_DIRECTORY "${SDCPP_DIR}"
        RESULT_VARIABLE SDCPP_PATCH_CAN_APPLY
        OUTPUT_QUIET
        ERROR_QUIET
    )
    if(SDCPP_PATCH_CAN_APPLY EQUAL 0)
        execute_process(
            COMMAND "${GIT_EXECUTABLE}" apply --whitespace=nowarn "${SDCPP_COMPAT_PATCH}"
            WORKING_DIRECTORY "${SDCPP_DIR}"
            RESULT_VARIABLE SDCPP_PATCH_RESULT
        )
        if(NOT SDCPP_PATCH_RESULT EQUAL 0)
            message(FATAL_ERROR "Failed to apply stable-diffusion.cpp compatibility patch")
        endif()
        message(STATUS "Applied split SDXL ComfyUI-GGUF compatibility patch")
    else()
        execute_process(
            COMMAND "${GIT_EXECUTABLE}" apply --reverse --check "${SDCPP_COMPAT_PATCH}"
            WORKING_DIRECTORY "${SDCPP_DIR}"
            RESULT_VARIABLE SDCPP_PATCH_ALREADY_APPLIED
            OUTPUT_QUIET
            ERROR_QUIET
        )
        if(NOT SDCPP_PATCH_ALREADY_APPLIED EQUAL 0)
            message(FATAL_ERROR
                "stable-diffusion.cpp sources match neither the pinned revision nor its compatibility patch"
            )
        endif()
        message(STATUS "Split SDXL ComfyUI-GGUF compatibility patch already applied")
    endif()
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