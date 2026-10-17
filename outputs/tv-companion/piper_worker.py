"""Loopback-only Piper worker for the household M1-2 Mac."""
import argparse
import io
import json
import socket
import os
import threading
import time
import urllib.request
import wave
from collections import OrderedDict
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

SERVICE = "duobook-piper-v1"
VOICES = ("en_US-hfc_female-medium", "en_US-lessac-high", "en_US-lessac-medium")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", required=True)
    parser.add_argument("--port", type=int, default=18770)
    args = parser.parse_args()
    voice_name = Path(args.model).stem
    model_dir = Path(args.model).parent
    lock = threading.Lock()
    voice = None
    voices = OrderedDict()
    def heartbeat():
        while True:
            try:
                print('{"heartbeat":true}', flush=True)
            except (BrokenPipeError, OSError):
                os._exit(0)  # Release the worker and caffeinate after SSH closes.
            time.sleep(10)
    threading.Thread(target=heartbeat, daemon=True).start()

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass  # Speech text is never logged.

        def reply(self, status, data, kind="application/json", headers=None):
            payload = data if isinstance(data, bytes) else json.dumps(data).encode()
            self.send_response(status)
            self.send_header("Content-Type", kind)
            self.send_header("Content-Length", str(len(payload)))
            self.send_header("Cache-Control", "no-store")
            for name, value in (headers or {}).items():
                self.send_header(name, str(value))
            self.end_headers()
            self.wfile.write(payload)

        def do_GET(self):
            if self.path != "/health":
                return self.reply(404, {"error": "Not found"})
            return self.reply(200, {"service": SERVICE, "voice": voice_name, "ready": voice is not None, "host": socket.gethostname(), "voiceSelection": True, "voices": list(VOICES)})

        def do_POST(self):
            if self.path != "/synthesize":
                return self.reply(404, {"error": "Not found"})
            try:
                length = int(self.headers.get("Content-Length", "0"))
                if not 0 < length <= 40000:
                    raise ValueError("Invalid request length")
                request = json.loads(self.rfile.read(length))
                if not isinstance(request, dict):
                    raise ValueError("Use a JSON object")
                text = request.get("text", "")
                selected = request.get("voice", voice_name)
                if selected not in VOICES:
                    raise ValueError("Choose one of the available voices")
                if not isinstance(text, str) or not text.strip() or len(text) > 8000:
                    raise ValueError("Use 1–8000 characters of speech text")
                started = time.monotonic()
                output = io.BytesIO()
                with lock:
                    if selected not in voices:
                        voices[selected] = PiperVoice.load(str(model_dir / (selected + ".onnx")))
                    voices.move_to_end(selected)
                    while len(voices) > 2:
                        voices.popitem(last=False)
                    with wave.open(output, "wb") as wav:
                        voices[selected].synthesize_wav(text, wav)
                audio = output.getvalue()
                with wave.open(io.BytesIO(audio), "rb") as wav:
                    duration = wav.getnframes() / wav.getframerate()
                self.reply(200, audio, "audio/wav", {"X-Synthesis-Ms": round((time.monotonic()-started)*1000), "X-Audio-Duration": round(duration, 3), "X-Piper-Voice": selected})
            except (ValueError, json.JSONDecodeError) as error:
                self.reply(400, {"error": str(error)})
            except Exception:
                self.reply(500, {"error": "Piper could not synthesize this request"})

    # Reuse an already-running copy after a controller restart. If its SSH
    # connection disappears, this copy takes over once its socket is released.
    while True:
        try:
            server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
            break
        except OSError:
            try:
                with urllib.request.urlopen(f"http://127.0.0.1:{args.port}/health", timeout=3) as response:
                    active = json.load(response)
                if active.get("service") != SERVICE or active.get("voice") != voice_name:
                    raise RuntimeError("Port belongs to a different service or voice")
                time.sleep(3)
            except (urllib.error.URLError, TimeoutError, ConnectionError):
                time.sleep(0.5)
    from piper import PiperVoice
    voice = PiperVoice.load(args.model)
    voices[voice_name] = voice
    print(json.dumps({"service": SERVICE, "ready": True, "voice": voice_name}), flush=True)
    server.serve_forever(poll_interval=0.5)


if __name__ == "__main__":
    main()
