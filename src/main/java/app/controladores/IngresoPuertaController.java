package app.controladores;

import app.conexion.Conexion;
import app.dao.UsuarioDAO;
import app.dao.RegistroIngresoDAO;
import app.dao.RestriccionDAO;
import app.modelos.Instalacion;
import app.modelos.Usuario;
import app.modelos.Puerta;
import app.servicios.CamaraService;
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

public class IngresoPuertaController implements Initializable, Liberable {

    private long ultimoIntrusoMs     = 0;
    private static final long INTERVALO_INTRUSOS_MS = 15_000;

    private int  ultimoIdReconocido  = -1;
    private long tiempoUltimoIngreso = 0;
    private static final long COOLDOWN_PERSONA_MS    = 10_000;

    // ── Pausa global: bloquea el loop mientras se muestra el resultado ──
    // Evita que el scheduler sature el sistema reconociendo sin parar
    // después de un ingreso exitoso, intento de intrusos o restricción.
    private final AtomicBoolean enPausa = new AtomicBoolean(false);
    private static final long   PAUSA_TRAS_EVENTO_MS = 5_000; // pausa tras cualquier evento

    // ─── Cámara ───
    @FXML private ImageView        imgCamara;
    @FXML private Circle           indicadorEstado;
    @FXML private Label            lblEstadoCamara;
    @FXML private Button           btnIniciarCamara;
    @FXML private Button           btnDetenerCamara;
    @FXML private VBox             panelSinCamara;

    // ─── Selector de cámara ───
    @FXML private ComboBox<String> cmbCamara;
    @FXML private Label            lblCamaraUtilizar;

    // ─── Selección ───
    @FXML private ComboBox<Instalacion> cmbInstalacion;
    @FXML private ComboBox<Puerta>      cmbPuerta;

    // ─── Paneles de resultado ───
    @FXML private VBox panelEsperando;
    @FXML private VBox panelReconociendo;
    @FXML private VBox panelIdentificado;
    @FXML private VBox panelRestringido;
    @FXML private VBox panelNoReconocido;

    // ─── Datos persona identificada ───
    @FXML private ImageView imgPersonaId;
    @FXML private Label     lblNombreId;
    @FXML private Label     lblTipoId;
    @FXML private Label     lblCarreraId;
    @FXML private Label     lblHoraIngreso;
    @FXML private Label     lblNombreRestringido;
    @FXML private Label     lblMotivoRestriccion;

    // ─── Tabla ingresos ───
    @FXML private TableView<String[]>           tablaIngresos;
    @FXML private TableColumn<String[], String> colNombreIngreso;
    @FXML private TableColumn<String[], String> colTipoIngreso;
    @FXML private TableColumn<String[], String> colHoraIngreso;
    @FXML private Label                         lblContadorIngresos;

    private final ObservableList<String[]> listaIngresos = FXCollections.observableArrayList();
    private ScheduledExecutorService       schedulerReconocimiento;
    private boolean                        reconociendoActivo = false;

    private static final DateTimeFormatter FMT_HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ══════════════════════════════════════════
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        configurarTabla();
        cargarInstalaciones();
        cargarListaCamaras();
        new Thread(() -> ReconocimientoFacialService.inicializar(), "opencv-init").start();

