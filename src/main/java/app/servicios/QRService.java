package app.servicios;

import app.modelos.Persona;
import app.modelos.Usuario;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

public class QRService {

    private static final String CARPETA_QR  = "data/qr/";
    private static final String CARPETA_WEB = "data/verificacion/";
    private static final int    TAMANIO_QR  = 300;

    // ══════════════════════════════════════════
    //  Generar QR completo con página web
    // ══════════════════════════════════════════
    public static String generarCodigoQR(int idPersona, String nombre, String carne) {
        new File(CARPETA_QR).mkdirs();
        new File(CARPETA_WEB).mkdirs();

        // Obtener URL de Ngrok dinámicamente si está disponible
        String baseUrl = ServidorVerificacion.getUrlNgrok();

        // Fallback: intentar leer desde archivo de configuración
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = leerUrlGuardada();
        }

        // Último fallback: localhost
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:8080";
            System.out.println("⚠ Ngrok no disponible, usando localhost");
        }

        String urlVerificacion = baseUrl + "/verificar_" + idPersona + ".html";

        try {
            generarArchivoQR(urlVerificacion, CARPETA_QR + "qr_" + idPersona + ".png");
            System.out.println("✅ QR generado → " + urlVerificacion);
        } catch (Exception e) {
            System.err.println("❌ Error generando QR: " + e.getMessage());
        }

        return "UMG-" + String.format("%06d", idPersona);
    }

    // ══════════════════════════════════════════
