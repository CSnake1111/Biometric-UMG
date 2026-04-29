package app.controladores;

import app.conexion.Conexion;
import app.dao.CursoDAO;
import app.dao.RegistroIngresoDAO;
import app.dao.UsuarioDAO;
import app.modelos.Curso;
import app.modelos.Instalacion;
import app.modelos.Salon;
import app.modelos.Usuario;
import app.servicios.CamaraService;
import app.servicios.EmailService;
import app.servicios.ReconocimientoFacialService;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.io.File;
import java.net.URL;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class IngresoSalonController implements Initializable, Liberable {

    // ── Cámara & estado ────────────────────────────────────────────
    @FXML private ImageView        imgCamara;
    @FXML private Circle           indicadorEstado;
    @FXML private Label            lblEstadoCamara;
    @FXML private Button           btnIniciarCamara;
    @FXML private Button           btnDetenerCamara;
    @FXML private VBox             panelSinCamara;
    @FXML private ComboBox<String> cmbCamara;
    @FXML private Label            lblCamaraUtilizar;

    // ── Selección de ubicación ─────────────────────────────────────
    @FXML private ComboBox<Instalacion> cmbInstalacion;
    @FXML private ComboBox<String>      cmbNivel;
    @FXML private ComboBox<Salon>       cmbSalon;

    // ── Paneles de estado biométrico ───────────────────────────────
    @FXML private VBox panelEsperando;
    @FXML private VBox panelReconociendo;
    @FXML private VBox panelIdentificado;
    @FXML private VBox panelNoReconocido;

    @FXML private ImageView imgPersonaId;
    @FXML private Label     lblNombreId;
    @FXML private Label     lblTipoId;
    @FXML private Label     lblHoraIngreso;

    // ── Panel asistencia manual ────────────────────────────────────
    @FXML private VBox                  panelAsistenciaManual;
    @FXML private ComboBox<Usuario>     cmbEstudianteManual;
    @FXML private Label                 lblMensajeManual;

    // ── Tabla de ingresos ──────────────────────────────────────────
    @FXML private TableView<String[]>           tablaIngresos;
    @FXML private TableColumn<String[], String> colNombreSalon;
    @FXML private TableColumn<String[], String> colTipoSalon;
    @FXML private TableColumn<String[], String> colHoraSalon;
    @FXML private TableColumn<String[], String> colModoSalon;   // Biométrico / Manual
    @FXML private Label                         lblContadorSalon;

    // ── Estado interno ─────────────────────────────────────────────
    private final ObservableList<String[]> listaIngresos = FXCollections.observableArrayList();
    private ScheduledExecutorService       scheduler;
    private boolean                        activo  = false;
    private final AtomicBoolean            enPausa = new AtomicBoolean(false);

    /** Contador de fallos biométricos consecutivos en esta sesión.
     *  Al superar el umbral se muestra el panel manual automáticamente. */
    private int  fallosConsecutivos = 0;
    private static final int  UMBRAL_FALLOS           = 3;
    // Cooldown por persona: evita registrar a la misma persona dos veces seguidas
    private int  ultimoIdReconocido  = -1;
    private long tiempoUltimoIngreso = 0;
    private static final long COOLDOWN_PERSONA_MS = 10_000;

    /** Evita enviar el aviso al admin más de una vez por sesión de curso. */
    private boolean avisoAdminEnviado = false;

    private static final long PAUSA_TRAS_IDENTIFICACION_MS = 4_000;
    private static final DateTimeFormatter FMT_HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ══════════════════════════════════════════════════════════════
    //  INICIALIZACIÓN
    // ══════════════════════════════════════════════════════════════

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        colNombreSalon.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[0]));
        colTipoSalon  .setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[1]));
        colHoraSalon  .setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[2]));
        colModoSalon  .setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[3]));
        tablaIngresos.setItems(listaIngresos);

        // Mostrar nombre completo en el ComboBox de estudiante manual
        cmbEstudianteManual.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Usuario u, boolean empty) {
                super.updateItem(u, empty);
                setText((empty || u == null) ? null : u.getNombreCompleto() + "  [" + u.getCarne() + "]");
            }
        });
        cmbEstudianteManual.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Usuario u, boolean empty) {
                super.updateItem(u, empty);
                setText((empty || u == null) ? null : u.getNombreCompleto() + "  [" + u.getCarne() + "]");
            }
        });

        cargarInstalaciones();
        cargarListaCamaras();
        ReconocimientoFacialService.inicializar();
    }

    // ══════════════════════════════════════════════════════════════
    //  CARGA DE COMBOS DE UBICACIÓN
    // ══════════════════════════════════════════════════════════════

    private void cargarListaCamaras() {
        new Thread(() -> {
            List<String> camaras = CamaraService.listarCamaras();
            Platform.runLater(() -> {
                if (cmbCamara != null) {
                    cmbCamara.getItems().setAll(camaras);
                    if (!camaras.isEmpty()) cmbCamara.setValue(camaras.get(0));
                }
            });
        }, "listar-camaras-salon") {{ setDaemon(true); }}.start();
    }

    private void cargarInstalaciones() {
        List<Instalacion> lista = new ArrayList<>();
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM instalaciones");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next())
                lista.add(new Instalacion(rs.getInt("id_instalacion"), rs.getString("nombre")));
        } catch (SQLException e) { System.err.println("⚠ " + e.getMessage()); }
        cmbInstalacion.setItems(FXCollections.observableArrayList(lista));
        cmbInstalacion.setOnAction(e -> cargarNiveles());
    }

    @FXML public void cargarNiveles() {
        Instalacion inst = cmbInstalacion.getValue();
        if (inst == null) return;
        List<String> niveles = new ArrayList<>();
        // FIX: SELECT DISTINCT nivel con ORDER BY nivel — nivel está en SELECT, no hay conflicto
        String sql = "SELECT DISTINCT nivel FROM salones WHERE id_instalacion = ? ORDER BY nivel";
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, inst.getIdInstalacion());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) niveles.add(rs.getString("nivel"));
        } catch (SQLException e) { System.err.println("⚠ " + e.getMessage()); }
        cmbNivel.setItems(FXCollections.observableArrayList(niveles));
        cmbNivel.setOnAction(e -> cargarSalones());
    }

    @FXML public void cargarSalones() {
        Instalacion inst  = cmbInstalacion.getValue();
        String      nivel = cmbNivel.getValue();
        if (inst == null || nivel == null) return;
        List<Salon> salones = new ArrayList<>();
        String sql = "SELECT * FROM salones WHERE id_instalacion = ? AND nivel = ? ORDER BY nombre";
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, inst.getIdInstalacion());
            ps.setString(2, nivel);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Salon s = new Salon();
                s.setIdSalon(rs.getInt("id_salon"));
                s.setNombre (rs.getString("nombre"));
                s.setNivel  (rs.getString("nivel"));
                salones.add(s);
            }
        } catch (SQLException e) { System.err.println("⚠ " + e.getMessage()); }
        cmbSalon.setItems(FXCollections.observableArrayList(salones));
        // Recargar estudiantes manuales cuando cambia el salón
        cmbSalon.setOnAction(e -> cargarEstudiantesManual());
    }

    // ══════════════════════════════════════════════════════════════
    //  ASISTENCIA MANUAL
    // ══════════════════════════════════════════════════════════════

    /**
     * Carga en el ComboBox manual los estudiantes inscritos en el curso
     * que corresponde al salón seleccionado (buscado por id_salon en cursos).
     */
    private void cargarEstudiantesManual() {
        Salon salon = cmbSalon.getValue();
        if (salon == null) return;
        new Thread(() -> {
            // Buscar el curso asignado a este salón
            List<Usuario> estudiantes = new ArrayList<>();
            String sql = """
                SELECT u.*, r.nombre_rol
                FROM usuarios u
                JOIN inscripciones_curso ic ON ic.id_estudiante = u.id_usuario
                JOIN cursos c ON c.id_curso = ic.id_curso
                LEFT JOIN roles r ON u.id_rol = r.id_rol
                WHERE c.id_salon = ? AND ic.activo = TRUE AND u.estado = TRUE
                ORDER BY u.apellido, u.nombre
                """;
            try (Connection con = Conexion.nuevaConexion();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, salon.getIdSalon());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Usuario u = new Usuario();
                    u.setIdPersona(rs.getInt("id_usuario"));
                    u.setNombre   (rs.getString("nombre"));
                    u.setApellido (rs.getString("apellido"));
                    u.setCarne    (rs.getString("carne"));
                    u.setCorreo   (rs.getString("correo"));
                    u.setFoto     (rs.getString("foto"));
                    u.setNombreRol(rs.getString("nombre_rol"));
                    estudiantes.add(u);
                }
            } catch (SQLException e) {
                System.err.println("⚠ cargarEstudiantesManual: " + e.getMessage());
            }
            Platform.runLater(() -> cmbEstudianteManual.setItems(
                    FXCollections.observableArrayList(estudiantes)));
        }, "carga-est-manual") {{ setDaemon(true); }}.start();
    }

    /**
     * Registra la asistencia de forma manual cuando el biométrico falla.
     * Envía un aviso al administrador para que corrija los datos biométricos.
     */
    @FXML public void registrarAsistenciaManual() {
        Usuario est   = cmbEstudianteManual.getValue();
        Salon   salon = cmbSalon.getValue();
        if (est == null) {
            lblMensajeManual.setText("⚠ Selecciona un estudiante.");
            return;
        }
        if (salon == null) {
            lblMensajeManual.setText("⚠ Selecciona un salón primero.");
            return;
        }

        String hora = LocalDateTime.now().format(FMT_HORA);

        new Thread(() -> {
            // 1. Registrar ingreso al salón
            boolean ok = RegistroIngresoDAO.registrarPorSalon(
                    est.getIdPersona(), salon.getIdSalon());

            // 2. Notificar al administrador (solo una vez por sesión de curso)
            if (!avisoAdminEnviado) {
                String correoAdmin = UsuarioDAO.getCorreoAdmin();
                if (correoAdmin != null) {
                    String asunto = "⚠ Asistencia Manual Registrada — BiometricUMG";
                    String cuerpo = """
                        <html><body style="font-family:Arial,sans-serif;color:#333;max-width:600px;margin:0 auto;">
                        <div style="background:#1a3a5c;padding:20px;text-align:center;">
                          <h2 style="color:white;margin:0;">BiometricUMG — Aviso de Sistema</h2>
                        </div>
                        <div style="padding:24px;background:#f9f9f9;border-left:4px solid #f39c12;">
                          <h3 style="color:#e67e22;">⚠ Asistencia Registrada Manualmente</h3>
                          <p>El sistema biométrico no pudo identificar a un estudiante.
                             Se registró la asistencia de forma manual con los siguientes datos:</p>
                          <table style="width:100%;border-collapse:collapse;margin:12px 0;">
                            <tr style="background:#fff3cd;">
                              <td style="padding:8px;border:1px solid #ddd;font-weight:bold;">Estudiante</td>
                              <td style="padding:8px;border:1px solid #ddd;">""" + est.getNombreCompleto() + """
                            </td></tr>
                            <tr><td style="padding:8px;border:1px solid #ddd;font-weight:bold;">Carné</td>
                              <td style="padding:8px;border:1px solid #ddd;">""" + est.getCarne() + """
                            </td></tr>
                            <tr style="background:#fff3cd;">
                              <td style="padding:8px;border:1px solid #ddd;font-weight:bold;">Salón</td>
                              <td style="padding:8px;border:1px solid #ddd;">""" + salon.getNombre() + """
                            </td></tr>
                            <tr><td style="padding:8px;border:1px solid #ddd;font-weight:bold;">Hora</td>
                              <td style="padding:8px;border:1px solid #ddd;">""" + hora + """
                            </td></tr>
                          </table>
                          <p style="color:#c0392b;font-weight:bold;">
                            Acción requerida: Por favor verifique y actualice los datos
                            biométricos (rostro) de este estudiante en el sistema.
                          </p>
                        </div>
                        <div style="padding:12px;text-align:center;color:#888;font-size:11px;">
                          BiometricUMG — Sistema Automático de Notificaciones
                        </div>
                        </body></html>
                        """;
                    boolean enviado = EmailService.enviarCorreoGenerico(correoAdmin, asunto, cuerpo);
                    avisoAdminEnviado = enviado;
                    System.out.println(enviado
                            ? "✅ Aviso biométrico enviado al admin: " + correoAdmin
                            : "⚠ No se pudo enviar aviso al admin");
                }
            }

            Platform.runLater(() -> {
                if (ok) {
                    // Agregar a la tabla con marca "Manual"
                    listaIngresos.add(0, new String[]{
                            est.getNombreCompleto(),
                            est.getTipoPersona() != null ? est.getTipoPersona() : "Estudiante",
                            hora,
                            "✏ Manual"
                    });
                    lblContadorSalon.setText(listaIngresos.size() + " ingresos");
                    lblMensajeManual.setStyle("-fx-text-fill:#27ae60;-fx-font-size:11px;");
                    lblMensajeManual.setText("✅ Asistencia registrada. Aviso enviado al administrador.");
                    cmbEstudianteManual.setValue(null);

                    // Ocultar panel manual tras unos segundos
                    PauseTransition ocultar = new PauseTransition(Duration.seconds(4));
                    ocultar.setOnFinished(e -> {
                        panelAsistenciaManual.setVisible(false);
                        panelAsistenciaManual.setManaged(false);
                        lblMensajeManual.setText("");
                        fallosConsecutivos = 0;
                    });
                    ocultar.play();
                } else {
                    lblMensajeManual.setStyle("-fx-text-fill:#e74c3c;-fx-font-size:11px;");
                    lblMensajeManual.setText("❌ Error al registrar. Intenta de nuevo.");
                }
            });
        }, "registro-manual") {{ setDaemon(true); }}.start();
    }

    // ══════════════════════════════════════════════════════════════
    //  RECONOCIMIENTO BIOMÉTRICO
    // ══════════════════════════════════════════════════════════════

    @FXML public void iniciarReconocimiento() {
        if (cmbSalon.getValue() == null) {
            new Alert(Alert.AlertType.WARNING, "Selecciona un salón primero").showAndWait();
            return;
        }
        boolean modeloListo = ReconocimientoFacialService.isEntrenado();
        if (!modeloListo) lblEstadoCamara.setText("⚠ Sin personas enroladas aún");

        int idxCamara = (cmbCamara != null)
                ? cmbCamara.getSelectionModel().getSelectedIndex()
                : -1;

        if (idxCamara >= 0) ReconocimientoFacialService.setIndiceCamara(idxCamara);

        boolean ok = CamaraService.iniciarPreviewLocal(imgCamara, idxCamara);
        if (!ok) { new Alert(Alert.AlertType.ERROR, "Cámara no disponible").showAndWait(); return; }

        panelSinCamara.setVisible(false);
        btnIniciarCamara.setDisable(true);
        btnDetenerCamara.setDisable(false);
        if (cmbCamara != null) cmbCamara.setDisable(true);
        indicadorEstado.setFill(Color.web("#27ae60"));
        lblEstadoCamara.setText(modeloListo
                ? "🟢 Reconocimiento activo — " + cmbSalon.getValue().getNombre()
                : "🟡 Cámara activa — sin modelo entrenado");

        activo = true;
        enPausa.set(false);
        fallosConsecutivos = 0;
        avisoAdminEnviado  = false;

        cargarEstudiantesManual();

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "reconocimiento-salon");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::reconocer, 1, 1, TimeUnit.SECONDS);
    }

    @Override
    public void liberarRecursos() {
        activo = false;
        enPausa.set(false);
        ultimoIdReconocido  = -1;
        tiempoUltimoIngreso = 0;
        fallosConsecutivos  = 0;
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            try { scheduler.awaitTermination(300, TimeUnit.MILLISECONDS); }
            catch (InterruptedException ignored) {}
            scheduler = null;
        }
        ReconocimientoFacialService.liberarCamaraCompartida();
        CamaraService.detener();
    }

    @FXML public void detenerReconocimiento() {
        activo = false;
        enPausa.set(false);
        if (scheduler != null) {
            scheduler.shutdownNow();
            try { scheduler.awaitTermination(500, TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) {}
            scheduler = null;
        }
        ReconocimientoFacialService.liberarCamaraCompartida();
        CamaraService.detener();
        btnIniciarCamara.setDisable(false);
        btnDetenerCamara.setDisable(true);
        if (cmbCamara != null) cmbCamara.setDisable(false);
        indicadorEstado.setFill(Color.web("#4a5a78"));
        lblEstadoCamara.setText("Cámara inactiva");
        panelSinCamara.setVisible(true);
        cambiarPanel("esperando");
    }

    private void reconocer() {
        if (!activo || enPausa.get()) return;

        Platform.runLater(() -> cambiarPanel("reconociendo"));

        java.awt.image.BufferedImage frame = CamaraService.capturarFrame();
        int id = (frame != null)
                ? ReconocimientoFacialService.identificarDesdeFrame(frame)
                : ReconocimientoFacialService.identificarDesdeCamera();

        Platform.runLater(() -> {
            if (id == -1) {
                manejarFalloBiometrico();
                return;
            }

            Usuario p = UsuarioDAO.loginFacial(id);
            if (p == null) {
                manejarFalloBiometrico();
                return;
            }

            // ── Identificación exitosa: resetear contador de fallos ──
            fallosConsecutivos = 0;

            // Pausar el loop inmediatamente para evitar que el scheduler siga
            // procesando frames mientras se muestra el resultado (causa saturación).
            enPausa.set(true);

            // Cooldown por persona: evitar doble-registro de la misma persona
            long ahora = System.currentTimeMillis();
            if (id == ultimoIdReconocido && (ahora - tiempoUltimoIngreso) < COOLDOWN_PERSONA_MS) {
                // Misma persona demasiado rápido: mostrar resultado sin re-registrar
                PauseTransition cooldown = new PauseTransition(Duration.millis(PAUSA_TRAS_IDENTIFICACION_MS));
                cooldown.setOnFinished(ev -> { if (activo) { cambiarPanel("esperando"); enPausa.set(false); } });
                cooldown.play();
                return;
            }
            ultimoIdReconocido  = id;
            tiempoUltimoIngreso = ahora;

            // Registrar ingreso al salón
            RegistroIngresoDAO.registrarPorSalon(id, cmbSalon.getValue().getIdSalon());

            // FIX #7: usar helper para soportar URLs de Supabase y rutas locales
            Image img = cargarImagenFoto(p.getFoto());
            if (img != null) imgPersonaId.setImage(img);
            lblNombreId.setText(p.getNombreCompleto());
            lblTipoId.setText(p.getTipoPersona());
            lblHoraIngreso.setText("⏱ " + LocalDateTime.now().format(FMT_HORA));
            cambiarPanel("identificado");

            listaIngresos.add(0, new String[]{
                    p.getNombreCompleto(),
                    p.getTipoPersona(),
                    LocalDateTime.now().format(FMT_HORA),
                    "🔍 Biométrico"
            });
            lblContadorSalon.setText(listaIngresos.size() + " ingresos");

            ScaleTransition st = new ScaleTransition(Duration.millis(300), panelIdentificado);
            st.setFromX(0.8); st.setToX(1); st.setFromY(0.8); st.setToY(1); st.play();

            PauseTransition espera = new PauseTransition(Duration.millis(PAUSA_TRAS_IDENTIFICACION_MS));
            espera.setOnFinished(e -> {
                if (activo) { cambiarPanel("esperando"); enPausa.set(false); }
            });
            espera.play();
        });
    }

    /**
     * Maneja un fallo del reconocimiento biométrico.
     * Tras UMBRAL_FALLOS fallos consecutivos muestra el panel de asistencia manual.
     */
    private void manejarFalloBiometrico() {
        fallosConsecutivos++;
        cambiarPanel("no_reconocido");

        if (fallosConsecutivos >= UMBRAL_FALLOS) {
            // Mostrar panel de asistencia manual
            panelAsistenciaManual.setVisible(true);
            panelAsistenciaManual.setManaged(true);
            lblMensajeManual.setStyle("-fx-text-fill:#f39c12;-fx-font-size:11px;");
            lblMensajeManual.setText("⚠ " + fallosConsecutivos
                    + " intentos fallidos. Usa el registro manual.");
        }
    }

    // FIX #7: helper que soporta tanto URL Supabase (https://...) como ruta local
    private Image cargarImagenFoto(String foto) {
        if (foto == null || foto.isBlank()) return null;
        try {
            if (foto.startsWith("http://") || foto.startsWith("https://")) {
                return new Image(foto, true);
            } else {
                File f = new File(foto);
                if (f.exists()) return new Image(f.toURI().toString());
            }
        } catch (Exception e) {
            System.err.println("⚠ cargarImagenFoto: " + e.getMessage());
        }
        return null;
    }

    private void cambiarPanel(String estado) {
        panelEsperando   .setVisible(false); panelEsperando   .setManaged(false);
        panelReconociendo.setVisible(false); panelReconociendo.setManaged(false);
        panelIdentificado.setVisible(false); panelIdentificado.setManaged(false);
        panelNoReconocido.setVisible(false); panelNoReconocido.setManaged(false);
        VBox v = switch (estado) {
            case "reconociendo"  -> panelReconociendo;
            case "identificado"  -> panelIdentificado;
            case "no_reconocido" -> panelNoReconocido;
            default              -> panelEsperando;
        };
        v.setVisible(true); v.setManaged(true);
    }
}