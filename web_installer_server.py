import http.server
import socketserver
import os
import sys

PORT = 3000
APK_PATH = os.path.abspath("FootIA-Live.apk")

HTML_PAGE = """<!DOCTYPE html>
<html lang="fr">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>Installer FootIA Live sur votre Téléphone</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }
        body { background: #0B0E14; color: #FFFFFF; display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 100vh; padding: 20px; }
        .card { background: #161B26; border: 1px solid #232B3E; border-radius: 24px; padding: 28px 24px; max-width: 440px; width: 100%; box-shadow: 0 20px 40px rgba(0,0,0,0.6); text-align: center; }
        .icon { width: 72px; height: 72px; background: linear-gradient(135deg, #10B981, #059669); border-radius: 20px; display: inline-flex; align-items: center; justify-content: center; font-size: 36px; margin-bottom: 16px; box-shadow: 0 10px 20px rgba(16,185,129,0.3); }
        h1 { font-size: 24px; font-weight: 800; margin-bottom: 6px; letter-spacing: -0.5px; }
        p.sub { font-size: 13px; color: #94A3B8; margin-bottom: 24px; line-height: 1.5; }
        .btn-install { display: flex; align-items: center; justify-content: center; gap: 10px; width: 100%; padding: 18px; background: #10B981; color: #000000; font-weight: 900; font-size: 16px; text-decoration: none; border-radius: 16px; box-shadow: 0 8px 25px rgba(16,185,129,0.4); transition: transform 0.15s, background 0.15s; margin-bottom: 24px; border: none; cursor: pointer; }
        .btn-install:active { transform: scale(0.97); background: #059669; }
        .badge { background: #232B3E; color: #10B981; font-size: 11px; font-weight: 700; padding: 4px 10px; border-radius: 20px; display: inline-block; margin-bottom: 20px; }
        .steps { text-align: left; background: #0F131D; border-radius: 16px; padding: 16px; }
        .step { display: flex; gap: 12px; margin-bottom: 14px; }
        .step:last-child { margin-bottom: 0; }
        .step-num { width: 26px; height: 26px; border-radius: 50%; background: #3B82F6; color: white; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: bold; flex-shrink: 0; }
        .step-txt { font-size: 12px; color: #CBD5E1; line-height: 1.4; }
        .step-txt strong { color: #FFFFFF; }
        .footer { margin-top: 20px; font-size: 11px; color: #64748B; }
    </style>
</head>
<body>
    <div class="card">
        <div class="icon">⚽</div>
        <div class="badge">APPLICATION OFFICIELLE • 25 MO</div>
        <h1>Installer FootIA Live</h1>
        <p class="sub">Vivez les matchs en direct avec le commentateur IA TV, les résumés vidéo réels et les classements officiels.</p>

        <a href="/FootIA-Live.apk" download="FootIA-Live.apk" class="btn-install" id="installBtn">
            <span>📲 TÉLÉCHARGER ET INSTALLER</span>
        </a>

        <div class="steps">
            <div class="step">
                <div class="step-num" style="background:#3B82F6;">1</div>
                <div class="step-txt"><strong>Téléchargement :</strong> Appuyez sur le bouton vert ci-dessus pour lancer le téléchargement de <strong>FootIA-Live.apk</strong>.</div>
            </div>
            <div class="step">
                <div class="step-num" style="background:#F59E0B;">2</div>
                <div class="step-txt"><strong>Ouvrir :</strong> Appuyez sur la notification "Téléchargement terminé" sur votre téléphone.</div>
            </div>
            <div class="step">
                <div class="step-num" style="background:#10B981;">3</div>
                <div class="step-txt"><strong>Autoriser :</strong> Si demandé, activez <em>"Autoriser cette source"</em> dans les paramètres.</div>
            </div>
            <div class="step">
                <div class="step-num" style="background:#8B5CF6;">4</div>
                <div class="step-txt"><strong>Installer :</strong> Appuyez sur <em>"Installer"</em> et lancez FootIA Live !</div>
            </div>
        </div>

        <p class="footer">Compatible avec tous les smartphones Android (Samsung, Xiaomi, Pixel, Oppo, etc.)</p>
    </div>
</body>
</html>
"""

class ApkHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.startswith("/FootIA-Live.apk") or self.path.startswith("/download") or self.path.startswith("/app.apk") or self.path.startswith("/footia.apk"):
            if os.path.exists(APK_PATH):
                self.send_response(200)
                self.send_header("Content-Type", "application/vnd.android.package-archive")
                self.send_header("Content-Disposition", 'attachment; filename="FootIA-Live.apk"')
                file_size = os.path.getsize(APK_PATH)
                self.send_header("Content-Length", str(file_size))
                self.end_headers()
                with open(APK_PATH, "rb") as f:
                    while chunk := f.read(65536):
                        self.wfile.write(chunk)
            else:
                self.send_error(404, "Fichier APK en cours de generation")
        else:
            content = HTML_PAGE.encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(content)))
            self.end_headers()
            self.wfile.write(content)

    def do_HEAD(self):
        if self.path.startswith("/FootIA-Live.apk") or self.path.startswith("/download") or self.path.startswith("/app.apk") or self.path.startswith("/footia.apk"):
            if os.path.exists(APK_PATH):
                self.send_response(200)
                self.send_header("Content-Type", "application/vnd.android.package-archive")
                self.send_header("Content-Disposition", 'attachment; filename="FootIA-Live.apk"')
                file_size = os.path.getsize(APK_PATH)
                self.send_header("Content-Length", str(file_size))
                self.end_headers()
            else:
                self.send_error(404, "Fichier APK en cours de generation")
        else:
            content = HTML_PAGE.encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(content)))
            self.end_headers()

    def log_message(self, format, *args):
        pass

if __name__ == "__main__":
    socketserver.TCPServer.allow_reuse_address = True
    with socketserver.TCPServer(("", PORT), ApkHandler) as httpd:
        print(f"Serveur d'installation active sur le port {PORT}")
        sys.stdout.flush()
        httpd.serve_forever()
