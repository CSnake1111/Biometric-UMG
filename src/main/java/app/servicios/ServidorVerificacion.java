package app.servicios;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ServidorVerificacion {

    private static final int    PUERTO      = 8080;
    private static final String CARPETA_WEB = "data/verificacion/";
    private static HttpServer   servidor    = null;
    private static boolean      corriendo   = false;
    private static Process      ngrokProcess = null;
    private static String       urlNgrok    = null;

    // ══════════════════════════════════════════
    //  Iniciar servidor + Ngrok automáticamente
    // ══════════════════════════════════════════
    public static void iniciar() {
        if (corriendo) return;

        new Thread(() -> {
            try {
                // 1. Levantar servidor HTTP local
                new File(CARPETA_WEB).mkdirs();
                servidor = HttpServer.create(new InetSocketAddress(PUERTO), 0);
                servidor.createContext("/", new Handler());
                servidor.setExecutor(null);
                servidor.start();
                corriendo = true;
                System.out.println("✅ Servidor local corriendo en puerto " + PUERTO);

                // 2. Lanzar Ngrok en proceso separado
                lanzarNgrok();

            } catch (Exception e) {
                System.err.println("⚠ Servidor: " + e.getMessage());
            }
        }, "servidor-verificacion") {{ setDaemon(true); }}.start();
    }

    // ══════════════════════════════════════════
    //  Lanzar Ngrok y capturar URL
    // ══════════════════════════════════════════
    private static void lanzarNgrok() {
        new Thread(() -> {
            try {
                System.out.println("🔄 Iniciando Ngrok...");

                // Buscar ngrok en ubicaciones comunes
                String[] rutas = {
                        "ngrok",
                        "C:\\Users\\" + System.getProperty("user.name") +
                                "\\AppData\\Local\\Microsoft\\WindowsApps\\ngrok.exe",
                        "C:\\ngrok\\ngrok.exe",
                        "C:\\Users\\" + System.getProperty("user.name") + "\\ngrok.exe"
                };

                String ngrokPath = null;
                for (String ruta : rutas) {
                    try {
                        Process test = Runtime.getRuntime().exec(ruta + " version");
                        test.waitFor();
                        if (test.exitValue() == 0) { ngrokPath = ruta; break; }
                    } catch (Exception ignored) {}
                }

                if (ngrokPath == null) {
                    System.err.println("⚠ Ngrok no encontrado — QR usará localhost");
                    return;
                }

                // Matar instancias anteriores de ngrok
                Runtime.getRuntime().exec("taskkill /F /IM ngrok.exe");
                Thread.sleep(1000);

                // Lanzar ngrok http 8080
                ProcessBuilder pb = new ProcessBuilder(ngrokPath, "http", "8080");
                pb.redirectErrorStream(true);
                ngrokProcess = pb.start();

                System.out.println("✅ Ngrok lanzado, esperando URL...");

                // Esperar y obtener URL via API local de Ngrok
                Thread.sleep(3000);
                obtenerUrlNgrok();

            } catch (Exception e) {
                System.err.println("⚠ Error lanzando Ngrok: " + e.getMessage());
            }
        }, "ngrok-launcher") {{ setDaemon(true); }}.start();
    }

    // ══════════════════════════════════════════
    //  Obtener URL de Ngrok via su API local
    // ══════════════════════════════════════════
    private static void obtenerUrlNgrok() {
        // Reintentar hasta 10 veces
        for (int i = 0; i < 10; i++) {
            try {
                Thread.sleep(1500);

                // Ngrok expone su API en localhost:4040
                java.net.URL apiUrl = new java.net.URL("http://localhost:4040/api/tunnels");
                java.net.HttpURLConnection conn =
                        (java.net.HttpURLConnection) apiUrl.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String linea;
                    while ((linea = br.readLine()) != null) sb.append(linea);
                    br.close();

                    String json = sb.toString();

                    // Extraer URL pública (buscar "public_url":"https://...")
                    int idx = json.indexOf("\"public_url\":\"https://");
                    if (idx != -1) {
                        int inicio = idx + 14;
                        int fin    = json.indexOf("\"", inicio);
                        urlNgrok   = json.substring(inicio, fin);

                        System.out.println("✅ URL Ngrok obtenida: " + urlNgrok);

                        // Actualizar QRService con la nueva URL
                        actualizarUrlEnQRService(urlNgrok);
                        return;
                    }
                }
            } catch (Exception ignored) {}
        }
        System.err.println("⚠ No se pudo obtener URL de Ngrok automáticamente");
    }

    // ══════════════════════════════════════════
    //  Actualizar URL en QRService dinámicamente
    // ══════════════════════════════════════════
    private static void actualizarUrlEnQRService(String url) {
        try {
            // Buscar QRService.java en el proyecto
            String[] posiblesRutas = {
                    "src/main/java/app/servicios/QRService.java",
                    "BiometricUMG/src/main/java/app/servicios/QRService.java"
            };

            File qrFile = null;
            for (String ruta : posiblesRutas) {
                File f = new File(ruta);
                if (f.exists()) { qrFile = f; break; }
            }

            if (qrFile == null) {
                System.err.println("⚠ No se encontró QRService.java para actualizar");
                System.out.println("📋 Copia esta URL manualmente: " + url);
                return;
            }

            String contenido = new String(Files.readAllBytes(qrFile.toPath()));

            // Reemplazar la URL base
            String nuevoContenido = contenido.replaceAll(
                    "String baseUrl = \"https://[^\"]+\";",
                    "String baseUrl = \"" + url + "\";"
            );

            if (!nuevoContenido.equals(contenido)) {
                Files.write(qrFile.toPath(), nuevoContenido.getBytes());
                System.out.println("✅ QRService.java actualizado con URL: " + url);
                System.out.println("⚠ Rebuild el proyecto para aplicar el cambio en nuevos QR");
            } else {
                System.out.println("📋 URL de Ngrok para QRService: " + url);
            }

        } catch (Exception e) {
            System.err.println("⚠ Error actualizando QRService: " + e.getMessage());
            System.out.println("📋 URL de Ngrok: " + url);
        }
    }

    // ══════════════════════════════════════════
    //  Detener todo
    // ══════════════════════════════════════════
    public static void detener() {
        if (servidor != null) {
            servidor.stop(0);
            corriendo = false;
            System.out.println("🔒 Servidor HTTP detenido");
        }
        if (ngrokProcess != null) {
            ngrokProcess.destroy();
            System.out.println("🔒 Ngrok detenido");
        }
        try {
            Runtime.getRuntime().exec("taskkill /F /IM ngrok.exe");
        } catch (Exception ignored) {}
    }

    public static String  getUrlNgrok() { return urlNgrok; }
    public static boolean isCorriendo() { return corriendo; }

    // ══════════════════════════════════════════
    //  Handler HTTP
    // ══════════════════════════════════════════
    static class Handler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";

            File archivo = new File(CARPETA_WEB + path);

            exchange.getResponseHeaders().add("ngrok-skip-browser-warning", "true");
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Cache-Control", "no-cache");

            if (!archivo.exists()) {
                String msg = "404 - No encontrado: " + path;
                exchange.sendResponseHeaders(404, msg.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(msg.getBytes());
                }
                return;
            }

            String ct = "text/html; charset=UTF-8";
            if (path.endsWith(".png")) ct = "image/png";
            if (path.endsWith(".jpg")) ct = "image/jpeg";
            if (path.endsWith(".css")) ct = "text/css";

            exchange.getResponseHeaders().add("Content-Type", ct);
            byte[] bytes = Files.readAllBytes(archivo.toPath());
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}