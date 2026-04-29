package app.servicios;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

/**
 * SupabaseStorageService — BiometricUMG 4.0
 *
 * Sube archivos a Supabase Storage via REST y devuelve la URL pública.
 * No requiere dependencias externas — usa solo java.net.HttpURLConnection.
 *
 * Buckets necesarios en Supabase Dashboard → Storage:
 *   - "fotos"   → fotos de perfil (Public: true)
 *   - "rostros" → muestras faciales (Public: true o false)
 *
 * Configuración en db.properties:
 *   supabase.url=https://XXXXXX.supabase.co
 *   supabase.key=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...  (service_role key)
 */
public class SupabaseStorageService {

    private static String  SUPABASE_URL = null;
    private static String  SUPABASE_KEY = null;
    private static boolean configurado  = false;

    static { cargarConfig(); }

    private static void cargarConfig() {
        try (InputStream is = SupabaseStorageService.class
                .getResourceAsStream("/db.properties")) {
            Properties props = new Properties();
            if (is != null) {
                props.load(is);
                SUPABASE_URL = props.getProperty("supabase.url");
                SUPABASE_KEY = props.getProperty("supabase.key");
            }

            // Fallback: intentar derivar URL del proyecto desde la URL JDBC
            if (SUPABASE_URL == null || SUPABASE_URL.isBlank()) {
                String user = props.getProperty("db.user", "");
                if (user.startsWith("postgres.")) {
                    String ref = user.substring("postgres.".length());
                    SUPABASE_URL = "https://" + ref + ".supabase.co";
                }
                SUPABASE_KEY = props.getProperty("supabase.key",
                        props.getProperty("db.password", ""));
            }

            // Limpiar posibles espacios o saltos de línea en la clave
            if (SUPABASE_KEY != null) SUPABASE_KEY = SUPABASE_KEY.trim();
            if (SUPABASE_URL != null) SUPABASE_URL = SUPABASE_URL.trim();

            if (SUPABASE_URL != null && !SUPABASE_URL.isBlank()
                    && SUPABASE_KEY != null && !SUPABASE_KEY.isBlank()) {
                configurado = true;
                System.out.println("✅ SupabaseStorageService configurado: " + SUPABASE_URL);
            } else {
                System.err.println("⚠ SupabaseStorageService: agrega 'supabase.url' y 'supabase.key' a db.properties");
            }

        } catch (Exception e) {
            System.err.println("⚠ SupabaseStorageService init: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  API PÚBLICA
    // ════════════════════════════════════════════════════════════════

    /**
     * Sube una foto de perfil (BufferedImage) al bucket "fotos".
     * Retorna la URL pública en Supabase, o ruta local si falla la subida.
     */
    public static String subirFotoPerfil(BufferedImage imagen, String nombrePersona) {
        String rutaLocal = guardarLocalTemp(imagen, nombrePersona, "data/fotos/");
        if (rutaLocal == null) return null;

        if (!configurado) {
            System.out.println("ℹ Storage no configurado — usando ruta local: " + rutaLocal);
            return rutaLocal;
        }

        // FIX: sanitizar el nombre antes de construir el path en Supabase
        String nombreSanitizado = sanitizarNombre(nombrePersona);
        String path = "perfil/" + nombreSanitizado + "_" + System.currentTimeMillis() + ".jpg";

        String url = subirArchivo("fotos", path, new File(rutaLocal), "image/jpeg");
        if (url != null) {
            System.out.println("✅ Foto subida a Supabase: " + url);
            new File(rutaLocal).delete();
            return url;
        }

        System.out.println("⚠ Subida falló — conservando ruta local: " + rutaLocal);
        return rutaLocal;
    }

    /**
     * Sube una foto desde ruta local al bucket "fotos".
     */
    public static String subirFotoDesdeArchivo(String rutaLocal, String nombrePersona) {
        if (!configurado) return rutaLocal;
        File archivo = new File(rutaLocal);
        if (!archivo.exists()) return rutaLocal;

        String ext  = rutaLocal.toLowerCase().endsWith(".png") ? ".png" : ".jpg";
        String mime = rutaLocal.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
        String path = "perfil/" + sanitizarNombre(nombrePersona)
                + "_" + System.currentTimeMillis() + ext;

        String url = subirArchivo("fotos", path, archivo, mime);
        return url != null ? url : rutaLocal;
    }

    /**
     * Sube una muestra facial al bucket "rostros".
     */
    public static String subirMuestraFacial(File archivoLocal, int idPersona, String nombreMuestra) {
        if (!configurado) return archivoLocal.getPath();
        String path = "id_" + idPersona + "/" + sanitizarNombre(nombreMuestra);
        String url = subirArchivo("rostros", path, archivoLocal, "image/jpeg");
        return url != null ? url : archivoLocal.getPath();
    }

    /**
     * Registra en muestras_faciales la referencia a la muestra subida.
     */
    public static void registrarMuestraEnBD(int idUsuario, String nombreArchivo, String urlOPath) {
        String sql = """
            INSERT INTO muestras_faciales (id_usuario, nombre_archivo, fecha_captura)
            VALUES (?, ?, NOW())
            ON CONFLICT DO NOTHING
            """;
        try (java.sql.Connection con = app.conexion.Conexion.nuevaConexion();
             java.sql.PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt   (1, idUsuario);
            ps.setString(2, nombreArchivo);
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("⚠ registrarMuestraEnBD: " + e.getMessage());
        }
    }

    /**
     * Descarga todas las muestras de un usuario desde Supabase a disco local.
     */
    public static boolean descargarMuestrasFaciales(int idPersona) {
        String sql = "SELECT nombre_archivo FROM muestras_faciales WHERE id_usuario = ?";
        int descargadas = 0;
        try (java.sql.Connection con = app.conexion.Conexion.nuevaConexion();
             java.sql.PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idPersona);
            java.sql.ResultSet rs = ps.executeQuery();
            File carpetaLocal = new File("data/rostros/" + idPersona);
            carpetaLocal.mkdirs();
            while (rs.next()) {
                String nombre = rs.getString("nombre_archivo");
                File destino  = new File(carpetaLocal, nombre);
                if (destino.exists()) { descargadas++; continue; }
                String urlPublica = getUrlPublica("rostros", "id_" + idPersona + "/" + nombre);
                if (descargarArchivo(urlPublica, destino)) descargadas++;
            }
        } catch (Exception e) {
            System.err.println("⚠ descargarMuestrasFaciales: " + e.getMessage());
        }
        System.out.println("📥 Muestras descargadas para persona " + idPersona + ": " + descargadas);
        return descargadas > 0;
    }

    // ════════════════════════════════════════════════════════════════
    //  INTERNOS
    // ════════════════════════════════════════════════════════════════

    /**
     * Sube un archivo a Supabase Storage usando la API REST.
     *
     * FIXES aplicados respecto a la versión anterior:
     *  1. Método HTTP: POST (no PUT) con x-upsert:true → correcto para la API de Supabase.
     *     El endpoint /object/{bucket}/{path} acepta POST para crear/reemplazar.
     *  2. Path URL-encoded: los segmentos del path se codifican correctamente para
     *     evitar HTTP 400 por caracteres inválidos (espacios, paréntesis, tildes, etc.)
     *  3. Content-Length explícito: algunos servidores rechazan uploads sin este header.
     */
    private static String subirArchivo(String bucket, String path,
                                       File archivo, String contentType) {
        if (!configurado || archivo == null || !archivo.exists()) return null;
        try {
            // Codificar cada segmento del path por separado (no codificar las /)
            String pathCodificado = codificarPath(path);
            String endpoint = SUPABASE_URL + "/storage/v1/object/" + bucket + "/" + pathCodificado;

            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + SUPABASE_KEY);
            conn.setRequestProperty("Content-Type", contentType);
            conn.setRequestProperty("x-upsert", "true");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(30_000);

            byte[] datos = Files.readAllBytes(archivo.toPath());
            conn.setRequestProperty("Content-Length", String.valueOf(datos.length));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(datos);
                os.flush();
            }

            int status = conn.getResponseCode();

            // Leer cuerpo del error si falla (ayuda a depurar)
            if (status != 200 && status != 201) {
                try (InputStream err = conn.getErrorStream()) {
                    if (err != null) {
                        String body = new String(err.readAllBytes(), StandardCharsets.UTF_8);
                        System.err.println("⚠ Supabase Storage HTTP " + status
                                + " al subir " + path + " → " + body);
                    } else {
                        System.err.println("⚠ Supabase Storage HTTP " + status
                                + " al subir " + path);
                    }
                }
                conn.disconnect();
                return null;
            }

            conn.disconnect();
            return getUrlPublica(bucket, pathCodificado);

        } catch (Exception e) {
            System.err.println("❌ subirArchivo: " + e.getMessage());
            return null;
        }
    }

