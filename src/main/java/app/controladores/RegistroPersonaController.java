package app.controladores;

import app.dao.AuditoriaDAO;
import app.dao.UsuarioDAO;
import app.modelos.Usuario;
import app.servicios.CamaraService;
import app.servicios.EmailService;
import app.servicios.FirmaService;
import app.servicios.PDFService;
import app.servicios.QRService;
import app.servicios.ReconocimientoFacialService;
import app.servicios.SupabaseStorageService;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controlador — Registro de Personas (BiometricUMG 4.0)
 *
 * CAMBIOS vs versión anterior:
 *  - capturarFoto()     → ahora sube a Supabase Storage via CamaraService
 *  - cargarFotoArchivo()→ sube la foto seleccionada a Supabase Storage
 *  - rutaFotoCapturada  → puede ser URL pública (https://...) o ruta local
 *  - Al guardar usuario, la URL de Supabase queda en usuarios.foto
 */
public class RegistroPersonaController implements Initializable, Liberable {

    // ─── Cámara ───
    @FXML private ImageView        imgPreview;
    @FXML private ImageView        imgFotoCapturada;
    @FXML private Label            lblCamaraEstado;
    @FXML private Button           btnActivarCamara;
    @FXML private Button           btnCapturar;
    @FXML private Button           btnEnrolar;
    @FXML private VBox             panelFotoCapturada;
    @FXML private ProgressBar      progEnrolamiento;
    @FXML private Label            lblEstadoEnrolamiento;
    @FXML private javafx.scene.canvas.Canvas canvasGuia;
    @FXML private Label            lblGuiaEnrolamiento;

    // ─── Selector de cámara ───
    @FXML private ComboBox<String> cmbCamara;
    @FXML private Label            lblCamaraUtilizar;

    private AnimationTimer timerGuia  = null;
    private double         guiaPhase  = 0.0;

    // ─── Datos personales ───
    @FXML private TextField        txtNombre;
    @FXML private TextField        txtApellido;
    @FXML private TextField        txtTelefono;
    @FXML private TextField        txtCorreo;
    @FXML private ComboBox<String> cmbTipoPersona;
    @FXML private Label            lblRolSistema;
    @FXML private ComboBox<String> cmbRol;

    // ─── Datos académicos (solo Estudiante) ───
    @FXML private VBox             panelAcademico;
    @FXML private TextField        txtCarne;
    @FXML private ComboBox<String> cmbCarrera;

    // ─── Administrativo ───
    @FXML private VBox             panelAdministrativo;
    @FXML private ComboBox<String> cmbCargoAdmin;
    @FXML private VBox             panelCorreoEncargado;
    @FXML private TextField        txtCorreoEncargado;
    @FXML private VBox             panelAvisoSubencargado;

    // ─── Credenciales ───
    @FXML private TextField        txtUsuarioSistema;
    @FXML private PasswordField    txtPasswordInicial;

    // ─── Resultado ───
    @FXML private VBox   panelMensaje;
    @FXML private Label  lblMensaje;
    @FXML private VBox   panelResultado;
    @FXML private Button btnGuardar;
    @FXML private ImageView imgLogoUMG;

    // ── Estado interno ──
    // Puede ser URL pública de Supabase (https://...) o ruta local (data/fotos/...)
    private String rutaFotoCapturada   = null;
    private int    idUsuarioRegistrado = -1;
    private String rutaCarnetPDF       = null;

    // ══════════════════════════════════════════
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        cmbCarrera.setItems(FXCollections.observableArrayList(
                "Ingeniería en Sistemas de Información y Ciencias de la Computación",
                "Administración de Empresas",
                "Ciencias Jurídicas y Sociales",
                "Contaduría Pública y Auditoría",
                "Psicología Clínica",
                "Psicología Industrial/Organizacional",
                "Trabajo Social"
        ));
        cmbCarrera.setValue("Ingeniería en Sistemas de Información y Ciencias de la Computación");

        cmbTipoPersona.setItems(FXCollections.observableArrayList(
                "Estudiante", "Catedrático", "Seguridad", "Administrativo", "Mantenimiento"
        ));

        cmbRol.setItems(FXCollections.observableArrayList(
                "Administrador", "Catedratico", "Estudiante", "Seguridad", "Mantenimiento", "Administrativo"
        ));
        cmbRol.setVisible(false);
        cmbRol.setManaged(false);

        cmbCargoAdmin.setItems(FXCollections.observableArrayList("Encargado", "SubEncargado"));
        cmbCargoAdmin.setOnAction(e -> ajustarCamposAdministrativo());

        txtCorreo.textProperty().addListener((obs, old, nv) -> {
            if (nv != null && nv.contains("@"))
                txtUsuarioSistema.setText(nv.split("@")[0]);
        });

        cmbTipoPersona.setOnAction(e -> ajustarCamposTipoPersona());

        cargarListaCamaras();
        new Thread(() -> ReconocimientoFacialService.inicializar()).start();

        try {
            imgLogoUMG.setImage(new Image(getClass().getResourceAsStream("/images/logo_umg.png")));
        } catch (Exception ignored) {}
    }

    private void cargarListaCamaras() {
        new Thread(() -> {
            List<String> camaras = CamaraService.listarCamaras();
            Platform.runLater(() -> {
                if (cmbCamara != null) {
                    cmbCamara.getItems().setAll(camaras);
                    if (!camaras.isEmpty()) cmbCamara.setValue(camaras.get(0));
                }
            });
        }, "listar-camaras-registro") {{ setDaemon(true); }}.start();
    }

    @Override
    public void liberarRecursos() {
        ReconocimientoFacialService.cancelarEnrolamiento();
        detenerGuiaFacial();
        CamaraService.detener();
    }

    // ══════════════════════════════════════════
    //  GUARDAR — proceso principal
    // ══════════════════════════════════════════
    @FXML
    public void guardarPersona() {
        if (!validarFormulario()) return;

        btnGuardar.setDisable(true);
        btnGuardar.setText("⏳ Registrando...");

        new Thread(() -> {
            try {
                Usuario u = new Usuario();
                u.setNombre      (txtNombre.getText().trim());
                u.setApellido    (txtApellido.getText().trim());
                u.setTelefono    (txtTelefono.getText().trim());
                u.setCorreo      (txtCorreo.getText().trim());
                u.setTipoPersona (cmbTipoPersona.getValue());

                // ── La foto ya es URL pública o ruta local según lo que resolvió ──
                // capturarFoto() / cargarFotoArchivo()
                u.setFoto(rutaFotoCapturada);

                u.setIdRol  (obtenerIdRol(cmbRol.getValue()));
                u.setUsuario(txtUsuarioSistema.getText().trim());

                if ("Estudiante".equals(cmbTipoPersona.getValue())) {
                    u.setCarrera(cmbCarrera.getValue() != null ? cmbCarrera.getValue().trim() : "");
                    u.setCarne  (txtCarne.getText().trim());
                    u.setSeccion("");
                }

                String password = txtPasswordInicial.getText();
                int idGenerado  = UsuarioDAO.insertarConPassword(u, password);

                if (idGenerado == -1) {
                    Platform.runLater(() -> {
                        mostrarMensaje("❌ Error al guardar. Verifica que la contraseña tenga 8+ caracteres, una mayúscula y un número.", false);
                        btnGuardar.setDisable(false);
                        btnGuardar.setText("💾 Guardar Registro");
                    });
                    return;
                }

                u.setIdUsuario(idGenerado);
                idUsuarioRegistrado = idGenerado;

                FirmaService.inicializar();
                String firmaCompleta = FirmaService.generarFirmaCompleta(
                        idGenerado, u.getNombreCompleto(), u.getCarne(), u.getCorreo());
                QRService.generarPaginaVerificacion(u, firmaCompleta);
                String codigoQR = QRService.generarCodigoQR(idGenerado, u.getNombreCompleto(), u.getCarne());
                UsuarioDAO.actualizarQR(idGenerado, codigoQR);

                rutaCarnetPDF = PDFService.generarCarnet(u);

                if ("Mantenimiento".equals(u.getTipoPersona())
                        && "SubEncargado".equals(cmbCargoAdmin.getValue())) {
                    String correoEnc = txtCorreoEncargado.getText().trim();
                    if (!correoEnc.isEmpty())
                        registrarRelacionSubencargado(idGenerado, u.getNombreCompleto(), correoEnc);
                }

                AuditoriaDAO.registrarAccion(
                        "Nuevo usuario registrado: " + u.getNombreCompleto()
                                + " [" + u.getTipoPersona() + "]", "REGISTRO");

                Platform.runLater(() -> {
                    btnEnrolar.setDisable(false);
                    mostrarResultadoExitoso();
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    mostrarMensaje("❌ Error: " + e.getMessage(), false);
                    btnGuardar.setDisable(false);
                    btnGuardar.setText("💾 Guardar Registro");
                });
                e.printStackTrace();
            }
        }, "bg-registro") {{ setDaemon(true); }}.start();
    }

    // ══════════════════════════════════════════
    //  Cámara
    // ══════════════════════════════════════════
    @FXML public void activarCamara() {
        btnActivarCamara.setDisable(true);
        lblCamaraEstado.setText("🔄 Iniciando cámara...");

        int idxCamara = (cmbCamara != null)
                ? cmbCamara.getSelectionModel().getSelectedIndex()
                : -1;
        final int idxFinal = idxCamara;

        new Thread(() -> {
            if (idxFinal >= 0) ReconocimientoFacialService.setIndiceCamara(idxFinal);
            boolean ok = CamaraService.iniciarPreviewLocal(imgPreview, idxFinal);
            Platform.runLater(() -> {
                if (ok) {
                    lblCamaraEstado.setVisible(false);
                    btnCapturar.setDisable(false);
                    btnActivarCamara.setText("⏹ Detener Cámara");
                    btnActivarCamara.setDisable(false);
                    btnActivarCamara.setOnAction(e -> detenerCamara());
                    if (cmbCamara != null) cmbCamara.setDisable(true);
                    iniciarGuiaFacial();
                } else {
                    lblCamaraEstado.setText("❌ Cámara no disponible");
                    btnActivarCamara.setDisable(false);
                    mostrarMensaje("No se pudo iniciar la cámara.", false);
                }
            });
        }, "cam-thread") {{ setDaemon(true); }}.start();
    }

    private void detenerCamara() {
        ReconocimientoFacialService.cancelarEnrolamiento();
        detenerGuiaFacial();
        CamaraService.detener();
        btnActivarCamara.setText("▶ Activar Cámara");
        btnActivarCamara.setOnAction(e -> activarCamara());
        btnCapturar.setDisable(true);
        if (cmbCamara != null) cmbCamara.setDisable(false);
        lblCamaraEstado.setVisible(true);
        lblCamaraEstado.setText("📷 Sin cámara activa");
    }

    // ══════════════════════════════════════════
    //  CAPTURAR FOTO — sube a Supabase Storage
    // ══════════════════════════════════════════
    @FXML public void capturarFoto() {
        String nombre = txtNombre.getText().trim().isEmpty()
                ? "persona" : (txtNombre.getText().trim() + "_" + txtApellido.getText().trim());

        mostrarMensaje("📤 Capturando y subiendo foto...", true);
        btnCapturar.setDisable(true);

        new Thread(() -> {
            // CamaraService.capturarFoto() ahora sube a Supabase y retorna URL pública
            String resultado = CamaraService.capturarFoto(nombre);

            Platform.runLater(() -> {
                btnCapturar.setDisable(false);
                if (resultado != null) {
                    rutaFotoCapturada = resultado;
                    mostrarPreviewFoto(resultado);
                    String destino = resultado.startsWith("http") ? "Supabase Storage ☁️" : "disco local";
                    mostrarMensaje("✅ Foto guardada en " + destino, true);
                } else {
                    mostrarMensaje("❌ No se pudo capturar la foto", false);
                }
            });
        }, "captura-foto-thread") {{ setDaemon(true); }}.start();
    }

    // ══════════════════════════════════════════
    //  CARGAR FOTO DESDE ARCHIVO — sube a Supabase
    // ══════════════════════════════════════════
    @FXML public void cargarFotoArchivo() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar Fotografía");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Imágenes", "*.jpg", "*.jpeg", "*.png"));
        File archivo = chooser.showOpenDialog(null);
        if (archivo == null) return;

        mostrarMensaje("📤 Subiendo foto a Supabase...", true);

        String nombrePersona = (txtNombre.getText().trim() + "_" + txtApellido.getText().trim()).trim();
        if (nombrePersona.isBlank()) nombrePersona = archivo.getName().replaceAll("\\.[^.]+$", "");
        final String nombre = nombrePersona;

        // Mostrar preview de inmediato (antes de subir)
        try {
            imgFotoCapturada.setImage(new Image(archivo.toURI().toString()));
            panelFotoCapturada.setVisible(true);
            panelFotoCapturada.setManaged(true);
            btnEnrolar.setDisable(false);
        } catch (Exception ignored) {}

        final String rutaLocal = archivo.getAbsolutePath();
        new Thread(() -> {
            // Subir a Supabase y obtener URL pública
            String urlFinal = SupabaseStorageService.subirFotoDesdeArchivo(rutaLocal, nombre);
            Platform.runLater(() -> {
                rutaFotoCapturada = urlFinal;
                String destino = urlFinal.startsWith("http") ? "Supabase Storage ☁️" : "disco local";
                mostrarMensaje("✅ Imagen guardada en " + destino + ": " + archivo.getName(), true);
            });
        }, "upload-foto-archivo") {{ setDaemon(true); }}.start();
    }

    /** Muestra la foto en el panel, aceptando URL http o ruta local */
    private void mostrarPreviewFoto(String urlOrPath) {
        try {
            Image img;
            if (urlOrPath.startsWith("http")) {
                img = new Image(urlOrPath, true); // carga async desde URL
            } else {
                img = new Image(new File(urlOrPath).toURI().toString());
            }
            imgFotoCapturada.setImage(img);
            panelFotoCapturada.setVisible(true);
            panelFotoCapturada.setManaged(true);
            btnEnrolar.setDisable(false);
        } catch (Exception e) {
            mostrarMensaje("⚠ Foto guardada pero no se pudo mostrar preview", true);
        }
    }

    // ══════════════════════════════════════════
    //  ENROLAMIENTO FACIAL
    // ══════════════════════════════════════════
    @FXML public void enrolarRostro() {
        if (idUsuarioRegistrado == -1) {
            mostrarMensaje("⚠ Primero registra al usuario para enrolar su rostro", false);
            return;
        }
        ReconocimientoFacialService.resetCancelacion();
        btnEnrolar.setDisable(true);
        progEnrolamiento.setVisible(true);
        progEnrolamiento.setProgress(0);

        String[][] fases = {
                {"😐  Mira al frente",                "30"},
                {"👈  Gira levemente a la izquierda", "20"},
                {"👉  Gira levemente a la derecha",   "20"},
                {"🙂  Inclina levemente la cabeza",   "10"}
        };
        int totalMuestras = 80;

        new Thread(() -> {
            int acumuladas = 0;
            for (int f = 0; f < fases.length; f++) {
                final String instruccion  = fases[f][0];
                final int    muestrasFase = Integer.parseInt(fases[f][1]);
                final int    faseNum      = f + 1;
                final int    acum         = acumuladas;

                for (int c = 3; c >= 1; c--) {
                    final int cc = c;
                    Platform.runLater(() -> lblEstadoEnrolamiento.setText(instruccion + "\n⏳ Preparate... " + cc));
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                }
                Platform.runLater(() -> lblEstadoEnrolamiento.setText("📡 Fase " + faseNum + "/4 — " + instruccion));

                // enrolarRostroDesdeOffset ahora también sube cada muestra a Supabase
                int cap = ReconocimientoFacialService.enrolarRostroDesdeOffset(
                        idUsuarioRegistrado, muestrasFase, acum);
                acumuladas += cap;
                final int total = acumuladas;
                Platform.runLater(() -> {
                    progEnrolamiento.setProgress((double) total / totalMuestras);
                    lblEstadoEnrolamiento.setText("✅ Fase " + faseNum + " — " + total + " muestras");
                });
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }

            ReconocimientoFacialService.entrenarModelo();
            ReconocimientoFacialService.inicializar();

            final int totalFinal = acumuladas;
            Platform.runLater(() -> {
                progEnrolamiento.setProgress(1.0);
                if (lblGuiaEnrolamiento != null) lblGuiaEnrolamiento.setVisible(false);
                if (totalFinal > 0) {
                    lblEstadoEnrolamiento.setText("🎉 " + totalFinal + " muestras en Supabase. ¡Modelo listo!");
                    lblEstadoEnrolamiento.setStyle("-fx-text-fill:#27ae60;-fx-font-size:11px;");
                } else {
                    lblEstadoEnrolamiento.setText("❌ No se capturaron muestras. Verifica la cámara.");
                    lblEstadoEnrolamiento.setStyle("-fx-text-fill:#e74c3c;-fx-font-size:11px;");
                    btnEnrolar.setDisable(false);
                }
            });
        }, "enrolamiento-thread") {{ setDaemon(true); }}.start();
    }

    // ══════════════════════════════════════════
    //  Post-registro
    // ══════════════════════════════════════════
    private void mostrarResultadoExitoso() {
        panelResultado.setVisible(true);
        panelResultado.setManaged(true);
        btnGuardar.setDisable(false);
        btnGuardar.setText("💾 Guardar Registro");
        mostrarMensaje("✅ Usuario registrado. ID: " + idUsuarioRegistrado + " | Carnet generado.", true);
    }

    @FXML public void verCarnet() {
        if (rutaCarnetPDF != null && new File(rutaCarnetPDF).exists()) {
            try { Desktop.getDesktop().open(new File(rutaCarnetPDF)); }
            catch (Exception e) { mostrarMensaje("⚠ No se pudo abrir el PDF: " + e.getMessage(), false); }
        } else {
            mostrarMensaje("⚠ Carnet PDF no disponible", false);
        }
    }

    @FXML public void enviarCarnetCorreo() {
        String correo = txtCorreo.getText().trim();
        String nombre = txtNombre.getText().trim() + " " + txtApellido.getText().trim();
        if (correo.isEmpty()) { mostrarMensaje("⚠ No hay correo registrado", false); return; }
        new Thread(() -> {
            boolean ok = EmailService.enviarCarnet(correo, nombre, rutaCarnetPDF);
            Platform.runLater(() -> mostrarMensaje(ok
                    ? "✅ Carnet enviado a " + correo
                    : "❌ No se pudo enviar el correo.", ok));
        }, "email-thread") {{ setDaemon(true); }}.start();
    }

    @FXML public void enviarWhatsApp() {
        String telefono = txtTelefono.getText().trim();
        String nombre   = txtNombre.getText().trim() + " " + txtApellido.getText().trim();
        if (telefono.isEmpty()) { mostrarMensaje("⚠ No hay número de teléfono", false); return; }
        if (idUsuarioRegistrado == -1) { mostrarMensaje("⚠ Primero registra al usuario", false); return; }
        try {
            String tel = telefono.replaceAll("[^0-9]", "");
            if (tel.length() == 8) tel = "502" + tel;
            String msg = "🎓 *Universidad Mariano Gálvez*\nSede La Florida, Zona 19\n\n"
                    + "Hola *" + nombre.trim() + "*,\n"
                    + "Tu registro en BiometricUMG 4.0 fue completado.\n\n"
                    + "✅ Datos registrados\n✅ Foto subida a Supabase ☁️\n✅ QR generado\n✅ Carnet generado\n\n"
                    + "_BiometricUMG 4.0 — 2026_";
            String url = "https://wa.me/" + tel + "?text=" + java.net.URLEncoder.encode(msg, "UTF-8");
            java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            mostrarMensaje("✅ WhatsApp Web abierto", true);
        } catch (Exception e) {
            mostrarMensaje("❌ Error: " + e.getMessage(), false);
        }
    }

    // ══════════════════════════════════════════
    //  Validaciones
    // ══════════════════════════════════════════
    private boolean validarFormulario() {
        if (txtNombre.getText().trim().isEmpty()) {
            mostrarMensaje("⚠ El nombre es obligatorio", false); txtNombre.requestFocus(); return false; }
        if (txtApellido.getText().trim().isEmpty()) {
            mostrarMensaje("⚠ El apellido es obligatorio", false); txtApellido.requestFocus(); return false; }
        String correo = txtCorreo.getText().trim();
        if (correo.isEmpty()) {
            mostrarMensaje("⚠ El correo es obligatorio", false); txtCorreo.requestFocus(); return false; }
        if (!correo.matches("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$")) {
            mostrarMensaje("⚠ Formato de correo inválido", false); txtCorreo.requestFocus(); return false; }
        if (!correo.endsWith("@miumg.edu.gt") && !correo.endsWith("@umg.edu.gt")) {
            mostrarMensaje("⚠ Debe ser correo institucional UMG", false); txtCorreo.requestFocus(); return false; }
        if (UsuarioDAO.correoExiste(correo)) {
            mostrarMensaje("⚠ Este correo ya está registrado", false); return false; }
        if (cmbTipoPersona.getValue() == null) {
            mostrarMensaje("⚠ Selecciona el tipo de persona", false); return false; }
        if ("Estudiante".equals(cmbTipoPersona.getValue())) {
            if (txtCarne.getText().trim().isEmpty()) {
                mostrarMensaje("⚠ El carné es obligatorio", false); txtCarne.requestFocus(); return false; }
            if (cmbCarrera.getValue() == null) {
                mostrarMensaje("⚠ Selecciona la carrera", false); return false; }
        }
        if ("Mantenimiento".equals(cmbTipoPersona.getValue())) {
            if (cmbCargoAdmin.getValue() == null) {
                mostrarMensaje("⚠ Selecciona el cargo de mantenimiento", false); return false; }
            if ("SubEncargado".equals(cmbCargoAdmin.getValue())) {
                String ce = txtCorreoEncargado.getText().trim();
                if (ce.isEmpty()) {
                    mostrarMensaje("⚠ Ingresa el correo del Encargado", false);
                    txtCorreoEncargado.requestFocus(); return false; }
                if (!ce.endsWith("@miumg.edu.gt") && !ce.endsWith("@umg.edu.gt")) {
                    mostrarMensaje("⚠ El correo del encargado debe ser institucional UMG", false);
                    txtCorreoEncargado.requestFocus(); return false; }
            }
        }
        if (rutaFotoCapturada == null) {
            mostrarMensaje("⚠ Captura o carga una fotografía", false); return false; }
        return true;
    }

    // ══════════════════════════════════════════
    //  Ajuste dinámico de campos
    // ══════════════════════════════════════════
    private void ajustarCamposTipoPersona() {
        String tipo = cmbTipoPersona.getValue();
        if (tipo == null) return;
        String rol = switch (tipo) {
            case "Estudiante"     -> "Estudiante";
            case "Catedrático"    -> "Catedratico";
            case "Seguridad"      -> "Seguridad";
            case "Mantenimiento"  -> "Mantenimiento";
            case "Administrativo" -> "Administrativo";
            default               -> "Estudiante";
        };
        cmbRol.setValue(rol);
        lblRolSistema.setText(tipo);
        boolean esEst = "Estudiante".equals(tipo);
        boolean esMant = "Mantenimiento".equals(tipo);
        panelAcademico.setVisible(esEst);       panelAcademico.setManaged(esEst);
        panelAdministrativo.setVisible(esMant); panelAdministrativo.setManaged(esMant);
        if (!esMant) { cmbCargoAdmin.setValue(null); ocultarPanelSubencargado(); }
    }

    private void ajustarCamposAdministrativo() {
        String cargo = cmbCargoAdmin.getValue();
        boolean esSub = "SubEncargado".equals(cargo);
        panelCorreoEncargado.setVisible(esSub);   panelCorreoEncargado.setManaged(esSub);
        panelAvisoSubencargado.setVisible(esSub); panelAvisoSubencargado.setManaged(esSub);
        if (!esSub) txtCorreoEncargado.clear();
    }

    private void ocultarPanelSubencargado() {
        panelCorreoEncargado.setVisible(false);   panelCorreoEncargado.setManaged(false);
        panelAvisoSubencargado.setVisible(false); panelAvisoSubencargado.setManaged(false);
        txtCorreoEncargado.clear();
    }

    // ══════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════
    private int obtenerIdRol(String nombreRol) {
        if (nombreRol == null) return 3;
        return switch (nombreRol) {
            case "Administrador"  -> 1;
            case "Catedratico"    -> 2;
            case "Estudiante"     -> 3;
            case "Seguridad"      -> 4;
            case "Mantenimiento"  -> 5;
            case "Administrativo" -> 6;
            default               -> 3;
        };
    }

    private void registrarRelacionSubencargado(int idSub, String nombreSub, String correoEnc) {
        try (var con = app.conexion.Conexion.nuevaConexion()) {
            if (con != null) {
                var ps = con.prepareStatement(
                        // FIX #6: columnas en snake_case igual que el CREATE TABLE de auditlog
                        "INSERT INTO auditlog (tabla,operacion,id_registro,campo_modificado," +
                                "valor_anterior,valor_nuevo,descripcion,modulo) VALUES " +
                                "('usuarios','INSERT',?,'correo_encargado',NULL,?,?,'Sistema')");
                ps.setInt   (1, idSub);
                ps.setString(2, correoEnc);
                ps.setString(3, "SubEncargado " + nombreSub + " asignado al encargado " + correoEnc);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("⚠ No se pudo registrar relación subencargado: " + e.getMessage());
        }
        new Thread(() -> {
            String asunto = "👤 Nuevo SubEncargado asignado — BiometricUMG";
            String cuerpo = "<html><body>Se le asignó el SubEncargado: <b>" + nombreSub + "</b></body></html>";
            EmailService.enviarCorreoGenerico(correoEnc, asunto, cuerpo);
        }, "notif-enc") {{ setDaemon(true); }}.start();
    }

    private void mostrarMensaje(String msg, boolean exito) {
        lblMensaje.setText(msg);
        lblMensaje.setStyle(exito
                ? "-fx-text-fill:#27ae60;-fx-font-size:12px;"
                : "-fx-text-fill:#e74c3c;-fx-font-size:12px;");
        panelMensaje.setVisible(true);
        panelMensaje.setManaged(true);
    }

    @FXML public void limpiarFormulario() {
        txtNombre.clear(); txtApellido.clear(); txtTelefono.clear(); txtCorreo.clear();
        txtCarne.clear(); txtUsuarioSistema.clear(); txtPasswordInicial.clear();
        cmbCarrera.setValue("Ingeniería en Sistemas de Información y Ciencias de la Computación");
        cmbTipoPersona.setValue(null); cmbRol.setValue(null); cmbCargoAdmin.setValue(null);
        txtCorreoEncargado.clear(); lblRolSistema.setText("—");
        panelAcademico.setVisible(false);      panelAcademico.setManaged(false);
        panelAdministrativo.setVisible(false); panelAdministrativo.setManaged(false);
        ocultarPanelSubencargado();
        rutaFotoCapturada   = null;
        idUsuarioRegistrado = -1;
        rutaCarnetPDF       = null;
        panelFotoCapturada.setVisible(false); panelFotoCapturada.setManaged(false);
        panelMensaje.setVisible(false);       panelMensaje.setManaged(false);
        panelResultado.setVisible(false);     panelResultado.setManaged(false);
        progEnrolamiento.setVisible(false);
        lblEstadoEnrolamiento.setText("");
        btnEnrolar.setDisable(true);
        detenerCamara();
    }

    // ── Guía facial animada ──
    private void iniciarGuiaFacial() {
        if (canvasGuia == null) return;
        canvasGuia.setVisible(true);
        timerGuia = new AnimationTimer() {
            @Override public void handle(long now) { guiaPhase += 0.04; dibujarGuia(guiaPhase); }
        };
        timerGuia.start();
    }

    private void detenerGuiaFacial() {
        if (timerGuia != null) { timerGuia.stop(); timerGuia = null; }
        if (canvasGuia != null) {
            canvasGuia.getGraphicsContext2D().clearRect(0, 0, canvasGuia.getWidth(), canvasGuia.getHeight());
            canvasGuia.setVisible(false);
        }
        if (lblGuiaEnrolamiento != null) lblGuiaEnrolamiento.setVisible(false);
    }

    private void dibujarGuia(double phase) {
        double w = canvasGuia.getWidth(), h = canvasGuia.getHeight();
        GraphicsContext gc = canvasGuia.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);
        double cx = w/2, cy = h/2-10, rx = 72, ry = 90;
        double pulse = 1.0 + 0.015*Math.sin(phase);
        double erx = rx*pulse, ery = ry*pulse;
        gc.setStroke(Color.web("#00d4ff", 0.85)); gc.setLineWidth(2.2);
        gc.strokeOval(cx-erx, cy-ery, erx*2, ery*2);
        double scanY = cy-ery+(ery*2)*((Math.sin(phase*0.7)+1)/2);
        double halfW = erx*Math.sqrt(Math.max(0,1-Math.pow((scanY-cy)/ery,2)));
        if (halfW > 2) {
            gc.setStroke(Color.web("#00d4ff", 0.55+0.35*Math.abs(Math.sin(phase*0.7))));
            gc.setLineWidth(1.5); gc.strokeLine(cx-halfW, scanY, cx+halfW, scanY);
        }
        double m=14, len=22, x1=cx-erx-m, y1=cy-ery-m*0.6, x2=cx+erx+m, y2=cy+ery+m*0.6;
        gc.setStroke(Color.web("#00d4ff", 0.9)); gc.setLineWidth(2.5);
        gc.strokeLine(x1,y1,x1+len,y1); gc.strokeLine(x1,y1,x1,y1+len);
        gc.strokeLine(x2,y1,x2-len,y1); gc.strokeLine(x2,y1,x2,y1+len);
        gc.strokeLine(x1,y2,x1+len,y2); gc.strokeLine(x1,y2,x1,y2-len);
        gc.strokeLine(x2,y2,x2-len,y2); gc.strokeLine(x2,y2,x2,y2-len);
    }
}