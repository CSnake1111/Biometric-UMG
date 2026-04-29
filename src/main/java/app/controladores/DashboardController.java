package app.controladores;

import app.Main;
import app.conexion.Conexion;
import app.dao.UsuarioDAO;
import app.modelos.Usuario;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.*;

public class DashboardController implements Initializable {

    // Sidebar
    @FXML private Label     lblUsuarioSidebar;
    @FXML private Label     lblRolBadge;
    @FXML private ImageView imgLogoSidebar;


    // Topbar
    @FXML private Label lblTituloPantalla;
    @FXML private Label lblFechaHora;
    @FXML private Label lblEstadoBD;

    // Dashboard inicio
    @FXML private Label lblBienvenida;
    @FXML private Label lblFechaBienvenida;
    @FXML private Label statPersonas;
    @FXML private Label statIngresosHoy;
    @FXML private Label statAsistencias;
    @FXML private Label statRestricciones;
    @FXML private Label statIntrusos;
    @FXML private Label statPctAsistencia;

    @FXML private Label statEnRiesgo;
    @FXML private Label lblAlertasBadge;


    @FXML private Button btnDashboard;
    @FXML private Button btnRegistro;
    @FXML private Button btnListaPersonas;

    @FXML private Button btnPuerta;
    @FXML private Button btnSalon;
    @FXML private Button btnRestricciones;
    @FXML private Button btnIntrusos;
    @FXML private Button btnCursos;
    @FXML private Button btnArbol;
    @FXML private Button btnEntrenamiento;
    @FXML private Button btnRepPuerta;
    @FXML private Button btnRepSalon;
    @FXML private Button btnAnalitica;
    @FXML private Button btnUsuarios;
    @FXML private Button btnCambiarPass;
    @FXML private Button btnAuditoria;
    @FXML private Button btnCerrar;

    // Tabla
    @FXML private TableView<String[]>       tablaUltimosIngresos;
    @FXML private TableColumn<String[],String> colNombre;
    @FXML private TableColumn<String[],String> colTipo;
    @FXML private TableColumn<String[],String> colUbicacion;
    @FXML private TableColumn<String[],String> colFechaHora;

    // Panel dinámico
    @FXML private StackPane contenedorPrincipal;
    @FXML private VBox      panelInicio;