    /**
     * Codifica cada segmento de un path URL sin tocar las barras separadoras.
     * Ejemplo: "perfil/Juan García_123.jpg" → "perfil/Juan%20Garc%C3%ADa_123.jpg"
     */
    private static String codificarPath(String path) {
        String[] segmentos = path.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segmentos.length; i++) {
            if (i > 0) sb.append("/");
            sb.append(URLEncoder.encode(segmentos[i], StandardCharsets.UTF_8)
                    .replace("+", "%20")); // URLEncoder usa + para espacios; Supabase necesita %20
        }
        return sb.toString();
    }

    /**
     * Sanitiza un nombre para usarlo en paths de Storage:
     * elimina caracteres problemáticos y reemplaza espacios con guion bajo.
     */
    private static String sanitizarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) return "archivo";
        return nombre.trim()
                .replaceAll("[\\s]+", "_")           // espacios → _
                .replaceAll("[^a-zA-Z0-9._\\-]", ""); // solo alfanumérico, punto, guion
    }

    /** Construye la URL pública de un objeto en Supabase Storage. */
    public static String getUrlPublica(String bucket, String path) {
        return SUPABASE_URL + "/storage/v1/object/public/" + bucket + "/" + path;
    }

    /** Descarga un archivo desde una URL HTTP y lo guarda en destino. */
    private static boolean descargarArchivo(String urlStr, File destino) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Authorization", "Bearer " + SUPABASE_KEY);
            conn.setConnectTimeout(8_000);
            conn.setReadTimeout(15_000);
            if (conn.getResponseCode() != 200) return false;
            try (InputStream in = conn.getInputStream()) {
                Files.copy(in, destino.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            conn.disconnect();
            return true;
        } catch (Exception e) {
            System.err.println("⚠ descargarArchivo: " + e.getMessage());
            return false;
        }
    }

    /** Guarda una BufferedImage en disco como JPG y retorna la ruta. */
    private static String guardarLocalTemp(BufferedImage img,
                                           String nombre, String carpeta) {
        try {
            new File(carpeta).mkdirs();
            // Sanitizar el nombre también para el archivo local
            String ruta = carpeta + sanitizarNombre(nombre)
                    + "_" + System.currentTimeMillis() + ".jpg";
            ImageIO.write(img, "jpg", new File(ruta));
            return ruta;
        } catch (Exception e) {
            System.err.println("❌ guardarLocalTemp: " + e.getMessage());
            return null;
        }
    }

    public static boolean isConfigurado() { return configurado; }
}