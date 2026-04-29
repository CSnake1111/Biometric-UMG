package app.servicios;

import com.github.sarxos.webcam.Webcam;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.ImageView;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CamaraService {

    private static Webcam                   webcam;
    private static ScheduledExecutorService executor;
    private static volatile boolean         corriendo   = false;
    private static Thread                   hiloIP      = null;
    private static volatile boolean         corriendoIP = false;

    private static volatile BufferedImage ultimoFrame = null;

    public static boolean iniciarPreview(ImageView imageView) {
        return iniciarPreviewLocal(imageView, -1);
    }

    public static boolean iniciarPreviewLocal(ImageView imageView, int indice) {
        detener();
        try { Thread.sleep(400); } catch (InterruptedException ignored) {}

        try {
            List<Webcam> camaras = Webcam.getWebcams();
            if (camaras == null || camaras.isEmpty()) return false;

            if (indice >= 0 && indice < camaras.size()) {
                webcam = camaras.get(indice);
                System.out.println("📷 Usando cámara [" + indice + "]: " + webcam.getName());
            } else {
                webcam = null;
                for (Webcam candidata : camaras) {
                    String nombre = candidata.getName().toLowerCase();
                    if (!nombre.contains("camo") && !nombre.contains("obs") && !nombre.contains("virtual")) {
                        webcam = candidata;
                        System.out.println("📷 Usando cámara física: " + candidata.getName());
                        break;
                    }
                }
                if (webcam == null) {
                    webcam = camaras.get(0);
                    System.out.println("📷 Fallback a primera cámara: " + webcam.getName());
                }
            }

            if (webcam.isOpen()) {
                try { webcam.close(); Thread.sleep(300); } catch (Exception ignored) {}
            }
            Dimension elegido = detectarTamanoNativo(webcam);
            try {
                webcam.setCustomViewSizes(new Dimension[]{ elegido });
                webcam.setViewSize(elegido);
            } catch (Exception eRes) {
                System.out.println("⚠ No se pudo cambiar resolución: " + eRes.getMessage());
            }
            webcam.open();
            Thread.sleep(600);

            corriendo = true;
            executor  = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "camara-preview");
                t.setDaemon(true);
                return t;
            });
            executor.scheduleAtFixedRate(() -> {
                if (!corriendo || webcam == null || !webcam.isOpen()) return;
                try {
                    BufferedImage img = webcam.getImage();
                    if (img != null) {
                        ultimoFrame = img;
                        Platform.runLater(() -> imageView.setImage(SwingFXUtils.toFXImage(img, null)));
                    }
                } catch (Exception ignored) {}
            }, 0, 33, TimeUnit.MILLISECONDS);
            return true;

        } catch (Exception e) {
            System.err.println("❌ Error iniciando cámara local: " + e.getMessage());
            return false;
        }
    }

    private static Dimension detectarTamanoNativo(Webcam cam) {
        Dimension[] disponibles = cam.getViewSizes();
        if (disponibles != null && disponibles.length > 0) {
            for (Dimension d : disponibles)
                if (d.width == 1280 && d.height == 720) return d;
            for (Dimension d : disponibles)
                if (d.width == 640 && d.height == 480) return d;
            Dimension mayor = disponibles[0];
            for (Dimension d : disponibles)
                if (d.width > mayor.width) mayor = d;
            return mayor;
        }
        return new Dimension(1280, 720);
    }

    // ══════════════════════════════════════════
    //  Cámara IP
    // ══════════════════════════════════════════
    public static boolean iniciarPreviewIP(ImageView imageView, String urlStream) {
        detener();
        corriendoIP = true;
        hiloIP = new Thread(() -> {
            try {
                URL url = new URL(urlStream);
                java.io.InputStream stream = url.openStream();
                while (corriendoIP && !Thread.currentThread().isInterrupted()) {
                    try {
                        int b1 = stream.read(), b2 = stream.read();
                        if (b1 == -1 || b2 == -1) break;
                        if (b1 == 0xFF && b2 == 0xD8) {
                            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                            baos.write(b1); baos.write(b2);
                            int prev = -1, curr;
                            while ((curr = stream.read()) != -1) {
                                baos.write(curr);
                                if (prev == 0xFF && curr == 0xD9) break;
                                prev = curr;
                            }
                            BufferedImage img = ImageIO.read(
                                    new java.io.ByteArrayInputStream(baos.toByteArray()));
                            if (img != null) {
                                ultimoFrame = img;
                                final BufferedImage fi = img;
                                Platform.runLater(() -> imageView.setImage(SwingFXUtils.toFXImage(fi, null)));
                            }
                        }
                    } catch (Exception ignored) {}
                }
                try { stream.close(); } catch (Exception ignored) {}
            } catch (Exception e) {
                System.err.println("❌ Cámara IP stream error: " + e.getMessage());
                if (corriendoIP)
                    iniciarPreviewIPSnapshot(imageView, urlStream.replace("/video", "/shot.jpg"));
            }
        }, "camara-ip-stream");
        hiloIP.setDaemon(true);
        hiloIP.start();
        return true;
    }

    private static void iniciarPreviewIPSnapshot(ImageView imageView, String snapshotUrl) {
        corriendoIP = true;
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "camara-ip-snapshot");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(() -> {
            if (!corriendoIP) return;
            try {
                java.net.HttpURLConnection conn =
                        (java.net.HttpURLConnection) new URL(snapshotUrl).openConnection();
                conn.setConnectTimeout(1500); conn.setReadTimeout(1500);
                BufferedImage img = ImageIO.read(conn.getInputStream());
                if (img != null) {
                    ultimoFrame = img;
                    final BufferedImage fi = img;
                    Platform.runLater(() -> imageView.setImage(SwingFXUtils.toFXImage(fi, null)));
                }
                conn.disconnect();
            } catch (Exception ignored) {}
        }, 0, 200, TimeUnit.MILLISECONDS);
    }

    public static BufferedImage getUltimoFrame() { return ultimoFrame; }

    // ══════════════════════════════════════════
    //  CAPTURA DE FOTO — sube a Supabase Storage
    // ══════════════════════════════════════════

    /**
     * Captura la foto actual de la cámara, la sube a Supabase Storage
     * (bucket "fotos") y retorna la URL pública.
     * Si Supabase no está configurado, guarda localmente y retorna la ruta.
     *
     * @param nombrePersona nombre del usuario (para el nombre del archivo)
     * @return URL pública de Supabase o ruta local, null si falla la captura
     */
    public static String capturarFoto(String nombrePersona) {
        if (webcam == null || !webcam.isOpen()) {
            // Intentar usar ultimoFrame si la cámara ya se cerró
            if (ultimoFrame == null) return null;
        }

        try {
            BufferedImage imagen = (webcam != null && webcam.isOpen())
                    ? webcam.getImage()
                    : ultimoFrame;
            if (imagen == null) return null;

            // Delegar a SupabaseStorageService: sube a nube y retorna URL
            String url = SupabaseStorageService.subirFotoPerfil(imagen, nombrePersona);
            System.out.println("📸 Foto capturada → " + url);
            return url;

        } catch (Exception e) {
            System.err.println("❌ Error capturando foto: " + e.getMessage());
            return null;
        }
    }

    /**
     * Versión que acepta un File ya existente en disco y lo sube a Supabase.
     * Úsala cuando el usuario carga una foto desde archivo local.
     */
    public static String subirFotoExistente(String rutaLocal, String nombrePersona) {
        return SupabaseStorageService.subirFotoDesdeArchivo(rutaLocal, nombrePersona);
    }

    public static BufferedImage capturarFrame() {
        try {
            if (webcam != null && webcam.isOpen()) return webcam.getImage();
        } catch (Exception ignored) {}
        return ultimoFrame;
    }

    public static List<String> listarCamaras() {
        List<String> nombres = new java.util.ArrayList<>();
        try {
            List<Webcam> cams = Webcam.getWebcams();
            for (int i = 0; i < cams.size(); i++) nombres.add("[" + i + "] " + cams.get(i).getName());
        } catch (Exception e) { nombres.add("[0] Cámara predeterminada"); }
        return nombres;
    }

    // ══════════════════════════════════════════
    //  MÉTODOS LEGACY (mantenidos por compatibilidad)
    // ══════════════════════════════════════════

    /**
     * @deprecated Usar capturarFoto() que ahora sube a Supabase Storage.
     *             Mantenido para no romper código que aún lo llame.
     */
    @Deprecated
    public static void guardarFotoEnBD(int idPersona, String rutaFoto) {
        System.out.println("ℹ guardarFotoEnBD() ya no es necesario — la foto se sube via SupabaseStorageService.");
    }

    /**
     * @deprecated Las muestras ahora se suben a Supabase Storage en ReconocimientoFacialService.
     */
    @Deprecated
    public static void guardarMuestrasFacialesEnBD(int idPersona) {
        // Sincronizar muestras locales al Storage
        File carpeta = new File("data/rostros/" + idPersona);
        if (!carpeta.exists()) return;
        File[] muestras = carpeta.listFiles((d, n) -> n.endsWith(".jpg") || n.endsWith(".png"));
        if (muestras == null) return;
        for (File m : muestras) {
            SupabaseStorageService.subirMuestraFacial(m, idPersona, m.getName());
            SupabaseStorageService.registrarMuestraEnBD(idPersona, m.getName(), m.getPath());
        }
    }

    public static boolean cargarMuestrasFacialesDesdeDB(int idPersona) {
        return SupabaseStorageService.descargarMuestrasFaciales(idPersona);
    }

    // ══════════════════════════════════════════
    //  DETENER
    // ══════════════════════════════════════════
    public static void detener() {
        corriendo   = false;
        corriendoIP = false;
        ultimoFrame = null;

        if (hiloIP != null) {
            hiloIP.interrupt();
            try { hiloIP.join(1000); } catch (InterruptedException ignored) {}
            hiloIP = null;
        }

        if (executor != null) {
            executor.shutdownNow();
            try { executor.awaitTermination(1000, TimeUnit.MILLISECONDS); }
            catch (InterruptedException ignored) {}
            executor = null;
        }

        if (webcam != null) {
            final Webcam cam = webcam;
            webcam = null;
            Thread cierre = new Thread(() -> {
                try {
                    if (cam.isOpen()) {
                        cam.close();
                        System.out.println("🔒 Cámara cerrada correctamente.");
                    }
                } catch (Exception e) {
                    System.err.println("⚠ Error cerrando cámara: " + e.getMessage());
                }
            }, "camara-cierre");
            cierre.setDaemon(true);
            cierre.start();
            try { cierre.join(3000); } catch (InterruptedException ignored) {}
        }
    }

    public static boolean isCorriendo() {
        return (corriendo && webcam != null && webcam.isOpen()) || corriendoIP;
    }
}
