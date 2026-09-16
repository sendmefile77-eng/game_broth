#!/usr/bin/env python3
"""Harden the pinned Local Dream HTTP core for in-app use.

The embedded core remains bound to loopback, but Android applications share the host
network namespace. A second app could otherwise probe 127.0.0.1 and invoke /generate.
This build-time patch requires a per-process secret supplied only through the child
process environment and removes browser-oriented CORS exposure.
"""

from __future__ import annotations

import sys
from pathlib import Path


class PatchError(RuntimeError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise PatchError(message)


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    require(count == 1, f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: patch_localdream_loopback_auth.py <main.cpp>", file=sys.stderr)
        return 2

    path = Path(sys.argv[1])
    require(path.is_file(), f"Local Dream main.cpp not found: {path}")
    text = path.read_text(encoding="utf-8")

    if "GAMEBROTH_LOCAL_TOKEN" in text:
        print("Local Dream loopback auth already applied")
        return 0

    text = replace_once(
        text,
        "#include <chrono>\n",
        "#include <chrono>\n#include <cstdlib>\n",
        "cstdlib include",
    )

    anchor = "static std::mutex g_generation_mutex;\n"
    auth = r'''
static std::string g_gamebroth_auth_token;

static bool gamebrothAuthorize(const httplib::Request &req,
                               httplib::Response &res) {
  const std::string presented = req.get_header_value("X-GameBroth-Token");
  if (g_gamebroth_auth_token.empty() ||
      presented.size() != g_gamebroth_auth_token.size()) {
    res.status = 403;
    res.set_content("Forbidden", "text/plain");
    return false;
  }
  unsigned char diff = 0;
  for (size_t i = 0; i < presented.size(); ++i)
    diff |= static_cast<unsigned char>(presented[i] ^ g_gamebroth_auth_token[i]);
  if (diff != 0) {
    res.status = 403;
    res.set_content("Forbidden", "text/plain");
    return false;
  }
  return true;
}
'''
    text = replace_once(
        text,
        anchor,
        anchor + auth,
        "generation mutex/auth insertion",
    )

    text = replace_once(
        text,
        '  svr.Post("/generate", [pipeline](const httplib::Request &request,\n'
        '                                   httplib::Response &res) {\n'
        '    try {\n',
        '  svr.Post("/generate", [pipeline](const httplib::Request &request,\n'
        '                                   httplib::Response &res) {\n'
        '    if (!gamebrothAuthorize(request, res)) return;\n'
        '    try {\n',
        "generate auth",
    )

    text = replace_once(
        text,
        '  svr.Post("/upscale", [](const httplib::Request &req, httplib::Response &res) {\n'
        '    std::unique_ptr<QnnModel> tempUpscalerApp = nullptr;\n',
        '  svr.Post("/upscale", [](const httplib::Request &req, httplib::Response &res) {\n'
        '    if (!gamebrothAuthorize(req, res)) return;\n'
        '    std::unique_ptr<QnnModel> tempUpscalerApp = nullptr;\n',
        "upscale auth",
    )

    text = replace_once(
        text,
        '  svr.Post("/tokenize", [text_encoder](const httplib::Request &req,\n'
        '                                       httplib::Response &res) {\n'
        '    try {\n',
        '  svr.Post("/tokenize", [text_encoder](const httplib::Request &req,\n'
        '                                       httplib::Response &res) {\n'
        '    if (!gamebrothAuthorize(req, res)) return;\n'
        '    try {\n',
        "tokenize auth",
    )

    options_block = r'''  svr.set_default_headers({
      {"Access-Control-Allow-Origin", "*"},
      {"Access-Control-Allow-Methods", "GET, POST, OPTIONS"},
      {"Access-Control-Allow-Headers", "Content-Type, Authorization"},
      {"Access-Control-Max-Age", "86400"},
  });
  svr.Options(R"(.*)", [](const httplib::Request &, httplib::Response &res) {
    res.status = 204;
  });
  svr.Get("/health", [](const httplib::Request &, httplib::Response &res) {
    res.status = 200;
  });
'''
    hardened_server = r'''  // GAMEBROTH: this server is an internal IPC endpoint, not a web API.
  // No CORS headers are emitted and every callable route requires the in-memory token.
  svr.Get("/health", [](const httplib::Request &req, httplib::Response &res) {
    if (!gamebrothAuthorize(req, res)) return;
    res.status = 200;
  });
'''
    text = replace_once(text, options_block, hardened_server, "HTTP server hardening")

    main_anchor = "  ServerOptions opts = processCommandLine(argc, argv);\n"
    token_init = r'''  ServerOptions opts = processCommandLine(argc, argv);

  const char *gamebroth_token = std::getenv("GAMEBROTH_LOCAL_TOKEN");
  if (gamebroth_token == nullptr || std::strlen(gamebroth_token) < 32) {
    std::cerr << "ERROR: missing embedded GameBroth authentication token\n";
    return EXIT_FAILURE;
  }
  g_gamebroth_auth_token = gamebroth_token;
'''
    text = replace_once(text, main_anchor, token_init, "environment token initialization")

    path.write_text(text, encoding="utf-8")
    print("Embedded Local Dream loopback authentication applied")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except PatchError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
