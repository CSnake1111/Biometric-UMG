package app.controladores;

import app.Main;
import app.dao.AuditoriaDAO;
import app.dao.UsuarioDAO;
import app.modelos.Usuario;
import app.servicios.CamaraService;
import app.servicios.ReconocimientoFacialService;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.util.Duration;

import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;

public class LoginController implements Initializable, Liberable {

    @FXML private TextField         txtUsuario;
    @FXML private PasswordField     txtPassword;
    @FXML private Button            btnLogin;
    @FXML private Label             lblError;
    @FXML private VBox              panelError;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private ImageView         imgLogo;

    // Panel reconocimiento facial
    @FXML private VBox      panelFacial;
    @FXML private ImageView imgCamaraLogin;
    @FXML private Label     lblEstadoFacial;
    @FXML private Button    btnFacial;

    // ─── Selector de cámara ───
    @FXML private ComboBox<String> cmbCamara;
    @FXML private Label            lblCamaraUtilizar;

    private boolean modoFacialActivo = false;
    private java.util.concurrent.ScheduledExecutorService schedulerFacial;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            Image logo = new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream("/images/logo_umg.png")
            ));
            imgLogo.setImage(logo);
        } catch (Exception ignored) {}

        txtPassword.setOnAction(e -> onLogin());
        txtUsuario.setOnAction(e -> txtPassword.requestFocus());
        txtUsuario.textProperty().addListener((obs, old, nv) -> ocultarError());
        txtPassword.textProperty().addListener((obs, old, nv) -> ocultarError());

        animarEntrada();
        ReconocimientoFacialService.inicializar();

        // ── Cargar lista de cámaras en background ──
        cargarListaCamaras();
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
        }, "listar-camaras-login") {{ setDaemon(true); }}.start();
    }

    @FXML
    public void onLogin() {
        String usuario  = txtUsuario.getText().trim();
        String password = txtPassword.getText();

        if (usuario.isEmpty()) {
            mostrarError("⚠ Ingresa tu usuario");
            txtUsuario.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            mostrarError("⚠ Ingresa tu contraseña");
            txtPassword.requestFocus();
            return;
        }

        setLoading(true);
        new Thread(() -> {
            boolean bloqueado = AuditoriaDAO.estaBloqueado(usuario);
            Usuario usuarioAuth = bloqueado ? null : UsuarioDAO.login(usuario, password);
            Platform.runLater(() -> {
                setLoading(false);
                if (bloqueado) {
                    java.time.LocalDateTime hasta = AuditoriaDAO.getBloqueadoHasta(usuario);
                    String msg = "🔒 Cuenta bloqueada" + (hasta != null
                            ? " hasta las " + hasta.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                            : "");
                    mostrarError(msg);
                    animarErrorShake();
                    return;
                }
                if (usuarioAuth == null) {
                    mostrarError("❌ Usuario o contraseña incorrectos");
                    animarErrorShake();
                    txtPassword.clear();
                    txtPassword.requestFocus();
                    return;
                }
                redirigirPorRol(usuarioAuth);
            });
        }, "bg-thread-22") {{ setDaemon(true); }}.start();
    }

    // ══════════════════════════════════════════
    //  RECONOCIMIENTO FACIAL
    // ══════════════════════════════════════════
    @Override
    public void liberarRecursos() { desactivarModoFacial(); }

    @FXML
    public void toggleReconocimientoFacial() {
        if (!modoFacialActivo) {
            activarModoFacial();
        } else {
            desactivarModoFacial();
        }
    }

    private void activarModoFacial() {
        if (!ReconocimientoFacialService.isEntrenado()) {
            mostrarError("⚠ El modelo facial no está entrenado todavía");
            return;
        }

        panelFacial.setVisible(true);
        panelFacial.setManaged(true);
        modoFacialActivo = true;
        lblEstadoFacial.setText("🔄 Iniciando cámara...");

        // ── Obtener índice de la cámara seleccionada ──
        int idxCamara = (cmbCamara != null)
                ? cmbCamara.getSelectionModel().getSelectedIndex()
                : -1;

        if (idxCamara >= 0) ReconocimientoFacialService.setIndiceCamara(idxCamara);
        boolean ok = CamaraService.iniciarPreviewLocal(imgCamaraLogin, idxCamara);
        if (!ok) {
            mostrarError("❌ No se pudo iniciar la cámara");
            desactivarModoFacial();
            return;
        }

        // Deshabilitar selector mientras la cámara está activa
        if (cmbCamara != null) cmbCamara.setDisable(true);

        lblEstadoFacial.setText("👁 Mirando a la cámara...");

        final int[] intentosFallidos = {0};
        final int MAX_INTENTOS = 5;

        schedulerFacial = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "facial-login");
            t.setDaemon(true);
            return t;
        });
        schedulerFacial.scheduleAtFixedRate(() -> {
            if (!modoFacialActivo) return;

            java.awt.image.BufferedImage frame = CamaraService.capturarFrame();
            int idPersona = ReconocimientoFacialService.identificarDesdeFrame(frame);

            if (idPersona != -1) {
                Usuario u = UsuarioDAO.buscarPorIdPersona(idPersona);
                if (u != null) {
                    AuditoriaDAO.registrarIntentoLogin(u.getUsuario(), true, "FACIAL", null);
                    Platform.runLater(() -> {
                        desactivarModoFacial();
                        lblEstadoFacial.setText("✅ Identificado");
                        redirigirPorRol(u);
                    });
                } else {
                    Platform.runLater(() ->
                            lblEstadoFacial.setText("⚠ Rostro reconocido pero sin usuario")
                    );
                }
            } else {
                intentosFallidos[0]++;
                int restantes = MAX_INTENTOS - intentosFallidos[0];

                Platform.runLater(() -> {
                    if (intentosFallidos[0] >= MAX_INTENTOS) {
                        desactivarModoFacial();
                        mostrarOpcionesFallidas();
                    } else {
                        lblEstadoFacial.setText(
                                "👁 Buscando rostro... (" + restantes + " intentos restantes)"
                        );
                    }
                });
            }
        }, 1, 2, java.util.concurrent.TimeUnit.SECONDS);
    }

    private void mostrarOpcionesFallidas() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Reconocimiento Fallido");
        alert.setHeaderText("No se pudo identificar el rostro");
        alert.setContentText("¿Qué deseas hacer?");

        ButtonType btnReintentar = new ButtonType("🔄 Reintentar");
        ButtonType btnManual     = new ButtonType("⌨ Ingresar Manualmente");
        alert.getButtonTypes().setAll(btnReintentar, btnManual);

        alert.showAndWait().ifPresent(respuesta -> {
            if (respuesta == btnReintentar) {
                activarModoFacial();
            }
        });
    }

    private void desactivarModoFacial() {
        modoFacialActivo = false;
        if (schedulerFacial != null) {
            schedulerFacial.shutdownNow();
            schedulerFacial = null;
        }
        CamaraService.detener();
        Platform.runLater(() -> {
            panelFacial.setVisible(false);
            panelFacial.setManaged(false);
            // Rehabilitar selector al detener la cámara
            if (cmbCamara != null) cmbCamara.setDisable(false);
        });
    }

    private void redirigirPorRol(Usuario usuario) {
        UsuarioDAO.setSesionActiva(usuario);
        switch (usuario.getNombreRol()) {
            case "Administrador" ->
                    Main.navegarA("/fxml/Dashboard.fxml", "Dashboard — Administrador", 1200, 750);
            case "Catedratico" ->
                    Main.navegarA("/fxml/DashboardCatedratico.fxml", "Panel Catedrático", 1100, 700);
            case "Seguridad" ->
                    Main.navegarA("/fxml/Ingreso.fxml", "Control de Ingreso", 1000, 680);
            case "Estudiante" ->
                    Main.navegarA("/fxml/PerfilEstudiante.fxml", "Mi Perfil", 950, 700);
            default ->
                    Main.navegarA("/fxml/Dashboard.fxml", "Sistema", 1100, 700);
        }
    }

    private void mostrarError(String mensaje) {
        lblError.setText(mensaje);
        panelError.setVisible(true);
        panelError.setManaged(true);
        PauseTransition pausa = new PauseTransition(Duration.seconds(4));
        pausa.setOnFinished(e -> ocultarError());
        pausa.play();
    }

    private void ocultarError() {
        panelError.setVisible(false);
        panelError.setManaged(false);
    }

    private void setLoading(boolean activo) {
        btnLogin.setDisable(activo);
        loadingIndicator.setVisible(activo);
        loadingIndicator.setManaged(activo);
        btnLogin.setText(activo ? "Verificando..." : "INGRESAR AL SISTEMA");
    }

    private void animarEntrada() {
        javafx.scene.Node[] nodos = {txtUsuario, txtPassword, btnLogin};
        for (int i = 0; i < nodos.length; i++) {
            final int idx = i;
            nodos[i].setOpacity(0);
            nodos[i].setTranslateY(20);
            PauseTransition delay = new PauseTransition(Duration.millis(100 + idx * 80));
            delay.setOnFinished(e -> {
                FadeTransition fade = new FadeTransition(Duration.millis(400), nodos[idx]);
                fade.setFromValue(0); fade.setToValue(1);
                TranslateTransition slide = new TranslateTransition(Duration.millis(400), nodos[idx]);
                slide.setFromY(20); slide.setToY(0);
                new ParallelTransition(fade, slide).play();
            });
            delay.play();
        }
    }

    private void animarErrorShake() {
        TranslateTransition shake = new TranslateTransition(Duration.millis(60), btnLogin);
        shake.setFromX(0); shake.setByX(10);
        shake.setCycleCount(6);
        shake.setAutoReverse(true);
        shake.setOnFinished(e -> btnLogin.setTranslateX(0));
        shake.play();
    }
}