//  Leer URL guardada desde archivo de config
// ══════════════════════════════════════════
    private static String leerUrlGuardada() {
        try {
            File config = new File("data/ngrok_url.txt");
            if (config.exists()) {
                String url = new String(
                        java.nio.file.Files.readAllBytes(config.toPath())).trim();
                if (!url.isEmpty()) {
                    System.out.println("✅ URL Ngrok cargada desde config: " + url);
                    return url;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ══════════════════════════════════════════
    //  Generar página web de verificación
    // ══════════════════════════════════════════
    public static void generarPaginaVerificacion(Persona p, String firmaCompleta) {
        new File(CARPETA_WEB).mkdirs();

        // FIX #7: soportar tanto URL Supabase (https://...) como ruta local para la foto.
        // Para HTML incrustado necesitamos base64 si es local, o la URL directa si es Supabase.
        String fotoBase64 = "";
        String fotoHtml   = "";  // se usará en el HTML como src del img
        if (p.getFoto() != null && !p.getFoto().isBlank()) {
            if (p.getFoto().startsWith("http://") || p.getFoto().startsWith("https://")) {
                // URL pública de Supabase — se puede usar directamente como src
                fotoHtml = p.getFoto();
            } else if (new File(p.getFoto()).exists()) {
                try {
                    byte[] bytes = new java.io.FileInputStream(p.getFoto()).readAllBytes();
                    fotoBase64 = "data:image/jpeg;base64," +
                            java.util.Base64.getEncoder().encodeToString(bytes);
                    fotoHtml = fotoBase64;
                } catch (Exception ignored) {}
            }
        }

        // Logo en base64
        String logoBase64 = "";
        try {
            InputStream logoStream = QRService.class
                    .getResourceAsStream("/images/logo_umg.png");
            if (logoStream != null) {
                byte[] bytes = logoStream.readAllBytes();
                logoBase64 = "data:image/png;base64," +
                        java.util.Base64.getEncoder().encodeToString(bytes);
            }
        } catch (Exception ignored) {}

        // Verificar firma
        String datosVerificacion = p.getIdPersona() + "|" + p.getNombreCompleto()
                + "|" + p.getCarne() + "|" + p.getCorreo();
        boolean firmaValida = firmaCompleta != null &&
                FirmaService.verificar(datosVerificacion, firmaCompleta);

        String estadoFirma = firmaValida
                ? "<span style='color:#27ae60'>✅ FIRMA VÁLIDA — Documento auténtico</span>"
                : "<span style='color:#e74c3c'>⚠️ Firma no verificada</span>";

        String html = """
            <!DOCTYPE html>
            <html lang="es">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Verificación — %s</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: 'Segoe UI', Arial, sans-serif;
                        background: linear-gradient(135deg, #0a0f1e 0%%, #0d1829 100%%);
                        min-height: 100vh;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        padding: 20px;
                    }
                    .card {
                        background: #0e1628;
                        border: 1px solid #1e2d47;
                        border-radius: 16px;
                        max-width: 480px;
                        width: 100%%;
                        overflow: hidden;
                        box-shadow: 0 20px 60px rgba(0,0,0,0.5);
                    }
                    .header {
                        background: linear-gradient(135deg, #003366, #1a4f8c);
                        padding: 24px;
                        text-align: center;
                        display: flex;
                        align-items: center;
                        gap: 16px;
                    }
                    .header img.logo {
                        width: 60px; height: 60px;
                        border-radius: 50%%;
                    }
                    .header-text h1 {
                        color: white;
                        font-size: 16px;
                        font-weight: bold;
                        text-align: left;
                    }
                    .header-text p {
                        color: rgba(255,255,255,0.6);
                        font-size: 11px;
                        text-align: left;
                    }
                    .foto-section {
                        display: flex;
                        align-items: center;
                        gap: 20px;
                        padding: 24px;
                        border-bottom: 1px solid #1e2d47;
                    }
                    .foto {
                        width: 100px; height: 100px;
                        border-radius: 50%%;
                        border: 3px solid #1a4f8c;
                        object-fit: cover;
                        flex-shrink: 0;
                    }
                    .foto-placeholder {
                        width: 100px; height: 100px;
                        border-radius: 50%%;
                        border: 3px solid #1e2d47;
                        background: #152035;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        font-size: 36px;
                        flex-shrink: 0;
                    }
                    .nombre-section h2 {
                        color: #e8edf5;
                        font-size: 20px;
                        margin-bottom: 4px;
                    }
                    .tipo-badge {
                        background: #1a4f8c;
                        color: #adc8e8;
                        font-size: 10px;
                        font-weight: bold;
                        padding: 3px 12px;
                        border-radius: 20px;
                        display: inline-block;
                        margin-bottom: 6px;
                    }
                    .nombre-section p {
                        color: #4a5a78;
                        font-size: 12px;
                    }
                    .datos {
                        padding: 20px 24px;
                        border-bottom: 1px solid #1e2d47;
                    }
                    .dato-row {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        padding: 8px 0;
                        border-bottom: 1px solid #0d1829;
                    }
                    .dato-row:last-child { border-bottom: none; }
                    .dato-label {
                        color: #4a5a78;
                        font-size: 11px;
                        font-weight: bold;
                        text-transform: uppercase;
                    }
                    .dato-valor {
                        color: #e8edf5;
                        font-size: 12px;
                        text-align: right;
                    }
                    .firma-section {
                        padding: 16px 24px;
                        background: #0a0f1e;
                        border-bottom: 1px solid #1e2d47;
                    }
                    .firma-title {
                        color: #4a5a78;
                        font-size: 10px;
                        font-weight: bold;
                        text-transform: uppercase;
                        margin-bottom: 10px;
                        letter-spacing: 1px;
                    }
                    .firma-badges {
                        display: flex;
                        gap: 10px;
                    }
                    .firma-badge {
                        display: inline-flex;
                        align-items: center;
                        font-size: 13px;
                        font-weight: bold;
                        padding: 8px 18px;
                        border-radius: 24px;
                    }
                    .firma-ok {
                        background: rgba(39,174,96,0.15);
                        border: 1.5px solid #27ae60;
                        color: #27ae60;
                    }
                    .firma-ver {
                        background: rgba(26,79,140,0.18);
                        border: 1.5px solid #1a4f8c;
                        color: #adc8e8;
                    }
                    .firma-hash {
                        font-family: monospace;
                        font-size: 9px;
                        color: #2e5fad;
                        word-break: break-all;
                        background: #0e1628;
                        padding: 8px;
                        border-radius: 6px;
                        border: 1px solid #1e2d47;
                        margin-top: 10px;
                    }
                    .footer {
                        background: #b40000;
                        padding: 12px 24px;
                        text-align: center;
                    }
                    .footer p {
                        color: rgba(255,255,255,0.8);
                        font-size: 11px;
                    }
                    .verificado-badge {
                        display: inline-flex;
                        align-items: center;
                        gap: 6px;
                        background: rgba(39,174,96,0.15);
                        border: 1px solid #27ae60;
                        color: #27ae60;
                        font-size: 11px;
                        font-weight: bold;
                        padding: 6px 14px;
                        border-radius: 20px;
                        margin-top: 8px;
                    }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="header">
                        %s
                        <div class="header-text">
                            <h1>Universidad Mariano Gálvez</h1>
                            <p>Sede La Florida, Zona 19</p>
                            <p>BiometricUMG 2.0 — Verificación de Identidad</p>
                        </div>
                    </div>

                    <div class="foto-section">
                        %s
                        <div class="nombre-section">
                            <h2>%s</h2>
                            <div class="tipo-badge">%s</div>
                            <p>%s</p>
                            <div class="verificado-badge">✅ Verificado</div>
                        </div>
                    </div>

                    <div class="datos">
                        <div class="dato-row">
                            <span class="dato-label">Carné</span>
                            <span class="dato-valor">%s</span>
                        </div>
                        <div class="dato-row">
                            <span class="dato-label">Correo UMG</span>
                            <span class="dato-valor">%s</span>
                        </div>
                        <div class="dato-row">
                            <span class="dato-label">Carrera</span>
                            <span class="dato-valor">%s</span>
                        </div>
                    </div>

                    <div class="firma-section">
                        <div class="firma-title">🔐 Firma Electrónica</div>
                        <div class="firma-badges">
                            <span class="firma-badge firma-ok">✅ FIRMA</span>
                            <span class="firma-badge firma-ver">✅ Verificado</span>
                        </div>
                        <div class="firma-hash">%s</div>
                    </div>

                    <div class="footer">
                        <p>BiometricUMG 2.0 · Universidad Mariano Gálvez · 2026</p>
                        <p>Este documento es de carácter oficial</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                p.getNombreCompleto(),
                logoBase64.isEmpty() ? ""
                        : "<img class='logo' src='" + logoBase64 + "' alt='UMG'>",
                fotoHtml.isEmpty()
                        ? "<div class='foto-placeholder'>👤</div>"
                        : "<img class='foto' src='" + fotoHtml + "' alt='Foto'>",
                p.getNombreCompleto(),
                p.getTipoPersona() != null ? p.getTipoPersona().toUpperCase() : "ESTUDIANTE",
                p.getCorreo(),
                p.getCarne() != null ? p.getCarne() : "—",
                p.getCorreo(),
                p.getCarrera() != null ? p.getCarrera() : "—",
                firmaCompleta != null ? firmaCompleta : "No disponible"
        );

        try (FileWriter fw = new FileWriter(CARPETA_WEB + "verificar_" + p.getIdPersona() + ".html")) {
            fw.write(html);
            System.out.println("✅ Página de verificación generada para: " + p.getNombreCompleto());
        } catch (Exception e) {
            System.err.println("❌ Error generando página web: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════
    //  Métodos existentes
    // ══════════════════════════════════════════
    public static void generarArchivoQR(String contenido, String rutaArchivo)
            throws Exception {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 2);

        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(contenido, BarcodeFormat.QR_CODE,
                TAMANIO_QR, TAMANIO_QR, hints);

        Path path = FileSystems.getDefault().getPath(rutaArchivo);
        MatrixToImageWriter.writeToPath(matrix, "PNG", path);
    }

    public static BufferedImage generarImagenQR(String contenido) throws Exception {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 2);
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(contenido, BarcodeFormat.QR_CODE,
                TAMANIO_QR, TAMANIO_QR, hints);
        return MatrixToImageWriter.toBufferedImage(matrix);
    }

    public static byte[] generarBytesQR(String contenido) throws Exception {
        BufferedImage imagen = generarImagenQR(contenido);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(imagen, "PNG", baos);
        return baos.toByteArray();
    }

    public static String getRutaQR(int idPersona) {
        return CARPETA_QR + "qr_" + idPersona + ".png";
    }

    public static boolean existeQR(int idPersona) {
        return new File(getRutaQR(idPersona)).exists();
    }

    // ══════════════════════════════════════════
    //  SOBRECARGA: acepta Usuario (tabla unificada)
    // ══════════════════════════════════════════
    public static void generarPaginaVerificacion(Usuario u, String firmaCompleta) {
        Persona p = new Persona();
        p.setIdPersona  (u.getIdUsuario());
        p.setNombre     (u.getNombre());
        p.setApellido   (u.getApellido());
        p.setCorreo     (u.getCorreo());
        p.setTipoPersona(u.getTipoPersona());
        p.setCarrera    (u.getCarrera());
        p.setSeccion    (u.getSeccion());
        p.setCarne      (u.getCarne());
        p.setFoto       (u.getFoto());
        p.setQrCodigo   (u.getQrCodigo());
        p.setEstado     (u.isEstado());
        p.setFechaRegistro(u.getFechaRegistro());
        generarPaginaVerificacion(p, firmaCompleta);
    }
}