        javafx.application.Platform.runLater(() -> {
            try {
                if (tablaIngresos.getScene() != null && tablaIngresos.getScene().getWindow() != null) {
                    tablaIngresos.getScene().getWindow().setOnHiding(e -> liberarTodo());
                } else {
                    tablaIngresos.sceneProperty().addListener((obs, oldScene, newScene) -> {
                        if (newScene != null) {
                            newScene.windowProperty().addListener((obs2, oldW, newW) -> {
                                if (newW != null) newW.setOnHiding(e -> liberarTodo());
                            });
                        }
                    });
                }
            } catch (Exception ignored) {}
        });
    }

    // ══════════════════════════════════════════
    //  Carga lista de cámaras disponibles
    // ══════════════════════════════════════════
    private void cargarListaCamaras() {
        new Thread(() -> {
            List<String> camaras = CamaraService.listarCamaras();
            Platform.runLater(() -> {
                if (cmbCamara != null) {
                    cmbCamara.getItems().setAll(camaras);
                    if (!camaras.isEmpty()) cmbCamara.setValue(camaras.get(0));
                }
            });
        }, "listar-camaras-puerta") {{ setDaemon(true); }}.start();
    }

    @Override
    public void liberarRecursos() { liberarTodo(); }

    private void liberarTodo() {
        reconociendoActivo  = false;
        enPausa.set(false);
        ultimoIdReconocido  = -1;
        tiempoUltimoIngreso = 0;
        if (schedulerReconocimiento != null && !schedulerReconocimiento.isShutdown()) {
            schedulerReconocimiento.shutdownNow();
            schedulerReconocimiento = null;
        }
        CamaraService.detener();
        ReconocimientoFacialService.liberarCamaraCompartida();
    }

    // ══════════════════════════════════════════
    private void configurarTabla() {
        colNombreIngreso.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[0]));
        colTipoIngreso  .setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[1]));
        colHoraIngreso  .setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[2]));
        tablaIngresos.setItems(listaIngresos);
    }

    private void cargarInstalaciones() {
        List<Instalacion> instalaciones = new ArrayList<>();
        String sql = "SELECT * FROM instalaciones ORDER BY nombre";
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                instalaciones.add(new Instalacion(
                        rs.getInt("id_instalacion"), rs.getString("nombre")
                ));
            }
        } catch (SQLException e) {
            System.err.println("⚠ Error cargando instalaciones: " + e.getMessage());
        }
        cmbInstalacion.setItems(FXCollections.observableArrayList(instalaciones));
    }

    @FXML
    public void cargarPuertas() {
        Instalacion inst = cmbInstalacion.getValue();
        if (inst == null) return;
        List<Puerta> puertas = new ArrayList<>();
        String sql = "SELECT * FROM puertas WHERE id_instalacion = ?";
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, inst.getIdInstalacion());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Puerta p = new Puerta();
                p.setIdPuerta(rs.getInt("id_puerta"));
                p.setNombre  (rs.getString("nombre"));
                puertas.add(p);
            }
        } catch (SQLException e) {
            System.err.println("⚠ Error cargando puertas: " + e.getMessage());
        }
        cmbPuerta.setItems(FXCollections.observableArrayList(puertas));
        if (!puertas.isEmpty()) cmbPuerta.setValue(puertas.get(0));
    }

    // ══════════════════════════════════════════
    //  Iniciar reconocimiento
    // ══════════════════════════════════════════
    @FXML
    public void iniciarReconocimiento() {
        if (cmbPuerta.getValue() == null) {
            mostrarAlerta("Selecciona una puerta antes de iniciar");
            return;
        }

        boolean modeloListo = ReconocimientoFacialService.isEntrenado();
        if (!modeloListo) {
            lblEstadoCamara.setText("⚠ Sin personas enroladas aún");
        }

        // ── Obtener índice de la cámara seleccionada ──
        int idxCamara = (cmbCamara != null)
                ? cmbCamara.getSelectionModel().getSelectedIndex()
                : -1;

        // Registrar el índice en ReconocimientoFacialService para que ambos usen
        // el mismo dispositivo si OpenCV necesita abrirlo independientemente.
        if (idxCamara >= 0) ReconocimientoFacialService.setIndiceCamara(idxCamara);

        boolean camaraOk = CamaraService.iniciarPreviewLocal(imgCamara, idxCamara);
        if (!camaraOk) {
            mostrarAlerta("❌ No se pudo iniciar la cámara");
            return;
        }

        panelSinCamara.setVisible(false);
        btnIniciarCamara.setDisable(true);
        btnDetenerCamara.setDisable(false);
        if (cmbCamara != null) cmbCamara.setDisable(true);
        indicadorEstado.setFill(Color.web("#27ae60"));
        lblEstadoCamara.setText(modeloListo ? "🟢 Reconocimiento activo" : "🟡 Cámara activa — sin modelo entrenado");
        reconociendoActivo = true;

        schedulerReconocimiento = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "reconocimiento-puerta");
            t.setDaemon(true);
            return t;
        });
        schedulerReconocimiento.scheduleAtFixedRate(
                this::ejecutarReconocimiento, 1, 1, TimeUnit.SECONDS
        );
    }

    @FXML
    public void detenerReconocimiento() {
        liberarTodo();
        Platform.runLater(() -> {
            btnIniciarCamara.setDisable(false);
            btnDetenerCamara.setDisable(true);
            if (cmbCamara != null) cmbCamara.setDisable(false);
            indicadorEstado.setFill(Color.web("#4a5a78"));
            lblEstadoCamara.setText("Cámara inactiva");
            panelSinCamara.setVisible(true);
            mostrarPanelEstado("esperando");
        });
    }

    // ══════════════════════════════════════════
    //  Loop de reconocimiento facial
    // ══════════════════════════════════════════
    private void ejecutarReconocimiento() {
        if (!reconociendoActivo || enPausa.get()) return;

        Platform.runLater(() -> mostrarPanelEstado("reconociendo"));

        java.awt.image.BufferedImage frame = CamaraService.capturarFrame();
        int idPersona = (frame != null)
                ? ReconocimientoFacialService.identificarDesdeFrame(frame)
                : ReconocimientoFacialService.identificarDesdeCamera();

        Platform.runLater(() -> {
            if (idPersona == -1) {
                mostrarPanelEstado("no_reconocido");

                long ahora = System.currentTimeMillis();
                if (ahora - ultimoIntrusoMs >= INTERVALO_INTRUSOS_MS) {
                    ultimoIntrusoMs = ahora;
                    app.servicios.VozService.intruso();
                    // Pausa global para no saturar mientras se graba el intruder
                    enPausa.set(true);
                    new Thread(() -> {
                        try {
                            String rutaFoto = CamaraService.capturarFoto("intruder");
                            String ubicacion = cmbPuerta.getValue() != null
                                    ? cmbPuerta.getValue().getNombre() : "Puerta";
                            app.dao.IntrusoDAO.registrar(ubicacion, rutaFoto);
                        } catch (Exception ignored) {}
                    }, "bg-thread-4") {{ setDaemon(true); }}.start();
                    javafx.animation.PauseTransition pausaIntruder =
                            new javafx.animation.PauseTransition(javafx.util.Duration.millis(PAUSA_TRAS_EVENTO_MS));
                    pausaIntruder.setOnFinished(ev -> {
                        if (reconociendoActivo) { mostrarPanelEstado("esperando"); enPausa.set(false); }
                    });
                    pausaIntruder.play();
                }
                return;
            }

            Usuario persona = UsuarioDAO.loginFacial(idPersona);
            if (persona == null) {
                mostrarPanelEstado("no_reconocido");
                return;
            }

            if (RestriccionDAO.tieneRestriccionActiva(idPersona)) {
                mostrarPersonaRestringida(persona);
                return;
            }

            long ahora = System.currentTimeMillis();
            if (idPersona == ultimoIdReconocido && (ahora - tiempoUltimoIngreso) < COOLDOWN_PERSONA_MS) {
                mostrarPanelEstado("esperando");
                return;
            }
            ultimoIdReconocido  = idPersona;
            tiempoUltimoIngreso = ahora;

            // Pausar el loop ANTES de hacer el registro para evitar saturación
            enPausa.set(true);

            Puerta puerta = cmbPuerta.getValue();
            RegistroIngresoDAO.registrarPorPuerta(idPersona, puerta.getIdPuerta());
            mostrarPersonaIdentificada(persona);

            listaIngresos.add(0, new String[]{
                    persona.getNombreCompleto(),
                    persona.getTipoPersona(),
                    LocalDateTime.now().format(FMT_HORA)
            });
            lblContadorIngresos.setText(listaIngresos.size() + " registros");
        });
    }

    // FIX #7: helper que soporta tanto URL Supabase (https://...) como ruta local
    private javafx.scene.image.Image cargarImagenFoto(String foto) {
        if (foto == null || foto.isBlank()) return null;
        try {
            if (foto.startsWith("http://") || foto.startsWith("https://")) {
                return new javafx.scene.image.Image(foto, true); // carga asíncrona desde URL
            } else {
                java.io.File f = new java.io.File(foto);
                if (f.exists()) return new javafx.scene.image.Image(f.toURI().toString());
            }
        } catch (Exception e) {
            System.err.println("⚠ cargarImagenFoto: " + e.getMessage());
        }
        return null;
    }

    // ══════════════════════════════════════════
    private void mostrarPersonaIdentificada(Usuario persona) {
        // FIX #7: usar helper para soportar URLs de Supabase y rutas locales
        javafx.scene.image.Image img = cargarImagenFoto(persona.getFoto());
        if (img != null) imgPersonaId.setImage(img);
        lblNombreId.setText(persona.getNombreCompleto());
        lblTipoId.setText(persona.getTipoPersona());
        lblCarreraId.setText(persona.getCarrera() != null ? persona.getCarrera() : "");
        lblHoraIngreso.setText("⏱ " + LocalDateTime.now().format(FMT_HORA));

        mostrarPanelEstado("identificado");
        animarIdentificacion(true);
        app.servicios.VozService.accesoPermitido();

        PauseTransition pausa = new PauseTransition(Duration.seconds(4));
        pausa.setOnFinished(e -> {
            if (reconociendoActivo) { mostrarPanelEstado("esperando"); enPausa.set(false); }
        });
        pausa.play();
    }

    private void mostrarPersonaRestringida(Usuario persona) {
        lblNombreRestringido.setText(persona.getNombreCompleto());
        lblMotivoRestriccion.setText("Acceso restringido por la administración");
        mostrarPanelEstado("restringido");
        animarIdentificacion(false);
        app.servicios.VozService.accesoRestringido();

        enPausa.set(true);
        PauseTransition pausa = new PauseTransition(Duration.seconds(5));
        pausa.setOnFinished(e -> {
            if (reconociendoActivo) { mostrarPanelEstado("esperando"); enPausa.set(false); }
        });
        pausa.play();
    }

    private void mostrarPanelEstado(String estado) {
        panelEsperando   .setVisible(false); panelEsperando   .setManaged(false);
        panelReconociendo.setVisible(false); panelReconociendo.setManaged(false);
        panelIdentificado.setVisible(false); panelIdentificado.setManaged(false);
        panelRestringido .setVisible(false); panelRestringido .setManaged(false);
        panelNoReconocido.setVisible(false); panelNoReconocido.setManaged(false);

        VBox activo = switch (estado) {
            case "reconociendo"  -> panelReconociendo;
            case "identificado"  -> panelIdentificado;
            case "restringido"   -> panelRestringido;
            case "no_reconocido" -> panelNoReconocido;
            default              -> panelEsperando;
        };
        activo.setVisible(true);
        activo.setManaged(true);
    }

    private void animarIdentificacion(boolean exito) {
        ScaleTransition scale = new ScaleTransition(Duration.millis(300),
                exito ? panelIdentificado : panelRestringido);
        scale.setFromX(0.8); scale.setToX(1.0);
        scale.setFromY(0.8); scale.setToY(1.0);
        scale.play();
    }

    private void mostrarAlerta(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK);
        alert.setTitle("BiometricUMG");
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}