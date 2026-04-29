package app.servicios;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ReconocimientoFacialService {

    private static final String CARPETA_ROSTROS = "data/rostros/";
    private static final String LABELS_FILE     = "data/labels.txt";
    private static final int    ANCHO_CARA = 150;
    private static final int    ALTO_CARA  = 150;

    private static boolean           opencvCargado = false;
    private static CascadeClassifier detector;
    private static VideoCapture      camaraCompartida;

    private static final Map<Integer, Integer>   labelAPersona = new HashMap<>();
    private static final Map<Integer, List<Mat>> muestrasCache = new HashMap<>();
    private static final AtomicBoolean cancelarEnrolamiento = new AtomicBoolean(false);

    // ── Índice de cámara seleccionado en la UI ──
    private static volatile int indiceCamaraSeleccionada = 0;

    /** Registra el índice de cámara elegido en el ComboBox de cualquier pantalla. */
    public static void setIndiceCamara(int idx) {
        if (idx >= 0) indiceCamaraSeleccionada = idx;
    }

    /** Retorna el índice de cámara actualmente seleccionado. */
    public static int getIndiceCamara() { return indiceCamaraSeleccionada; }

    public static void cancelarEnrolamiento()  { cancelarEnrolamiento.set(true); }
    public static void resetCancelacion()      { cancelarEnrolamiento.set(false); }
    public static boolean isCancelado()        { return cancelarEnrolamiento.get(); }

    public static boolean inicializar() {
        if (opencvCargado) { cargarLabels(); cargarMuestrasCache(); return true; }
        if (!cargarLibreriaNativa()) {
            System.err.println("❌ OpenCV no disponible.");
            return false;
        }
        new File(CARPETA_ROSTROS).mkdirs();
        new File("data").mkdirs();
        String cascadePath = resolverCascade();
        detector = new CascadeClassifier();
        if (cascadePath != null && !detector.load(cascadePath))
            System.err.println("⚠ Cascade load() falló: " + cascadePath);
        if (detector.empty())
            System.err.println("⚠ Detector facial no disponible (cascade vacío).");
        cargarLabels();
        cargarMuestrasCache();
        return true;
    }

    private static boolean cargarLibreriaNativa() {
        String[] nombres = {"opencv_java4100","opencv_java490","opencv_java480",
                "opencv_java470","opencv_java460","opencv_java450",
                "opencv_java4100.dll","opencv_java490.dll","libopencv_java4100.so"};
        String[] rutas = {"lib/","lib/opencv/","../lib/",".",System.getProperty("user.dir")+"/lib/"};
        for (String ruta : rutas) for (String nombre : nombres) {
            File f = new File(ruta + nombre);
            if (f.exists()) { try { System.load(f.getAbsolutePath()); opencvCargado=true;
                System.out.println("✅ OpenCV desde: "+f.getAbsolutePath()); return true;
            } catch (UnsatisfiedLinkError ignored) {} }
        }
        for (String n : new String[]{"opencv_java4100","opencv_java490","opencv_java480","opencv_java470","opencv_java460","opencv_java450"}) {
            try { System.loadLibrary(n); opencvCargado=true;
                System.out.println("✅ OpenCV loadLibrary: "+n); return true;
            } catch (UnsatisfiedLinkError ignored) {}
        }
        try { System.loadLibrary(Core.NATIVE_LIBRARY_NAME); opencvCargado=true; return true; }
        catch (UnsatisfiedLinkError e) { System.err.println("❌ No se encontró OpenCV nativo."); return false; }
    }

    private static String resolverCascade() {
        String[] posibles = {"data/haarcascade_frontalface_default.xml",
                "lib/haarcascade_frontalface_default.xml","haarcascade_frontalface_default.xml"};
        for (String ruta : posibles) if (new File(ruta).exists()) return ruta;
        try (InputStream is = ReconocimientoFacialService.class.getResourceAsStream("/haarcascade_frontalface_default.xml")) {
            if (is != null) {
                File temp = File.createTempFile("cascade_",".xml"); temp.deleteOnExit();
                Files.copy(is, temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return temp.getAbsolutePath();
            }
        } catch (Exception e) { System.err.println("⚠ No se pudo extraer cascade: "+e.getMessage()); }
        return null;
    }

    // ════════════════════════════════════════════════════════════════
    //  ENROLAMIENTO — guarda local + sube a Supabase Storage
    // ════════════════════════════════════════════════════════════════

    public static int enrolarDesdeFramesSarxos(int idPersona, int numMuestras, int offset,
                                               javafx.scene.control.Label lblStatus,
                                               javafx.scene.control.ProgressBar progreso) {
        if (!opencvCargado && !inicializar()) return 0;
        if (detector == null || detector.empty()) { System.err.println("❌ Detector no disponible"); return 0; }

        String carpeta = CARPETA_ROSTROS + idPersona + "/";
        new File(carpeta).mkdirs();

        int guardadas = 0, intentosSinRostro = 0;
        final int MAX_INTENTOS = 300;

        while (guardadas < numMuestras && intentosSinRostro < MAX_INTENTOS) {
            if (cancelarEnrolamiento.get() || Thread.currentThread().isInterrupted()) break;

            java.awt.image.BufferedImage bi = CamaraService.capturarFrame();
            if (bi == null) { sleep(100); intentosSinRostro++; continue; }

            try {
                Mat frame = bufferedImageToMat(bi);
                if (frame.empty()) { sleep(100); intentosSinRostro++; continue; }
                Mat gris = preprocesar(frame);
                MatOfRect rostros = new MatOfRect();
                detector.detectMultiScale(gris, rostros, 1.1, 5, 0, new Size(80,80), new Size());
                Rect[] det = rostros.toArray();
                if (det.length == 0) { intentosSinRostro++; sleep(100); continue; }
                intentosSinRostro = 0;

                String nombreMuestra = "muestra_" + (offset + guardadas) + ".jpg";
                String rutaMuestra   = carpeta + nombreMuestra;
                guardarMuestra(new Mat(gris, det[0]), rutaMuestra);
                guardadas++;

                final int    idP  = idPersona;
                final String nom  = nombreMuestra;
                final File   arch = new File(rutaMuestra);
                new Thread(() -> {
                    SupabaseStorageService.subirMuestraFacial(arch, idP, nom);
                    SupabaseStorageService.registrarMuestraEnBD(idP, nom, arch.getPath());
                }, "upload-muestra-" + guardadas) {{ setDaemon(true); }}.start();

                final int tot  = guardadas;
                final double prog = (double) tot / numMuestras;
                if (lblStatus != null) javafx.application.Platform.runLater(() -> {
                    lblStatus.setText("📡 Capturando... " + tot + "/" + numMuestras);
                    if (progreso != null) progreso.setProgress(prog);
                });
            } catch (Exception e) { System.err.println("⚠ Frame error: " + e.getMessage()); }
            sleep(150);
        }

        if (guardadas > 0) {
            labelAPersona.put(obtenerOCrearLabel(idPersona), idPersona);
            guardarLabels();
            cargarMuestrasCache();
        }
        if (intentosSinRostro >= MAX_INTENTOS && lblStatus != null)
            javafx.application.Platform.runLater(() ->
                    lblStatus.setText("⚠ No se detectó rostro. Acércate a la cámara."));

        System.out.println("✅ " + guardadas + " muestras Sarxos (offset=" + offset + ") para persona " + idPersona);
        return guardadas;
    }

    public static int enrolarRostro(int idPersona, int numMuestras) {
        if (CamaraService.capturarFrame() != null)
            return enrolarDesdeFramesSarxos(idPersona, numMuestras, 0, null, null);
        return enrolarRostroOpenCV(idPersona, numMuestras, 0);
    }

    public static int enrolarRostroDesdeOffset(int idPersona, int numMuestras, int offset) {
        if (CamaraService.capturarFrame() != null)
            return enrolarDesdeFramesSarxos(idPersona, numMuestras, offset, null, null);
        return enrolarRostroOpenCV(idPersona, numMuestras, offset);
    }

    private static int enrolarRostroOpenCV(int idPersona, int numMuestras, int offset) {
        if (!opencvCargado && !inicializar()) return 0;
        if (detector == null || detector.empty() || cancelarEnrolamiento.get()) return 0;
        VideoCapture camara = abrirCamaraOpenCV();
        if (camara == null) return 0;
        String carpeta = CARPETA_ROSTROS + idPersona + "/";
        new File(carpeta).mkdirs();
        int guardadas = 0, fallosCamara = 0;
        final int MAX_FALLOS_CAMARA = 150;
        Mat frame = new Mat(); MatOfRect rostros = new MatOfRect();
        while (guardadas < numMuestras) {
            if (cancelarEnrolamiento.get() || Thread.currentThread().isInterrupted()) break;
            if (!camara.read(frame) || frame.empty()) {
                fallosCamara++;
                if (fallosCamara >= MAX_FALLOS_CAMARA) {
                    System.err.println("❌ enrolarRostroOpenCV: cámara no responde. Abortando.");
                    break;
                }
                sleep(100); continue;
            }
            fallosCamara = 0;
            Mat gris = preprocesar(frame);
            detector.detectMultiScale(gris, rostros, 1.1, 5, 0, new Size(80,80), new Size());
            for (Rect r : rostros.toArray()) {
                String nombreMuestra = "muestra_" + (offset + guardadas) + ".jpg";
                String rutaMuestra   = carpeta + nombreMuestra;
                guardarMuestra(new Mat(gris, r), rutaMuestra);
                guardadas++;

                final int    idP  = idPersona;
                final String nom  = nombreMuestra;
                final File   arch = new File(rutaMuestra);
                new Thread(() -> {
                    SupabaseStorageService.subirMuestraFacial(arch, idP, nom);
                    SupabaseStorageService.registrarMuestraEnBD(idP, nom, arch.getPath());
                }, "upload-muestra-ocv-" + guardadas) {{ setDaemon(true); }}.start();
                break;
            }
            sleep(200);
        }
        camara.release();
        if (guardadas > 0) { labelAPersona.put(obtenerOCrearLabel(idPersona), idPersona); guardarLabels(); cargarMuestrasCache(); }
        System.out.println("✅ " + guardadas + " muestras OpenCV para persona " + idPersona);
        return guardadas;
    }

    public static int reenrolarRostro(int idPersona, javafx.scene.control.Label lblStatus,
                                      javafx.scene.control.ProgressBar progreso) {
        if (!opencvCargado && !inicializar()) return 0;
        if (detector == null || detector.empty()) return 0;
        cancelarEnrolamiento.set(false);
        File carpeta = new File(CARPETA_ROSTROS + idPersona + "/");
        if (carpeta.exists()) { File[] v = carpeta.listFiles(); if (v != null) for (File f : v) f.delete(); }
        carpeta.mkdirs();
        String[] instrucciones = {"😐 Mira al frente","👈 Gira a la izquierda","👉 Gira a la derecha","🙂 Inclina la cabeza"};
        int[] muestrasPorFase = {25,15,15,5};
        int totalCapturadas = 0;
        for (int f = 0; f < instrucciones.length; f++) {
            if (cancelarEnrolamiento.get()) break;
            final String instruccion = instrucciones[f];
            for (int c = 3; c >= 1; c--) {
                if (cancelarEnrolamiento.get()) break;
                final int cuenta = c;
                if (lblStatus != null) javafx.application.Platform.runLater(() -> lblStatus.setText(instruccion + "  ⏳ " + cuenta + "s"));
                sleep(1000);
            }
            if (lblStatus != null) javafx.application.Platform.runLater(() -> lblStatus.setText("📡 " + instruccion));
            totalCapturadas += enrolarDesdeFramesSarxos(idPersona, muestrasPorFase[f], totalCapturadas, lblStatus, progreso);
        }
        if (totalCapturadas > 0) entrenarModelo();
        final int total = totalCapturadas;
        if (lblStatus != null) javafx.application.Platform.runLater(() ->
                lblStatus.setText(cancelarEnrolamiento.get() ? "⚠ Cancelado — " + total + " muestras" : "✅ " + total + " muestras capturadas."));
        return totalCapturadas;
    }

    // ════════════════════════════════════════════════════════════════
    //  IDENTIFICACIÓN
    // ════════════════════════════════════════════════════════════════

    public static int identificarDesdeFrame(java.awt.image.BufferedImage buffered) {
        if (!opencvCargado && !inicializar()) return -1;
        if (detector == null || detector.empty() || muestrasCache.isEmpty() || buffered == null) return -1;
        try {
            Mat frame = bufferedImageToMat(buffered);
            if (frame.empty()) return -1;
            Mat gris = preprocesar(frame);
            MatOfRect rostros = new MatOfRect();
            detector.detectMultiScale(gris, rostros, 1.1, 4, 0, new Size(60,60), new Size());
            for (Rect r : rostros.toArray()) { int id = identificarCara(new Mat(gris,r)); if (id != -1) return id; break; }
        } catch (Exception e) { System.err.println("⚠ identificarDesdeFrame: " + e.getMessage()); }
        return -1;
    }

    public static int identificarDesdeCamera() {
        // FIX: Si CamaraService (sarxos) ya tiene la cámara abierta, NO abrir
        // un VideoCapture paralelo — causaría conflicto de acceso y bloqueo.
        if (CamaraService.isCorriendo()) return -1;
        if (!opencvCargado && !inicializar()) return -1;
        if (detector == null || detector.empty() || muestrasCache.isEmpty()) return -1;
        VideoCapture cam = getCamaraCompartida();
        if (!cam.isOpened()) return -1;
        Mat frame = new Mat(); MatOfRect rostros = new MatOfRect();
        for (int i = 0; i < 5; i++) {
            if (!cam.read(frame) || frame.empty()) { sleep(50); continue; }
            Mat gris = preprocesar(frame);
            detector.detectMultiScale(gris, rostros, 1.1, 4, 0, new Size(60,60), new Size());
            for (Rect r : rostros.toArray()) { int id = identificarCara(new Mat(gris,r)); if (id != -1) return id; break; }
            sleep(50);
        }
        return -1;
    }

    private static int identificarCara(Mat cara) {
        Mat caraRedim = new Mat();
        Imgproc.resize(cara, caraRedim, new Size(ANCHO_CARA, ALTO_CARA));
        int mejorLabel = -1; double mejorDist = Double.MAX_VALUE;
        for (Map.Entry<Integer,List<Mat>> e : muestrasCache.entrySet()) {
            double dist = compararConMuestras(caraRedim, e.getValue());
            if (dist < mejorDist) { mejorDist = dist; mejorLabel = e.getKey(); }
        }
        if (mejorLabel != -1 && mejorDist < 0.50) {
            int id = labelAPersona.getOrDefault(mejorLabel, -1);
            System.out.printf("✅ ID:%d | Dist:%.4f%n", id, mejorDist);
            return id;
        }
        return -1;
    }

    public static boolean entrenarModelo() {
        if (!opencvCargado && !inicializar()) return false;
        cargarLabels(); cargarMuestrasCache();
        System.out.println("✅ Modelo reentrenado: " + labelAPersona.size() + " personas");
        return !labelAPersona.isEmpty();
    }

    // ════════════════════════════════════════════════════════════════
    //  SINCRONIZACIÓN DESDE SUPABASE
    // ════════════════════════════════════════════════════════════════

    public static boolean sincronizarMuestrasDesdeNube() {
        List<Integer> ids = new java.util.ArrayList<>();
        try (java.sql.Connection con = app.conexion.Conexion.nuevaConexion();
             java.sql.PreparedStatement ps = con.prepareStatement(
                     "SELECT DISTINCT id_usuario FROM muestras_faciales")) {
            java.sql.ResultSet rs = ps.executeQuery();
            while (rs.next()) ids.add(rs.getInt("id_usuario"));
        } catch (Exception e) {
            System.err.println("⚠ sincronizarMuestrasDesdeNube: " + e.getMessage());
        }

        int sincronizados = 0;
        for (int id : ids) {
            boolean ok = SupabaseStorageService.descargarMuestrasFaciales(id);
            if (ok) sincronizados++;
        }
        System.out.println("📥 Sincronización completa: " + sincronizados + "/" + ids.size() + " usuarios");
        if (sincronizados > 0) {
            cargarLabels();
            cargarMuestrasCache();
        }
        return sincronizados > 0;
    }

    // ════════════════════════════════════════════════════════════════
    //  CÁMARA OPENCV COMPARTIDA
    // ════════════════════════════════════════════════════════════════

    private static VideoCapture getCamaraCompartida() {
        if (camaraCompartida != null && camaraCompartida.isOpened()) return camaraCompartida;
        // Intentar primero con el índice elegido por el usuario, luego los demás
        LinkedHashSet<Integer> orden = new LinkedHashSet<>();
        orden.add(indiceCamaraSeleccionada);
        for (int i = 0; i < 4; i++) orden.add(i);
        for (int idx : orden) {
            camaraCompartida = new VideoCapture(idx);
            if (camaraCompartida.isOpened()) {
                sleep(300);
                System.out.println("📷 OpenCV camaraCompartida idx=" + idx);
                break;
            }
            camaraCompartida.release();
        }
        return camaraCompartida;
    }

    public static void liberarCamaraCompartida() {
        if (camaraCompartida != null) {
            try { if (camaraCompartida.isOpened()) camaraCompartida.release(); } catch (Exception ignored) {}
            camaraCompartida = null;
        }
    }

    private static VideoCapture abrirCamaraOpenCV() {
        // Intentar primero el índice seleccionado por el usuario
        LinkedHashSet<Integer> orden = new LinkedHashSet<>();
        orden.add(indiceCamaraSeleccionada);
        for (int i = 0; i < 4; i++) orden.add(i);
        for (int idx : orden) {
            VideoCapture c = new VideoCapture(idx);
            if (c.isOpened()) {
                System.out.println("✅ OpenCV cámara física índice: " + idx);
                sleep(300);
                return c;
            }
            c.release();
        }
        System.err.println("❌ No se encontró cámara física con OpenCV");
        return null;
    }

    // ════════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════════

    private static Mat preprocesar(Mat frame) {
        Mat gris = new Mat(); Imgproc.cvtColor(frame, gris, Imgproc.COLOR_BGR2GRAY); Imgproc.equalizeHist(gris, gris); return gris;
    }

    private static void guardarMuestra(Mat cara, String ruta) {
        Mat r = new Mat(); Imgproc.resize(cara, r, new Size(ANCHO_CARA, ALTO_CARA)); Imgcodecs.imwrite(ruta, r);
    }

    private static Mat bufferedImageToMat(java.awt.image.BufferedImage bi) {
        java.awt.image.BufferedImage conv = new java.awt.image.BufferedImage(bi.getWidth(), bi.getHeight(), java.awt.image.BufferedImage.TYPE_3BYTE_BGR);
        conv.getGraphics().drawImage(bi, 0, 0, null);
        byte[] data = ((java.awt.image.DataBufferByte) conv.getRaster().getDataBuffer()).getData();
        Mat m = new Mat(bi.getHeight(), bi.getWidth(), CvType.CV_8UC3); m.put(0, 0, data); return m;
    }

    private static double compararConMuestras(Mat cara, List<Mat> muestras) {
        if (muestras.isEmpty()) return Double.MAX_VALUE;
        Mat histCara = calcularHistograma(cara); double total = 0; int lim = Math.min(25, muestras.size());
        for (int i = 0; i < lim; i++) total += (1.0 - Imgproc.compareHist(histCara, calcularHistograma(muestras.get(i)), Imgproc.CV_COMP_CORREL));
        return total / lim;
    }

    private static Mat calcularHistograma(Mat img) {
        Mat hist = new Mat();
        Imgproc.calcHist(List.of(img), new MatOfInt(0), new Mat(), hist, new MatOfInt(256), new MatOfFloat(0f, 256f));
        Core.normalize(hist, hist, 0, 1, Core.NORM_MINMAX); return hist;
    }

    private static void cargarMuestrasCache() {
        muestrasCache.clear();
        File raiz = new File(CARPETA_ROSTROS); if (!raiz.exists()) return;
        File[] subdirs = raiz.listFiles(File::isDirectory); if (subdirs == null) return;
        for (File carpeta : subdirs) {
            try {
                int idPersona = Integer.parseInt(carpeta.getName());
                int label = obtenerOCrearLabel(idPersona);
                List<Mat> muestras = new java.util.ArrayList<>();
                File[] archivos = carpeta.listFiles(); if (archivos == null) continue;
                Arrays.sort(archivos); int cargadas = 0;
                for (File img : archivos) {
                    if (!img.getName().endsWith(".jpg") || cargadas >= 40) continue;
                    Mat m = Imgcodecs.imread(img.getAbsolutePath(), Imgcodecs.IMREAD_GRAYSCALE);
                    if (!m.empty()) { Imgproc.resize(m, m, new Size(ANCHO_CARA, ALTO_CARA)); muestras.add(m); cargadas++; }
                }
                if (!muestras.isEmpty()) muestrasCache.put(label, muestras);
            } catch (NumberFormatException ignored) {}
        }
        System.out.println("✅ Caché: " + muestrasCache.size() + " personas");
    }

    private static int obtenerOCrearLabel(int idPersona) {
        for (Map.Entry<Integer,Integer> e : labelAPersona.entrySet()) if (e.getValue()==idPersona) return e.getKey();
        int nuevo = labelAPersona.size(); labelAPersona.put(nuevo, idPersona); return nuevo;
    }

    private static void guardarLabels() {
        try {
            StringBuilder sb = new StringBuilder();
            labelAPersona.forEach((k,v) -> sb.append(k).append("=").append(v).append("\n"));
            Files.writeString(Paths.get(LABELS_FILE), sb.toString());
        } catch (Exception e) { System.err.println("⚠ guardarLabels: " + e.getMessage()); }
    }

    private static void cargarLabels() {
        labelAPersona.clear();
        try {
            File f = new File(LABELS_FILE); if (!f.exists()) return;
            for (String linea : Files.readAllLines(f.toPath())) {
                String[] p = linea.split("=");
                if (p.length == 2) labelAPersona.put(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()));
            }
        } catch (Exception e) { System.err.println("⚠ cargarLabels: " + e.getMessage()); }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public static boolean isDisponible() { return opencvCargado; }

    public static boolean isEntrenado() {
        if (!opencvCargado && !inicializar()) return false;
        if (!muestrasCache.isEmpty()) return true;
        File raiz = new File(CARPETA_ROSTROS); if (!raiz.exists()) return false;
        File[] carpetas = raiz.listFiles(File::isDirectory);
        if (carpetas == null || carpetas.length == 0) return false;
        for (File c : carpetas) {
            File[] imgs = c.listFiles(f -> f.getName().endsWith(".jpg"));
            if (imgs != null && imgs.length > 0) { cargarLabels(); cargarMuestrasCache(); return !muestrasCache.isEmpty(); }
        }
        return false;
    }

    public static int getTotalPersonasEnroladas() { return labelAPersona.size(); }
}