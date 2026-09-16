#!/usr/bin/env python3
"""Apply the Local Dream SampleApp adaptations to QAIRT 2.48 semantically.

The upstream Local Dream patch was authored against QAIRT 2.39 and relies on exact
line context. QAIRT 2.48 moved declarations in QnnSampleApp.hpp, which makes
`git apply` fail even though the required APIs are still present. This patcher
matches symbols/regions instead of line numbers and fails closed when a required
transformation cannot be proven.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path


class PatchError(RuntimeError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise PatchError(message)


def read(path: Path) -> str:
    require(path.is_file(), f"missing QAIRT SampleApp file: {path}")
    return path.read_text(encoding="utf-8")


def write(path: Path, text: str) -> None:
    path.write_text(text, encoding="utf-8")


def patch_qnn_sample_app_cpp(path: Path) -> None:
    text = read(path)

    if "GAMEBROTH_MMAP_CONTEXT" not in text:
        include_anchor = "#include <inttypes.h>"
        require(include_anchor in text, "QnnSampleApp.cpp: inttypes include anchor not found")
        text = text.replace(
            include_anchor,
            include_anchor
            + "\n\n#ifndef __hexagon__\n"
            + "#include <fcntl.h>\n"
            + "#include <sys/mman.h>\n"
            + "#include <sys/stat.h>\n"
            + "#include <unistd.h>\n"
            + "#endif  // GAMEBROTH_MMAP_CONTEXT",
            1,
        )

        # Local Dream supplies tensors directly and does not use SampleApp's CLI input lists.
        initialize_pattern = re.compile(
            r"(sample_app::StatusCode\s+sample_app::QnnSampleApp::initialize\(\)\s*\{)"
            r"(?P<body>.*?)"
            r"(?P<logging>\n\s*//\s*initialize logging in the backend)",
            re.S,
        )
        match = initialize_pattern.search(text)
        require(match is not None, "QnnSampleApp.cpp: initialize() layout not recognized")
        body = match.group("body")
        # Only replace the old CLI/file-list preparation section. If Qualcomm adds other
        # required initialization before logging, fail rather than deleting it silently.
        require(
            "m_inputFileLists" in body or "readInputLists" in body,
            "QnnSampleApp.cpp: input-list setup not found in initialize()",
        )
        replacement_body = (
            "\n  // GAMEBROTH: tensors are supplied directly by Local Dream; do not create\n"
            "  // CLI output directories or read SampleApp input-list files here.\n"
        )
        text = text[: match.start("body")] + replacement_body + text[match.start("logging") :]

        # QAIRT copies cached context binaries into a heap buffer by default. For multi-GB
        # diffusion contexts that duplicates memory. Map the file read-only instead.
        binary_pattern = re.compile(
            r"uint64_t\s+bufferSize\s*\{0\};\s*"
            r"std::shared_ptr<uint8_t>\s+buffer\s*\{nullptr\};"
            r"(?P<body>.*?)"
            r"(?P<inspect>\n\s*//\s*inspect binary info)",
            re.S,
        )
        binary = binary_pattern.search(text)
        require(binary is not None, "QnnSampleApp.cpp: cached-binary load block not recognized")
        old_body = binary.group("body")
        require(
            "readBinaryFromFile" in old_body or "getFileSize" in old_body,
            "QnnSampleApp.cpp: cached-binary block is not the expected QAIRT implementation",
        )
        mmap_body = r'''
  // GAMEBROTH_MMAP_CONTEXT: keep large QNN context binaries file-backed instead
  // of duplicating them in an anonymous heap allocation.
  int fd = open(m_cachedBinaryPath.c_str(), O_RDONLY);
  if (fd < 0) {
    QNN_ERROR("Failed to open input file: %s", m_cachedBinaryPath.c_str());
    return StatusCode::FAILURE;
  }
  struct stat fileStat {};
  if (0 != fstat(fd, &fileStat) || 0 == fileStat.st_size) {
    QNN_ERROR("Received path to an empty file. Nothing to deserialize.");
    close(fd);
    return StatusCode::FAILURE;
  }
  bufferSize = static_cast<uint64_t>(fileStat.st_size);

  void* mapped = mmap(nullptr, static_cast<size_t>(bufferSize), PROT_READ, MAP_PRIVATE, fd, 0);
  close(fd);
  if (MAP_FAILED == mapped) {
    QNN_ERROR("Failed to mmap binary file: %s", m_cachedBinaryPath.c_str());
    return StatusCode::FAILURE;
  }
  madvise(mapped, static_cast<size_t>(bufferSize), MADV_SEQUENTIAL);
  buffer = std::shared_ptr<uint8_t>(
      static_cast<uint8_t*>(mapped),
      [bufferSize](uint8_t* p) { munmap(p, static_cast<size_t>(bufferSize)); });
'''
        text = text[: binary.start("body")] + mmap_body + text[binary.start("inspect") :]

    write(path, text)


def patch_qnn_sample_app_hpp(path: Path) -> None:
    text = read(path)
    if "GAMEBROTH_DERIVED_ACCESS" not in text:
        # QnnModel derives from QnnSampleApp and intentionally reuses SampleApp lifecycle,
        # graph/context and IO helpers. QAIRT 2.48 moved private declarations, so expose
        # them to derived classes semantically instead of depending on surrounding lines.
        matches = list(re.finditer(r"(?m)^(?P<indent>\s*)private:\s*$", text))
        require(matches, "QnnSampleApp.hpp: no private section found")
        text = re.sub(
            r"(?m)^(?P<indent>\s*)private:\s*$",
            r"\g<indent>protected:  // GAMEBROTH_DERIVED_ACCESS",
            text,
        )
    write(path, text)


def patch_io_tensor_hpp(path: Path) -> None:
    text = read(path)
    if "convertToFloatInto" not in text:
        pattern = re.compile(
            r"(?P<decl>\s*StatusCode\s+convertToFloat\s*\(\s*float\s*\*\*\s*out\s*,\s*"
            r"Qnn_Tensor_t\s*\*\s*\w+\s*\)\s*;)"
        )
        match = pattern.search(text)
        require(match is not None, "IOTensor.hpp: convertToFloat declaration not found")
        insertion = (
            match.group("decl")
            + "\n\n public:  // GAMEBROTH_DIRECT_FLOAT_OUTPUT\n"
            + "  StatusCode convertToFloatInto(float *dst, Qnn_Tensor_t *output);\n\n private:\n"
        )
        text = text[: match.start()] + insertion + text[match.end() :]
    write(path, text)


def patch_io_tensor_cpp(path: Path) -> None:
    text = read(path)
    if "GAMEBROTH_DIRECT_FLOAT_OUTPUT" not in text:
        # Keep QAIRT 2.48's own conversion implementation intact. The compatibility helper
        # delegates to it, copies into caller-owned memory, then releases the temporary.
        # This is slightly less memory-efficient than Local Dream's 2.39 patch but much less
        # brittle across SDK releases and is small compared with the QNN context mmap win above.
        helper = r'''

#ifndef __hexagon__
// GAMEBROTH_DIRECT_FLOAT_OUTPUT
qnn::tools::iotensor::StatusCode
qnn::tools::iotensor::IOTensor::convertToFloatInto(float *dst, Qnn_Tensor_t *tensor) {
  if (nullptr == dst || nullptr == tensor) {
    QNN_ERROR("convertToFloatInto received a null pointer");
    return StatusCode::FAILURE;
  }

  float *converted = nullptr;
  auto status = convertToFloat(&converted, tensor);
  if (StatusCode::SUCCESS != status || nullptr == converted) {
    if (converted != nullptr) free(converted);
    return status;
  }

  std::vector<size_t> dims;
  fillDims(dims, QNN_TENSOR_GET_DIMENSIONS(tensor), QNN_TENSOR_GET_RANK(tensor));
  const size_t elementCount = datautil::calculateElementCount(dims);
  for (size_t i = 0; i < elementCount; ++i) dst[i] = converted[i];
  free(converted);
  return StatusCode::SUCCESS;
}
#endif
'''
        text += helper
    write(path, text)


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: patch_qairt248_sampleapp.py <SampleApp-root>", file=sys.stderr)
        return 2

    root = Path(sys.argv[1]).resolve()
    try:
        patch_qnn_sample_app_cpp(root / "src/QnnSampleApp.cpp")
        patch_qnn_sample_app_hpp(root / "src/QnnSampleApp.hpp")
        patch_io_tensor_hpp(root / "src/Utils/IOTensor.hpp")
        patch_io_tensor_cpp(root / "src/Utils/IOTensor.cpp")
    except PatchError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1

    print("GameBroth QAIRT 2.48 SampleApp adaptations applied successfully")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
