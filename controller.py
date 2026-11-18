"""Afterglow loopback lifecycle server. Python standard library only."""
from __future__ import annotations

import argparse
import hmac
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request, ProxyHandler, build_opener

APP_ID = "afterglow-local-controller-v1"
APP_DIR = Path(__file__).resolve().parent
DEFAULT_PORT = 8766


class ControllerServer(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = False

    def server_bind(self):
        if hasattr(socket, "SO_EXCLUSIVEADDRUSE"):
            self.socket.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
        super().server_bind()

    def __init__(self, port):
        self.capability = secrets.token_urlsafe(32)
        self.stopping = False
        super().__init__(("127.0.0.1", port), ControllerHandler)


class ControllerHandler(BaseHTTPRequestHandler):
    server_version = "Afterglow"

    def log_message(self, *_):
        pass

    def _allowed(self, require_origin=False):
        port = self.server.server_address[1]
        hosts = {f"localhost:{port}", f"127.0.0.1:{port}"}
        host = self.headers.get("Host", "").lower()
        origin = self.headers.get("Origin")
        if self.client_address[0] != "127.0.0.1" or host not in hosts:
            return False
        if self.headers.get("Sec-Fetch-Site", "").lower() == "cross-site":
            return False
        if origin is not None and origin != "http://" + host:
            return False
        return not require_origin or origin is not None

    def _reply(self, status, body, content_type="application/json; charset=utf-8"):
        if isinstance(body, dict):
            body = json.dumps(body).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("X-Frame-Options", "DENY")
        self.send_header("Content-Security-Policy", "frame-ancestors 'none'")
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(body)
        self.wfile.flush()

    def do_GET(self):
        if not self._allowed():
            self._reply(403, {"error": "Loopback same-origin access only"})
            return
        path = urlsplit(self.path).path
        if path == "/api/status":
            self._reply(200, {"app": APP_ID, "api": 1, "pid": os.getpid(),
                              "app_dir": str(APP_DIR), "token": self.server.capability,
                              "stopping": self.server.stopping})
            return
        assets = {"/": ("index.html", "text/html; charset=utf-8"),
                  "/index.html": ("index.html", "text/html; charset=utf-8"),
                  "/README.txt": ("README.txt", "text/plain; charset=utf-8")}
        if path not in assets:
            self._reply(404, {"error": "Not found"})
            return
        name, content_type = assets[path]
        try:
            body = (APP_DIR / name).read_bytes()
        except OSError:
            self._reply(404, {"error": "App asset unavailable"})
            return
        self._reply(200, body, content_type)

    def do_POST(self):
        if not self._allowed(require_origin=True):
            self._reply(403, {"error": "Same-origin POST required"})
            return
        if urlsplit(self.path).path != "/api/shutdown":
            self._reply(404, {"error": "Not found"})
            return
        token = self.headers.get("X-Afterglow-Token", "")
        if len(token) > 128 or not token.isascii() or not hmac.compare_digest(token, self.server.capability):
            self._reply(403, {"error": "Invalid shutdown capability"})
            return
        self.server.stopping = True
        self._reply(200, {"app": APP_ID, "stopping": True})
        threading.Thread(target=self.server.shutdown, daemon=True).start()

    def do_OPTIONS(self):
        self._reply(403, {"error": "Cross-origin access is disabled"})


def _url(port):
    return f"http://localhost:{port}"


def _request(port, path, method="GET", token=None):
    headers = {"Origin": _url(port)}
    if token:
        headers["X-Afterglow-Token"] = token
    request = Request(f"http://127.0.0.1:{port}{path}", method=method,
                      headers={**headers, "Host": f"localhost:{port}"})
    with build_opener(ProxyHandler({})).open(request, timeout=.7) as response:
        return json.loads(response.read(8192))


def identify(port):
    try:
        info = _request(port, "/api/status")
        if info.get("app") == APP_ID and info.get("api") == 1 and isinstance(info.get("token"), str):
            return info
    except (OSError, ValueError, HTTPError, URLError):
        pass
    return None


def port_occupied(port):
    try:
        with socket.create_connection(("127.0.0.1", port), timeout=.3):
            return True
    except OSError:
        return False


def same_copy(info):
    return os.path.normcase(str(Path(info.get("app_dir", "")).resolve())) == os.path.normcase(str(APP_DIR))


def open_chrome(port):
    candidates = [Path(os.environ.get(key, "")) / suffix for key, suffix in [
        ("ProgramFiles", "Google/Chrome/Application/chrome.exe"),
        ("ProgramFiles(x86)", "Google/Chrome/Application/chrome.exe"),
        ("LOCALAPPDATA", "Google/Chrome/Application/chrome.exe")]]
    for chrome in candidates:
        if chrome.is_file():
            subprocess.Popen([str(chrome), _url(port)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            return
    print("Chrome was not found in standard locations. Open Chrome at " + _url(port))


def start(port, no_browser=False):
    info = identify(port)
    if not info and not port_occupied(port):
        executable = Path(sys.executable)
        hidden_python = executable.with_name("pythonw.exe")
        if hidden_python.is_file():
            executable = hidden_python
        subprocess.Popen([str(executable), str(Path(__file__).resolve()), "--serve", "--port", str(port)],
                         cwd=str(APP_DIR), stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                         stderr=subprocess.DEVNULL,
                         creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
    # Atomic socket bind elects one server even when multiple launchers race.
    until = time.monotonic() + 5
    while not info and time.monotonic() < until:
        time.sleep(.1)
        info = identify(port)
    if not info:
        print(f"Cannot start: port {port} is occupied or the local server could not start. No existing process was stopped.")
        return 1
    if not same_copy(info):
        print(f"Another Afterglow copy is running from {info.get('app_dir')} (PID {info.get('pid')}). Quit that copy first. Nothing was stopped.")
        return 1
    if info.get("stopping"):
        print("Afterglow is still stopping. Try Start controller again in a moment.")
        return 1
    print(f"Afterglow ready: {_url(port)} (PID {info['pid']}). Existing instances are reused.")
    if not no_browser:
        open_chrome(port)
    return 0


def stop(port):
    info = identify(port)
    if not info or not same_copy(info):
        print("This Afterglow server was not found. No process was stopped.")
        return 1 if port_occupied(port) else 0
    _request(port, "/api/shutdown", "POST", info["token"])
    until = time.monotonic() + 5
    while time.monotonic() < until:
        if not port_occupied(port):
            print("Afterglow stopped. Chrome stays open; its tab can be closed.")
            return 0
        time.sleep(.1)
    print("Shutdown requested; the server has not exited yet.")
    return 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    action = parser.add_mutually_exclusive_group()
    action.add_argument("--serve", action="store_true")
    action.add_argument("--start", action="store_true")
    action.add_argument("--stop", action="store_true")
    parser.add_argument("--no-browser", action="store_true")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    args = parser.parse_args()
    if not 1024 <= args.port <= 65535:
        parser.error("port must be between 1024 and 65535")
    if args.serve:
        try:
            with ControllerServer(args.port) as server:
                server.serve_forever(poll_interval=.1)
            return 0
        except OSError:
            return 1  # A racing launcher may have won the atomic bind.
    if args.stop:
        return stop(args.port)
    return start(args.port, args.no_browser)


if __name__ == "__main__":
    raise SystemExit(main())
