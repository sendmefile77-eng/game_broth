#!/usr/bin/env python3
"""Make the pinned tokenizers-cpp FFI explicit about raw-pointer borrows.

Rust 1.89 promoted ``dangerous_implicit_autorefs`` to deny-by-default. The
pinned tokenizers-cpp source calls ``String::len`` through a raw-pointer field,
which previously relied on an implicit reference. Keep the vendored submodule
pinned and apply the compiler-recommended explicit borrow during the build.
"""

from __future__ import annotations

import sys
from pathlib import Path


class PatchError(RuntimeError):
    pass


REPLACEMENTS = (
    (
        "*out_len = (*handle).decode_str.len();",
        "*out_len = (&(*handle).decode_str).len();",
    ),
    (
        "*out_len = (*handle).id_to_token_result.len();",
        "*out_len = (&(*handle).id_to_token_result).len();",
    ),
)


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: patch_tokenizers_rust_autoref.py <rust/src/lib.rs>", file=sys.stderr)
        return 2

    path = Path(sys.argv[1])
    if not path.is_file():
        raise PatchError(f"tokenizers-cpp Rust source not found: {path}")

    text = path.read_text(encoding="utf-8")
    changed = False
    for old, new in REPLACEMENTS:
        old_count = text.count(old)
        new_count = text.count(new)
        if old_count == 1 and new_count == 0:
            text = text.replace(old, new, 1)
            changed = True
        elif old_count == 0 and new_count == 1:
            continue
        else:
            raise PatchError(
                "unexpected tokenizers-cpp source layout for "
                f"{old!r}: old={old_count}, patched={new_count}"
            )

    path.write_text(text, encoding="utf-8")
    print(
        "Pinned tokenizers-cpp explicit raw-pointer borrows "
        + ("applied" if changed else "already present")
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except PatchError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