    private ScheduledExecutorService reloj;
    private static final DateTimeFormatter FMT_FECHA =
            DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM 'de' yyyy");
    private static final DateTimeFormatter FMT_HORA =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        cargarLogo();
        configurarInfoUsuario();
        configurarTablaIngresos();
        iniciarReloj();
        cargarEstadisticas();
        actualizarBadgeAlertas();
    }

    // ── Logo ──
    private void cargarLogo() {
        try {
            if (imgLogoSidebar != null)
                imgLogoSidebar.setImage(new Image(
                        Objects.requireNonNull(getClass().getResourceAsStream("/images/logo_umg.png"))));
        } catch (Exception ignored) {}
    }


    // ── Info usuario ──
    private void configurarInfoUsuario() {
        Usuario u = UsuarioDAO.getSesionActiva();
        if (u != null) {
            lblUsuarioSidebar.setText(u.getUsuario());
            lblRolBadge.setText(u.getNombreRol().toUpperCase());
            if (lblBienvenida != null)
                lblBienvenida.setText("Bienvenido, " + u.getUsuario());
        }
        if (lblFechaBienvenida != null)
            lblFechaBienvenida.setText(LocalDateTime.now().format(FMT_FECHA));

        boolean bdOk = Conexion.probarConexion();
        if (lblEstadoBD != null) {
            lblEstadoBD.setText(bdOk ? "● BD Conectada" : "● BD Sin conexión");
            lblEstadoBD.setStyle(bdOk
                    ? "-fx-text-fill: #27ae60; -fx-font-size: 11px;"
                    : "-fx-text-fill: #e74c3c; -fx-font-size: 11px;");
        }
    }

    private void actualizarBadgeAlertas() {
        new Thread(() -> {
            int alertas = app.dao.AuditoriaDAO.contarAlertasActivas();
            Platform.runLater(() -> {
                if (lblAlertasBadge != null) {
                    lblAlertasBadge.setText(alertas > 0 ? String.valueOf(alertas) : "");
                    lblAlertasBadge.setVisible(alertas > 0);
                    lblAlertasBadge.setManaged(alertas > 0);
                }
            });
        }, "badge-alertas") {{ setDaemon(true); }}.start();
    }

    // ── Reloj ──
    private void iniciarReloj() {
        reloj = Executors.newSingleThreadScheduledExecutor();
        reloj.scheduleAtFixedRate(() -> {
            String hora = LocalDateTime.now().format(FMT_HORA);
            Platform.runLater(() -> { if (lblFechaHora != null) lblFechaHora.setText(hora); });
        }, 0, 1, TimeUnit.SECONDS);
    }

    // ── Estadísticas ──
    private void cargarEstadisticas() {
        new Thread(() -> {
            int[] stats = consultarEstadisticas();
            int intrusosHoy       = app.dao.IntrusoDAO.contarHoy();
            int enRiesgo          = app.servicios.AnaliticaService.obtenerEstudiantesEnRiesgo().size();
            double pctAsist       = app.servicios.AnaliticaService.porcentajeAsistenciaHoy();
            Platform.runLater(() -> {
                animarContador(statPersonas,      stats[0]);
                animarContador(statIngresosHoy,   stats[1]);
                animarContador(statAsistencias,   stats[2]);
                animarContador(statRestricciones, stats[3]);
                if (statIntrusos    != null) animarContador(statIntrusos,    intrusosHoy);
                if (statEnRiesgo    != null) animarContador(statEnRiesgo,    enRiesgo);
                if (statPctAsistencia != null)
                    statPctAsistencia.setText(String.format("%.0f%%", pctAsist));
                cargarUltimosIngresos();
            });
        }, "bg-stats") {{ setDaemon(true); }}.start();
    }

    private int[] consultarEstadisticas() {
        int[] stats = {0, 0, 0, 0};
        try (Connection con = Conexion.nuevaConexion()) {
            if (con == null) return stats;
            String[] queries = {
                    "SELECT COUNT(*) FROM usuarios WHERE estado = TRUE",
                    "SELECT COUNT(*) FROM registro_ingresos WHERE fecha_hora::date = CURRENT_DATE",
                    "SELECT COUNT(*) FROM asistencias WHERE fecha = CURRENT_DATE AND estado IN ('PRESENTE','TARDANZA')",
                    "SELECT COUNT(*) FROM restricciones WHERE fecha_inicio <= CURRENT_DATE AND fecha_fin >= CURRENT_DATE"
            };
            for (int i = 0; i < queries.length; i++) {
                try (PreparedStatement ps = con.prepareStatement(queries[i]);
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) stats[i] = rs.getInt(1);
                }
            }
        } catch (SQLException e) { System.err.println("⚠ " + e.getMessage()); }
        return stats;
    }

    private void animarContador(Label label, int valorFinal) {
        if (label == null) return;
        Timeline tl = new Timeline();
        int pasos = 30;
        for (int i = 0; i <= pasos; i++) {
            final int val = (int)(valorFinal * (i / (double) pasos));
            KeyFrame kf = new KeyFrame(Duration.millis(i * 30L),
                    e -> label.setText(String.valueOf(val)));
            tl.getKeyFrames().add(kf);
        }
        tl.play();
    }

    // ── Tabla ──
    private void configurarTablaIngresos() {
        if (colNombre    != null) colNombre   .setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[0]));
        if (colTipo      != null) colTipo     .setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[1]));
        if (colUbicacion != null) colUbicacion.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[2]));
        if (colFechaHora != null) colFechaHora.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[3]));
    }

    private void cargarUltimosIngresos() {
        ObservableList<String[]> datos = FXCollections.observableArrayList();
        String sql = """
            SELECT
                p.nombre || ' ' || p.apellido AS nombre,
                p.tipo_persona,
                COALESCE(pu.nombre, s.nombre, 'N/A') AS ubicacion,
                TO_CHAR(ri.fecha_hora, 'DD/MM/YYYY HH24:MI:SS') AS fecha_hora
            FROM registro_ingresos ri
            JOIN usuarios p ON ri.id_usuario = p.id_usuario
            LEFT JOIN puertas pu ON ri.id_puerta = pu.id_puerta
            LEFT JOIN salones s  ON ri.id_salon  = s.id_salon
            ORDER BY ri.fecha_hora DESC LIMIT 20
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next())
                datos.add(new String[]{
                        rs.getString("nombre"), rs.getString("tipo_persona"),
                        rs.getString("ubicacion"), rs.getString("fecha_hora")
                });
        } catch (SQLException e) { System.err.println("⚠ " + e.getMessage()); }
        if (tablaUltimosIngresos != null) tablaUltimosIngresos.setItems(datos);
    }

    // ── Navegación ──
    private void cargarPantalla(String fxmlPath, String titulo) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent pantalla = loader.load();

            contenedorPrincipal.getChildren().clear();
            contenedorPrincipal.getChildren().add(pantalla);
            if (lblTituloPantalla != null) lblTituloPantalla.setText(titulo);

            pantalla.setOpacity(0);
            FadeTransition ft = new FadeTransition(Duration.millis(200), pantalla);
            ft.setFromValue(0); ft.setToValue(1);
            ft.play();
        } catch (IOException e) {
            System.err.println("❌ Error cargando: " + fxmlPath);
            mostrarError("No se pudo cargar: " + fxmlPath);
        }
    }

    private void mostrarError(String msg) {
        Label lbl = new Label("⚠ " + msg);
        lbl.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 14px;");
        contenedorPrincipal.getChildren().setAll(lbl);
    }

    // ── Acciones menú ──
    @FXML public void mostrarDashboard() {
        contenedorPrincipal.getChildren().setAll(panelInicio);
        if (lblTituloPantalla != null) lblTituloPantalla.setText("Dashboard");
        cargarEstadisticas();
    }
    @FXML public void mostrarRegistroPersonas()  { cargarPantalla("/fxml/RegistroPersona.fxml",  "Registro de Personas"); }
    @FXML public void mostrarListaPersonas()     { cargarPantalla("/fxml/ListaPersonas.fxml",    "Personas Registradas"); }
    @FXML public void mostrarIngresoPuerta()     { cargarPantalla("/fxml/IngresoPuerta.fxml",    "Ingreso — Puerta Principal"); }
    @FXML public void mostrarIngresoSalon()      { cargarPantalla("/fxml/IngresoSalon.fxml",     "Ingreso — Salón"); }
    @FXML public void mostrarEntrenamiento()     { cargarPantalla("/fxml/Entrenamiento.fxml",    "Entrenamiento Facial"); }
    @FXML public void mostrarGestionCursos()     { cargarPantalla("/fxml/GestionCursos.fxml",    "Gestión de Cursos"); }
    @FXML public void mostrarArbolAsistencias()  { cargarPantalla("/fxml/ArbolAsistencias.fxml", "Árbol de Asistencias"); }
    @FXML public void mostrarRestricciones()     { cargarPantalla("/fxml/Restricciones.fxml",    "Restricciones"); }
    @FXML public void mostrarReportesPuerta()    { cargarPantalla("/fxml/ReportesPuerta.fxml",   "Reportes — Puerta"); }
    @FXML public void mostrarReportesSalon()     { cargarPantalla("/fxml/ReportesSalon.fxml",    "Reportes — Salón"); }
    @FXML public void mostrarAnalitica()         { cargarPantallaAnaliticaInline(); }
    @FXML public void mostrarIntrusos()          { cargarPantallaIntrusos(); }

    @FXML public void mostrarCambiarPass() {
        cargarPantalla("/fxml/CambiarPass.fxml", "Cambiar Contraseña");
    }

    @FXML public void mostrarAuditoria() {
        app.modelos.Usuario u = UsuarioDAO.getSesionActiva();
        if (u == null || !esAdmin(u)) {
            mostrarError("⛔ Acceso denegado — Solo Administrador");
            app.dao.AuditoriaDAO.registrarAccion(
                    "Acceso denegado a Auditoría: " + (u != null ? u.getUsuario() : "?"), "AUDITORIA");
            return;
        }
        app.dao.AuditoriaDAO.registrarAccion("Auditoría abierta por " + u.getUsuario(), "AUDITORIA");
        cargarPantalla("/fxml/Auditoria.fxml", "Auditoría");
        actualizarBadgeAlertas();
    }

    @FXML public void mostrarGestionUsuarios() {
        app.modelos.Usuario u = UsuarioDAO.getSesionActiva();
        if (u == null || !esAdmin(u)) {
            mostrarError("⛔ Acceso denegado — Solo Administrador");
            return;
        }
        cargarPantalla("/fxml/GestionUsuarios.fxml", "Gestión de Usuarios");
    }

    /** Comprueba si el usuario tiene rol administrador (tolerante a variantes de texto) */
    private boolean esAdmin(app.modelos.Usuario u) {
        if (u == null) return false;
        String rol = u.getNombreRol();
        if (rol == null) return false;
        return rol.equalsIgnoreCase("Administrador")
                || rol.equalsIgnoreCase("Admin")
                || rol.equalsIgnoreCase("ADMIN");
    }

    // ── Demo Mode ──
    @FXML public void ejecutarDemoMode() {
        app.modelos.Usuario u = UsuarioDAO.getSesionActiva();
        if (u == null || !esAdmin(u)) {
            mostrarError("⚠ Solo el Administrador puede ejecutar el modo demo.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "🎬 MODO DEMO\n\nSimulará un día completo de clases.\n¿Continuar?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Modo Demo");
        confirm.showAndWait().ifPresent(resp -> {
            if (resp == ButtonType.YES) new Thread(this::ejecutarSimulacionDemo).start();
        });
    }

    private void ejecutarSimulacionDemo() {
        Platform.runLater(() -> { if (lblTituloPantalla != null) lblTituloPantalla.setText("⏳ Ejecutando demo..."); });
        try (Connection con = app.conexion.Conexion.nuevaConexion()) {
            if (con == null) { Platform.runLater(() -> mostrarError("Sin conexión a BD")); return; }
            java.util.List<Integer> ids = new java.util.ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT id_usuario FROM usuarios WHERE estado = TRUE AND tipo_persona='Estudiante' LIMIT 20");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getInt(1));
            }
            java.util.List<Integer> puertas = new java.util.ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement("SELECT id_puerta FROM puertas");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) puertas.add(rs.getInt(1));
            }
            java.util.Random rnd = new java.util.Random();
            java.time.LocalDate hoy = java.time.LocalDate.now();
            for (int idP : ids) {
                if (puertas.isEmpty()) break;
                int idPuerta = puertas.get(rnd.nextInt(puertas.size()));
                java.time.LocalDateTime hora = hoy.atTime(7, rnd.nextInt(60), rnd.nextInt(60));
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO registro_ingresos (id_usuario, id_puerta, fecha_hora, tipo_ingreso) VALUES (?,?,?,?)")) {
                    ps.setInt(1, idP); ps.setInt(2, idPuerta);
                    ps.setTimestamp(3, java.sql.Timestamp.valueOf(hora));
                    ps.setString(4, "PUERTA"); ps.executeUpdate();
                } catch (Exception ignored) {}
                Thread.sleep(50);
            }
            try (PreparedStatement psCursos = con.prepareStatement("SELECT id_curso, id_salon, id_catedratico FROM cursos");
                 ResultSet rs = psCursos.executeQuery()) {
                while (rs.next())
                    app.dao.AsistenciaDAO.confirmarAsistencia(
                            rs.getInt("id_curso"), rs.getInt("id_salon"), rs.getInt("id_catedratico"));
            }
            Platform.runLater(() -> {
                if (lblTituloPantalla != null) lblTituloPantalla.setText("Dashboard");
                cargarEstadisticas();
                new Alert(Alert.AlertType.INFORMATION,
                        "✅ Demo completado!\n" + ids.size() + " ingresos generados.").showAndWait();
            });
        } catch (Exception e) {
            Platform.runLater(() -> mostrarError("Error en demo: " + e.getMessage()));
        }
    }

    // ── Analítica inline ──
    private void cargarPantallaAnaliticaInline() {
        if (lblTituloPantalla != null) lblTituloPantalla.setText("📊 Analítica Inteligente");

        // FIX #1: mostrar loading PRIMERO, luego construir el panel cuando los datos ya llegaron.
        // Antes: el panel se ponía en pantalla vacío y el hilo intentaba agregar labels después
        // de que el ScrollPane ya estaba renderizado → los datos nunca aparecían.
        VBox panelLoading = new VBox(20);
        panelLoading.setPadding(new Insets(30));
        panelLoading.setAlignment(javafx.geometry.Pos.CENTER);
        ProgressIndicator pi = new ProgressIndicator();
        pi.setMaxSize(60, 60);
        Label lblCargando = new Label("Cargando analítica...");
        lblCargando.setStyle("-fx-text-fill:#8a9bbf;-fx-font-size:13px;");
        panelLoading.getChildren().addAll(pi, lblCargando);

        ScrollPane sp = new ScrollPane(panelLoading);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background:transparent;-fx-background-color:transparent;");
        contenedorPrincipal.getChildren().setAll(sp);

        // FIX #2: cargar datos en hilo y construir el panel COMPLETO antes de ponerlo en pantalla
        new Thread(() -> {
            // FIX #3: rankingPuntuales requiere HAVING COUNT >= 2. Si hay pocos datos usar 1.
            var riesgos     = app.servicios.AnaliticaService.obtenerEstudiantesEnRiesgo();
            var ranking     = app.servicios.AnaliticaService.rankingPuntuales(10);
            double pct      = app.servicios.AnaliticaService.porcentajeAsistenciaHoy();
            var prediccion  = app.servicios.AnaliticaService.prediccionAusenciaManana();
            var porCurso    = app.servicios.AnaliticaService.resumenPorCurso();

            Platform.runLater(() -> {
                VBox panel = new VBox(16);
                panel.setPadding(new Insets(24));

                // ── Título ──
                Label titulo = new Label("📊 Analítica Inteligente");
                titulo.setStyle("-fx-font-size:20px;-fx-font-weight:bold;-fx-text-fill:#e8edf5;");
                panel.getChildren().add(titulo);

                // ── % Asistencia hoy ──
                String colorPct = pct >= 75 ? "#27ae60" : pct >= 50 ? "#f39c12" : "#e74c3c";
                Label lp = new Label("📈  Asistencia hoy: " + String.format("%.1f%%", pct));
                lp.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:" + colorPct + ";"
                        + "-fx-background-color:" + colorPct + "22;"
                        + "-fx-padding:10 20;-fx-background-radius:10;");
                panel.getChildren().add(lp);

                // ── Estudiantes en riesgo ──
                Label lr = new Label("⚠  Estudiantes en riesgo de reprobar:");
                lr.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#f39c12;-fx-padding:8 0 4 0;");
                panel.getChildren().add(lr);

                if (riesgos.isEmpty()) {
                    Label ok = new Label("   ✅  Ningún estudiante en riesgo crítico por el momento.");
                    ok.setStyle("-fx-text-fill:#27ae60;-fx-font-size:12px;");
                    panel.getChildren().add(ok);
                } else {
                    for (String[] r : riesgos) {
                        // r: {nombre, correo, curso, %asistencia, tipoAlerta, ausentes}
                        String colorAlerta = "CRÍTICO".equals(r[4]) ? "#e74c3c" : "#f39c12";
                        HBox fila = new HBox(10);
                        fila.setPadding(new Insets(8, 14, 8, 14));
                        fila.setStyle("-fx-background-color:" + colorAlerta + "18;"
                                + "-fx-border-color:" + colorAlerta + ";"
                                + "-fx-border-radius:8;-fx-background-radius:8;");
                        Label badge = new Label(r[4]);
                        badge.setStyle("-fx-text-fill:white;-fx-background-color:" + colorAlerta + ";"
                                + "-fx-padding:3 10;-fx-background-radius:20;-fx-font-size:10px;-fx-font-weight:bold;");
                        VBox info = new VBox(2);
                        Label lNombre = new Label(r[0]);
                        lNombre.setStyle("-fx-text-fill:#e8edf5;-fx-font-weight:bold;-fx-font-size:12px;");
                        Label lCurso = new Label(r[2] + "   •   Asistencia: " + r[3] + "   •   Ausencias: " + r[5]);
                        lCurso.setStyle("-fx-text-fill:#8a9bbf;-fx-font-size:11px;");
                        info.getChildren().addAll(lNombre, lCurso);
                        fila.getChildren().addAll(badge, info);
                        panel.getChildren().add(fila);
                    }
                }

                // ── Ranking más puntuales ──
                Label lrank = new Label("🏆  Ranking más puntuales:");
                lrank.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#4080ff;-fx-padding:12 0 4 0;");
                panel.getChildren().add(lrank);

                if (ranking.isEmpty()) {
                    Label noRank = new Label("   (Se necesitan al menos 2 clases registradas para mostrar el ranking)");
                    noRank.setStyle("-fx-text-fill:#4a5a78;-fx-font-size:11px;");
                    panel.getChildren().add(noRank);
                } else {
                    String[] medallas = {"🥇","🥈","🥉","④","⑤","⑥","⑦","⑧","⑨","⑩"};
                    for (String[] r : ranking) {
                        // r: {puesto, nombre, correo, foto, %asistencia, presentes}
                        int idx = Integer.parseInt(r[0]) - 1;
                        String medal = idx < medallas.length ? medallas[idx] : "#" + r[0];
                        HBox fila = new HBox(12);
                        fila.setPadding(new Insets(7, 14, 7, 14));
                        fila.setStyle("-fx-background-color:#0d1f3a;-fx-border-color:#1a3a6b;"
                                + "-fx-border-radius:8;-fx-background-radius:8;");
                        Label lMedal = new Label(medal);
                        lMedal.setStyle("-fx-font-size:18px;");
                        VBox info = new VBox(2);
                        Label lNombre = new Label(r[1]);
                        lNombre.setStyle("-fx-text-fill:#e8edf5;-fx-font-weight:bold;-fx-font-size:12px;");
                        Label lStats = new Label(r[2] + "   •   " + r[4] + " asistencia   •   " + r[5] + " presentes");
                        lStats.setStyle("-fx-text-fill:#4a6a9f;-fx-font-size:10px;");
                        info.getChildren().addAll(lNombre, lStats);
                        fila.getChildren().addAll(lMedal, info);
                        panel.getChildren().add(fila);
                    }
                }

                // ── Resumen por curso ──
                if (!porCurso.isEmpty()) {
                    Label lc = new Label("📚  Resumen por curso:");
                    lc.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#e8edf5;-fx-padding:12 0 4 0;");
                    panel.getChildren().add(lc);
                    for (String[] r : porCurso) {
                        // r: {nombre_curso, seccion, total, presentes, %}
                        HBox fila = new HBox(10);
                        fila.setPadding(new Insets(6, 14, 6, 14));
                        fila.setStyle("-fx-background-color:#0a1828;-fx-border-color:#1e2d47;"
                                + "-fx-border-radius:6;-fx-background-radius:6;");
                        Label lNom = new Label(r[0] + " [" + r[1] + "]");
                        lNom.setStyle("-fx-text-fill:#c8d8f0;-fx-font-size:12px;-fx-font-weight:bold;");
                        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
                        Label lPct = new Label(r[4] + " (" + r[3] + "/" + r[2] + ")");
                        lPct.setStyle("-fx-text-fill:#8a9bbf;-fx-font-size:11px;");
                        fila.getChildren().addAll(lNom, spacer, lPct);
                        panel.getChildren().add(fila);
                    }
                }

                // ── Predicción ausencias mañana ──
                if (!prediccion.isEmpty()) {
                    Label lpred = new Label("🔮  Riesgo de ausencia mañana (últimas 2 semanas):");
                    lpred.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#9b59b6;-fx-padding:12 0 4 0;");
                    panel.getChildren().add(lpred);
                    for (String[] r : prediccion) {
                        HBox fila = new HBox(10);
                        fila.setPadding(new Insets(6, 14, 6, 14));
                        fila.setStyle("-fx-background-color:#1a0a2a;-fx-border-color:#6c3483;"
                                + "-fx-border-radius:6;-fx-background-radius:6;");
                        Label lNom = new Label(r[0]);
                        lNom.setStyle("-fx-text-fill:#d7bde2;-fx-font-size:12px;");
                        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
                        Label lRiesg = new Label("Riesgo: " + r[2]);
                        lRiesg.setStyle("-fx-text-fill:#9b59b6;-fx-font-size:11px;-fx-font-weight:bold;");
                        fila.getChildren().addAll(lNom, spacer, lRiesg);
                        panel.getChildren().add(fila);
                    }
                }

                // FIX #4: reemplazar el panel de loading con el panel de datos ya construido
                sp.setContent(panel);
            });
        }, "analitica-bg") {{ setDaemon(true); }}.start();
    }

    // ── Intrusos ──
    private void cargarPantallaIntrusos() {
        if (lblTituloPantalla != null) lblTituloPantalla.setText("⚠ Intrusos");
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(20));
        int hoy = app.dao.IntrusoDAO.contarHoy();
        Label titulo = new Label("⚠ Caras No Reconocidas — " + hoy + " hoy");
        titulo.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:#e74c3c;");
        panel.getChildren().add(titulo);
        Button btnPDF = new Button("📄 Exportar PDF");
        btnPDF.setStyle("-fx-background-color:#c0392b;-fx-text-fill:white;-fx-font-weight:bold;-fx-padding:8 18;-fx-background-radius:8;");
        btnPDF.setOnAction(e -> {
            String ruta = app.servicios.PDFService.generarPDFIntrusos(app.dao.IntrusoDAO.listarRecientes(50));
            if (ruta != null) {
                new Alert(Alert.AlertType.INFORMATION, "PDF: " + ruta).showAndWait();
                try { java.awt.Desktop.getDesktop().open(new java.io.File(ruta)); } catch (Exception ignored) {}
            }
        });
        panel.getChildren().add(btnPDF);
        var intrusos = app.dao.IntrusoDAO.listarRecientes(30);
        if (intrusos.isEmpty()) {
            panel.getChildren().add(new Label("✅ Sin intentos de intrusión.") {{
                setStyle("-fx-text-fill:#27ae60;-fx-font-size:13px;");
            }});
        } else {
            javafx.scene.layout.FlowPane flow = new javafx.scene.layout.FlowPane(10, 10);
            for (String[] r : intrusos) {
                VBox card = new VBox(6);
                card.setStyle("-fx-background-color:#1a0a0a;-fx-border-color:#c0392b;-fx-border-radius:8;-fx-background-radius:8;-fx-padding:10;");
                card.setPrefWidth(160);
                try {
                    if (r[2] != null && new java.io.File(r[2]).exists()) {
                        ImageView iv = new ImageView(new Image(new java.io.File(r[2]).toURI().toString()));
                        iv.setFitWidth(140); iv.setFitHeight(100); iv.setPreserveRatio(true);
                        card.getChildren().add(iv);
                    }
                } catch (Exception ignored) {}
                card.getChildren().add(new Label("🕐 " + r[0]) {{ setStyle("-fx-text-fill:#e74c3c;-fx-font-size:10px;"); }});
                card.getChildren().add(new Label("📍 " + (r[1]!=null?r[1]:"?")) {{ setStyle("-fx-text-fill:#8a9bbf;-fx-font-size:10px;"); }});
                flow.getChildren().add(card);
            }
            panel.getChildren().add(flow);
        }
        ScrollPane sp = new ScrollPane(panel);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background:transparent;-fx-background-color:transparent;");
        contenedorPrincipal.getChildren().setAll(sp);
    }

    @FXML public void cerrarSesion() {
        if (reloj != null) reloj.shutdown();
        UsuarioDAO.logout();
        Main.navegarA("/fxml/Login.fxml", "Login", 900, 600);
    }

    public void shutdown() { if (reloj != null) reloj.shutdown(); }
}