#!/usr/bin/env python3
"""Add fail-closed SDXL QNN diagnostics to the pinned Local Dream source.

The stock Local Dream error only says that graphExecute failed.  For model/runtime
compatibility work we need the actual QNN status plus the graph IO contract seen
on the device.  This patch is deliberately diagnostic only: it does not change
tensor contents, sizes, model selection, or execution semantics.
"""

from __future__ import annotations

import sys
from pathlib import Path


OLD = '''    if (QNN_GRAPH_NO_ERROR != executeStatus) {
      returnStatus = StatusCode::FAILURE;
      QNN_ERROR("sdxl unet graph execution failed!");
      return returnStatus;
    }
'''

NEW = '''    if (QNN_GRAPH_NO_ERROR != executeStatus) {
      returnStatus = StatusCode::FAILURE;
      QNN_ERROR("sdxl unet graph execution failed: status=%d",
                static_cast<int>(executeStatus));
      for (uint32_t i = 0; i < graphInfo.numInputTensors; ++i) {
        const auto &tensor = inputs[i];
        const uint32_t rank = QNN_TENSOR_GET_RANK(tensor);
        const uint32_t *dims = QNN_TENSOR_GET_DIMENSIONS(tensor);
        std::string dimText;
        for (uint32_t d = 0; d < rank; ++d) {
          if (d) dimText += "x";
          dimText += std::to_string(dims ? dims[d] : 0);
        }
        const char *name = QNN_TENSOR_GET_NAME(tensor);
        QNN_ERROR("sdxl unet input[%u] name=%s dims=%s dtype=%d", i,
                  name ? name : "<null>", dimText.c_str(),
                  static_cast<int>(QNN_TENSOR_GET_DATA_TYPE(tensor)));
      }
      for (uint32_t i = 0; i < graphInfo.numOutputTensors; ++i) {
        const auto &tensor = outputs[i];
        const uint32_t rank = QNN_TENSOR_GET_RANK(tensor);
        const uint32_t *dims = QNN_TENSOR_GET_DIMENSIONS(tensor);
        std::string dimText;
        for (uint32_t d = 0; d < rank; ++d) {
          if (d) dimText += "x";
          dimText += std::to_string(dims ? dims[d] : 0);
        }
        const char *name = QNN_TENSOR_GET_NAME(tensor);
        QNN_ERROR("sdxl unet output[%u] name=%s dims=%s dtype=%d", i,
                  name ? name : "<null>", dimText.c_str(),
                  static_cast<int>(QNN_TENSOR_GET_DATA_TYPE(tensor)));
      }
      return returnStatus;
    }
'''


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: patch_localdream_qnn_diagnostics.py <QnnModel.hpp>", file=sys.stderr)
        return 2

    path = Path(sys.argv[1])
    if not path.is_file():
        print(f"ERROR: missing file: {path}", file=sys.stderr)
        return 1

    text = path.read_text(encoding="utf-8")
    marker = 'sdxl unet graph execution failed: status=%d'
    if marker in text:
        if OLD in text:
            print("ERROR: both old and diagnostic SDXL failure blocks are present", file=sys.stderr)
            return 1
        print("Local Dream SDXL QNN diagnostics already present")
        return 0

    count = text.count(OLD)
    if count != 1:
        print(f"ERROR: expected exactly one SDXL UNet failure block, found {count}", file=sys.stderr)
        return 1

    path.write_text(text.replace(OLD, NEW, 1), encoding="utf-8")
    print("Local Dream SDXL QNN diagnostics applied")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
