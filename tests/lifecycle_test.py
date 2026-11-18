"""Radio-free lifecycle checks; isolated dynamically allocated loopback ports."""
import importlib.util
import json
from pathlib import Path
import socket
import subprocess
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.error import HTTPError
from urllib.request import Request, ProxyHandler, build_opener

APP = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parents[1])
SCRIPT = APP / "controller.py"
spec = importlib.util.spec_from_file_location("controller", SCRIPT)
controller = importlib.util.module_from_spec(spec)
spec.loader.exec_module(controller)


def fresh_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def request(port, path, method="GET", origin=True, token=None, host=None, extra=None):
    headers = {"Host": host or f"localhost:{port}"}
    if origin:
        headers["Origin"] = f"http://localhost:{port}" if origin is True else origin
    if token is not None:
        headers["X-Afterglow-Token"] = token
    headers.update(extra or {})
    req = Request(f"http://127.0.0.1:{port}{path}", headers=headers, method=method)
    try:
        with build_opener(ProxyHandler({})).open(req, timeout=2) as response:
            return response.status, response.read()
    except HTTPError as error:
        return error.code, error.read()


def wait_status(port):
    until = time.monotonic() + 6
    while time.monotonic() < until:
        info = controller.identify(port)
        if info:
            return info
        time.sleep(.05)
    raise AssertionError("Server did not become ready")


port = fresh_port()
try:
    launchers = [subprocess.Popen([sys.executable, str(SCRIPT), "--start", "--no-browser", "--port", str(port)],
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True) for _ in range(6)]
    outputs = [proc.communicate(timeout=10) for proc in launchers]
    assert all(proc.returncode == 0 for proc in launchers), outputs
    info = wait_status(port)
    assert all(f"PID {info['pid']}" in out for out, _ in outputs), outputs
    again = subprocess.run([sys.executable, str(SCRIPT), "--start", "--no-browser", "--port", str(port)], capture_output=True, text=True, timeout=8)
    assert again.returncode == 0 and f"PID {info['pid']}" in again.stdout
    assert controller.identify(port)["pid"] == info["pid"]
    print("PASS group 1a: six racing launches plus repeat reuse one verified server PID")

    token = info["token"]
    checks = [
        request(port, "/api/status", origin="https://foreign.example"),
        request(port, "/api/status", host=f"foreign.example:{port}", origin=False),
        request(port, "/api/status", origin=False, extra={"Sec-Fetch-Site": "cross-site"}),
        request(port, "/api/shutdown", "POST", token=None),
        request(port, "/api/shutdown", "POST", token="invalid"),
        request(port, "/api/shutdown", "POST", token=token, origin=False),
        request(port, "/api/shutdown", "POST", token=token, origin="https://foreign.example"),
    ]
    assert all(status == 403 for status, _ in checks), checks
    assert request(port, "/api/shutdown")[0] == 404
    for path in ["/controller.py", "/tests/lifecycle_test.py", "/../controller.py", "/%2e%2e/controller.py"]:
        assert request(port, path)[0] == 404
    assert request(port, "/")[0] == 200
    assert controller.identify(port)["pid"] == info["pid"]
    stopped = subprocess.run([sys.executable, str(SCRIPT), "--stop", "--port", str(port)], capture_output=True, text=True, timeout=8)
    assert stopped.returncode == 0 and not controller.port_occupied(port), stopped.stdout
    print("PASS group 2a: origin/host/token restrictions, asset whitelist and verified Stop fallback")
finally:
    info = controller.identify(port)
    if info:
        request(port, "/api/shutdown", "POST", token=info["token"])

port = fresh_port()
child = subprocess.Popen([sys.executable, str(SCRIPT), "--serve", "--port", str(port)])
try:
    info = wait_status(port)
    status, body = request(port, "/api/shutdown", "POST", token=info["token"])
    assert status == 200 and json.loads(body)["stopping"]
    assert child.wait(timeout=5) == 0
    assert not controller.port_occupied(port)
    print("PASS group 2b: authenticated HTTP shutdown exits owned child cleanly")
finally:
    if child.poll() is None:
        info = controller.identify(port)
        if info:
            request(port, "/api/shutdown", "POST", token=info["token"])
        child.wait(timeout=5)


class OtherHandler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass
    def do_GET(self):
        body = b'{"app":"unrelated"}'
        self.send_response(200)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


with ThreadingHTTPServer(("127.0.0.1", 0), OtherHandler) as other:
    port = other.server_address[1]
    threading.Thread(target=other.serve_forever, daemon=True).start()
    result = subprocess.run([sys.executable, str(SCRIPT), "--start", "--no-browser", "--port", str(port)], capture_output=True, text=True, timeout=8)
    assert result.returncode == 1 and "No existing process was stopped" in result.stdout
    result = subprocess.run([sys.executable, str(SCRIPT), "--stop", "--port", str(port)], capture_output=True, text=True, timeout=4)
    assert result.returncode == 1 and controller.port_occupied(port)
    other.shutdown()
print("PASS group 1b/2c: unrelated port occupant refused and left running")
