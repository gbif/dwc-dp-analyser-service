import http.server
import json


class Handler(http.server.BaseHTTPRequestHandler):
  def do_PUT(self):
    length = int(self.headers.get('Content-Length', 0))
    body = self.rfile.read(length).decode()
    print(f'[registry-mock] PUT {self.path}', flush=True)
    try:
      parsed = json.loads(body)
      print(json.dumps(parsed, indent=2), flush=True)
    except Exception:
      print(body, flush=True)
    self.send_response(200)
    self.end_headers()

  def log_message(self, fmt, *args):
    pass  # suppress default access log, do_PUT handles it


if __name__ == '__main__':
  print('[registry-mock] listening on :8080', flush=True)
  http.server.HTTPServer(('', 8080), Handler).serve_forever()
