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

# Split SDXL support for the pinned engine revision.
#
# Two upstream loader bugs interact here:
# 1) external CLIP-G is initially registered under the temporary "clip_g." alias, but
#    get_sd_version() did not count that alias as SDXL's second text encoder;
# 2) convert_tensors_name() detected a version for conversion but did not cache it.
#    init() then called get_sd_version() a second time on already-converted names. This
#    is a known upstream SDXL failure mode that ends as "model metadata validation failed".
#
# Keep the temporary aliases intact, teach the detector about clip_g, and cache the
# pre-conversion version exactly once. The canonical clip_l/clip_g -> SDXL names are
# still produced by upstream name_conversion.cpp, so metadata validation sees the
# names its model builders expect.
if(ANDROID)
    set(SD_MODEL_LOADER_CPP "${SDCPP_DIR}/src/model_loader.cpp")
    if(NOT EXISTS "${SD_MODEL_LOADER_CPP}")
        message(FATAL_ERROR "Pinned model loader source is missing: ${SD_MODEL_LOADER_CPP}")
    endif()

    file(READ "${SD_MODEL_LOADER_CPP}" SD_MODEL_LOADER_SOURCE)

    # A. Recognize external split CLIP-G as the second SDXL text encoder.
    set(SDXL_SPLIT_DETECT_OLD [=[tensor_storage.name.find("te.1") != std::string::npos)]=])
    set(SDXL_SPLIT_DETECT_NEW [=[tensor_storage.name.find("te.1") != std::string::npos ||
            tensor_storage.name.find("clip_g.") != std::string::npos)]=])
    string(FIND "${SD_MODEL_LOADER_SOURCE}" "${SDXL_SPLIT_DETECT_OLD}" SDXL_SPLIT_DETECT_OLD_POS)
    string(FIND "${SD_MODEL_LOADER_SOURCE}" "${SDXL_SPLIT_DETECT_NEW}" SDXL_SPLIT_DETECT_NEW_POS)
    if(SDXL_SPLIT_DETECT_OLD_POS GREATER -1 AND SDXL_SPLIT_DETECT_NEW_POS EQUAL -1)
        string(REPLACE
            "${SDXL_SPLIT_DETECT_OLD}"
            "${SDXL_SPLIT_DETECT_NEW}"
            SD_MODEL_LOADER_SOURCE
            "${SD_MODEL_LOADER_SOURCE}"
        )
        message(STATUS "Patched split SDXL detection for external CLIP-G")
    elseif(SDXL_SPLIT_DETECT_OLD_POS EQUAL -1 AND SDXL_SPLIT_DETECT_NEW_POS GREATER -1)
        message(STATUS "Split SDXL detector patch already applied")
    else()
        message(FATAL_ERROR
            "Unexpected stable-diffusion.cpp SDXL detector layout. Refusing an ambiguous split-model build."
        )
    endif()

    # B. Cache the version detected before tensor-name conversion. Upstream issue #1938
    # documents this exact SDXL metadata-validation failure and verifies this strategy.
    set(SDXL_VERSION_CACHE_OLD [=[void ModelLoader::convert_tensors_name() {
    if (names_converted_) {
        return;
    }
    names_converted_ = true;
    rebuild_catalog();
}]=])
    set(SDXL_VERSION_CACHE_NEW [=[void ModelLoader::convert_tensors_name() {
    if (names_converted_) {
        return;
    }
    if (version_ == VERSION_COUNT) {
        version_ = get_sd_version();
    }
    names_converted_ = true;
    rebuild_catalog();
}]=])
    string(FIND "${SD_MODEL_LOADER_SOURCE}" "${SDXL_VERSION_CACHE_OLD}" SDXL_VERSION_CACHE_OLD_POS)
    string(FIND "${SD_MODEL_LOADER_SOURCE}" "${SDXL_VERSION_CACHE_NEW}" SDXL_VERSION_CACHE_NEW_POS)
    if(SDXL_VERSION_CACHE_OLD_POS GREATER -1 AND SDXL_VERSION_CACHE_NEW_POS EQUAL -1)
        string(REPLACE
            "${SDXL_VERSION_CACHE_OLD}"
            "${SDXL_VERSION_CACHE_NEW}"
            SD_MODEL_LOADER_SOURCE
            "${SD_MODEL_LOADER_SOURCE}"
        )
        message(STATUS "Patched SDXL model-version cache before tensor-name conversion")
    elseif(SDXL_VERSION_CACHE_OLD_POS EQUAL -1 AND SDXL_VERSION_CACHE_NEW_POS GREATER -1)
        message(STATUS "SDXL model-version cache patch already applied")
    else()
        message(FATAL_ERROR
            "Unexpected stable-diffusion.cpp convert_tensors_name layout. Refusing an unsafe SDXL build."
        )
    endif()

    # C. Once cached, every later version query must use the cached pre-conversion value.
    set(SDXL_VERSION_QUERY_OLD [=[SDVersion ModelLoader::get_sd_version() const {
    TensorStorage token_embedding_weight, input_block_weight, context_ebedding_weight;]=])
    set(SDXL_VERSION_QUERY_NEW [=[SDVersion ModelLoader::get_sd_version() const {
    if (version_ != VERSION_COUNT) {
        return version_;
    }
    TensorStorage token_embedding_weight, input_block_weight, context_ebedding_weight;]=])
    string(FIND "${SD_MODEL_LOADER_SOURCE}" "${SDXL_VERSION_QUERY_OLD}" SDXL_VERSION_QUERY_OLD_POS)
    string(FIND "${SD_MODEL_LOADER_SOURCE}" "${SDXL_VERSION_QUERY_NEW}" SDXL_VERSION_QUERY_NEW_POS)
    if(SDXL_VERSION_QUERY_OLD_POS GREATER -1 AND SDXL_VERSION_QUERY_NEW_POS EQUAL -1)
        string(REPLACE
            "${SDXL_VERSION_QUERY_OLD}"
            "${SDXL_VERSION_QUERY_NEW}"
            SD_MODEL_LOADER_SOURCE
            "${SD_MODEL_LOADER_SOURCE}"
        )
        message(STATUS "Patched SDXL cached-version lookup")
    elseif(SDXL_VERSION_QUERY_OLD_POS EQUAL -1 AND SDXL_VERSION_QUERY_NEW_POS GREATER -1)
        message(STATUS "SDXL cached-version lookup patch already applied")
    else()
        message(FATAL_ERROR
            "Unexpected stable-diffusion.cpp get_sd_version layout. Refusing an unsafe SDXL build."
        )
    endif()

    file(WRITE "${SD_MODEL_LOADER_CPP}" "${SD_MODEL_LOADER_SOURCE}")
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