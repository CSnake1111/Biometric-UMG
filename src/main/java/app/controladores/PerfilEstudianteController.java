package app.controladores;

import app.Main;
import app.conexion.Conexion;
import app.dao.UsuarioDAO;
import app.modelos.Usuario;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

import java.awt.Desktop;
import java.io.File;
import java.net.URL;
import java.sql.*;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

public class PerfilEstudianteController implements Initializable {

    // ── Header ──────────────────────────────────────────────────────
    @FXML private ImageView imgFoto;
    @FXML private ImageView imgQR;
    @FXML private Label     lblNombre;
    @FXML private Label     lblCarne;
    @FXML private Label     lblCorreo;
    @FXML private Label     lblCarrera;
    @FXML private Label     lblSeccion;
    @FXML private Label     lblChipSeccion;
    @FXML private Label     lblChipAlerta;

    // ── Estadísticas ────────────────────────────────────────────────
    @FXML private Label lblPctGlobal;
    @FXML private Label lblClasesAsistidas;
    @FXML private Label lblTardanzas;
    @FXML private Label lblAusencias;

    // ── Listas ──────────────────────────────────────────────────────
    @FXML private VBox  listaCursosActuales;
    @FXML private VBox  listaCursosAnteriores;
    @FXML private VBox  listaUltimosRegistros;
    @FXML private Label lblSinCursosActuales;
    @FXML private Label lblSinCursosAnteriores;
    @FXML private Label lblSinRegistros;

    // ── Scroll ──────────────────────────────────────────────────────
    @FXML private ScrollPane scrollPane;
    @FXML private HBox       anchorCursos;
    @FXML private VBox       anchorHistorial;

    private int idUsuario = -1;
    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_DT    = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ════════════════════════════════════════════════════════════════
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        new Thread(() -> {
            Usuario sesion = UsuarioDAO.getSesionActiva();
            if (sesion == null) return;
            Usuario u = UsuarioDAO.buscarPorId(sesion.getIdUsuario());
            if (u == null) return;
            idUsuario = u.getIdUsuario();
            Platform.runLater(() -> mostrarPerfil(u));
        }, "perfil-thread") {{ setDaemon(true); }}.start();
    }

    // ════════════════════════════════════════════════════════════════
    //  CARGA Y DISPLAY
    // ════════════════════════════════════════════════════════════════

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

    private void mostrarPerfil(Usuario u) {
        lblNombre  .setText(u.getNombreCompleto());
        lblCarne   .setText(u.getCarne()   != null ? u.getCarne()   : "—");
        lblCorreo  .setText(u.getCorreo()  != null ? u.getCorreo()  : "—");
        lblCarrera .setText(u.getCarrera() != null ? u.getCarrera() : "—");
        lblSeccion .setText(u.getSeccion() != null ? u.getSeccion() : "—");

        // chip de sección
        if (lblChipSeccion != null && u.getSeccion() != null)
            lblChipSeccion.setText("Sección " + u.getSeccion());

        // Foto con clip circular
        Image fotoImg = cargarImagenFoto(u.getFoto());
        if (fotoImg != null) {
            imgFoto.setImage(fotoImg);
            imgFoto.setPreserveRatio(false);
            double r = Math.min(imgFoto.getFitWidth(), imgFoto.getFitHeight()) / 2;
            imgFoto.setClip(new Circle(r, r, r));
        }

        // QR
        String rutaQR = app.servicios.QRService.getRutaQR(u.getIdUsuario());
        if (rutaQR != null && new File(rutaQR).exists()) {
            try { imgQR.setImage(new Image(new File(rutaQR).toURI().toString())); }
            catch (Exception ignored) {}
        }

        // Cargar el resto en background
        new Thread(() -> {
            cargarEstadisticasGlobales(u.getIdUsuario());
            cargarCursos(u.getIdUsuario());
            cargarUltimosRegistros(u.getIdUsuario());
        }, "perfil-data") {{ setDaemon(true); }}.start();
    }

    // ════════════════════════════════════════════════════════════════
    //  ESTADÍSTICAS GLOBALES (4 tarjetas superiores)
    // ════════════════════════════════════════════════════════════════

    private void cargarEstadisticasGlobales(int idUsuario) {
        String sql = """
            SELECT
                COUNT(*)                                                   AS total,
                SUM(CASE WHEN estado='PRESENTE'  THEN 1 ELSE 0 END)       AS presentes,
                SUM(CASE WHEN estado='TARDANZA'  THEN 1 ELSE 0 END)       AS tardanzas,
                SUM(CASE WHEN estado='AUSENTE'   THEN 1 ELSE 0 END)       AS ausentes
            FROM asistencias
            WHERE id_estudiante = ?
              AND EXTRACT(YEAR FROM fecha) = EXTRACT(YEAR FROM CURRENT_DATE)
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int total     = rs.getInt("total");
                int presentes = rs.getInt("presentes");
                int tardanzas = rs.getInt("tardanzas");
                int ausentes  = rs.getInt("ausentes");
                int pct = total > 0 ? presentes * 100 / total : 0;

                Platform.runLater(() -> {
                    lblClasesAsistidas.setText(String.valueOf(presentes));
                    lblTardanzas.setText(String.valueOf(tardanzas));
                    lblAusencias.setText(String.valueOf(ausentes));

                    // color del porcentaje según umbral
                    String colorPct = pct >= 80 ? "#27ae60" : pct >= 60 ? "#f39c12" : "#e74c3c";
                    lblPctGlobal.setText(pct + "%");
                    lblPctGlobal.setStyle("-fx-text-fill:" + colorPct + ";-fx-font-size:22px;-fx-font-weight:bold;");

                    // chip de alerta si hay cursos en riesgo
                    if (pct < 80 && lblChipAlerta != null) {
                        lblChipAlerta.setText("⚠ Asistencia en riesgo");
                        lblChipAlerta.setVisible(true);
                        lblChipAlerta.setManaged(true);
                    }
                });
            }
        } catch (SQLException e) {
            System.err.println("⚠ estadísticasGlobales: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  CURSOS ACTUALES + ANTERIORES
    // ════════════════════════════════════════════════════════════════

    private void cargarCursos(int idUsuario) {
        int anio = java.time.LocalDate.now().getYear();

        // ── Actuales ────────────────────────────────────────────────
        String sqlAct = """
            SELECT c.nombre_curso, c.seccion,
                   u.nombre || ' ' || u.apellido AS catedratico,
                   s.nombre AS salon,
                   COUNT(a.id_asistencia)                                AS clases,
                   SUM(CASE WHEN a.estado='PRESENTE'  THEN 1 ELSE 0 END) AS presentes,
                   SUM(CASE WHEN a.estado='TARDANZA'  THEN 1 ELSE 0 END) AS tardanzas,
                   SUM(CASE WHEN a.estado='AUSENTE'   THEN 1 ELSE 0 END) AS ausentes
            FROM asistencias a
            JOIN cursos c   ON a.id_curso = c.id_curso
            LEFT JOIN usuarios u ON c.id_catedratico = u.id_usuario
            LEFT JOIN salones s  ON c.id_salon = s.id_salon
            WHERE a.id_estudiante = ? AND EXTRACT(YEAR FROM a.fecha) = ?
            GROUP BY c.nombre_curso, c.seccion, u.nombre, u.apellido, s.nombre
            ORDER BY c.nombre_curso
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sqlAct)) {
            ps.setInt(1, idUsuario); ps.setInt(2, anio);
            ResultSet rs = ps.executeQuery();
            boolean hay = false;
            while (rs.next()) {
                hay = true;
                int cl  = rs.getInt("clases");
                int pr  = rs.getInt("presentes");
                int td  = rs.getInt("tardanzas");
                int au  = rs.getInt("ausentes");
                int pct = cl > 0 ? pr * 100 / cl : 0;
                String salon     = rs.getString("salon");
                String catedra   = rs.getString("catedratico");
                String nombre    = rs.getString("nombre_curso");
                String seccion   = rs.getString("seccion");
                VBox card = crearCardCurso(nombre, seccion, catedra, salon, cl, pr, td, au, pct);
                Platform.runLater(() -> listaCursosActuales.getChildren().add(card));
            }
            if (!hay) Platform.runLater(() -> {
                if (lblSinCursosActuales != null) {
                    lblSinCursosActuales.setVisible(true);
                    lblSinCursosActuales.setManaged(true);
                }
            });
        } catch (SQLException e) {
            System.err.println("⚠ cursos actuales: " + e.getMessage());
        }

        // ── Anteriores ──────────────────────────────────────────────
        String sqlAnt = """
            SELECT c.nombre_curso, c.seccion,
                   EXTRACT(YEAR FROM a.fecha)::int                        AS anio,
                   COUNT(a.id_asistencia)                                 AS clases,
                   SUM(CASE WHEN a.estado='PRESENTE' THEN 1 ELSE 0 END)  AS presentes
            FROM asistencias a
            JOIN cursos c ON a.id_curso = c.id_curso
            WHERE a.id_estudiante = ? AND EXTRACT(YEAR FROM a.fecha) < ?
            GROUP BY c.nombre_curso, c.seccion, EXTRACT(YEAR FROM a.fecha)
            ORDER BY anio DESC, c.nombre_curso
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sqlAnt)) {
            ps.setInt(1, idUsuario); ps.setInt(2, anio);
            ResultSet rs = ps.executeQuery();
            boolean hay = false;
            while (rs.next()) {
                hay = true;
                int cl  = rs.getInt("clases");
                int pr  = rs.getInt("presentes");
                int pct = cl > 0 ? pr * 100 / cl : 0;
                String label = rs.getString("nombre_curso")
                        + " (" + rs.getInt("anio") + ")  — Sección " + rs.getString("seccion");
                VBox card = crearCardSimple(label, pr + "/" + cl + " clases  (" + pct + "%)", "#4a5a78");
                Platform.runLater(() -> listaCursosAnteriores.getChildren().add(card));
            }
            if (!hay) Platform.runLater(() -> {
                if (lblSinCursosAnteriores != null) {
                    lblSinCursosAnteriores.setVisible(true);
                    lblSinCursosAnteriores.setManaged(true);
                }
            });
        } catch (SQLException e) {
            System.err.println("⚠ cursos anteriores: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  ÚLTIMOS REGISTROS DE ASISTENCIA
    // ════════════════════════════════════════════════════════════════

    private void cargarUltimosRegistros(int idUsuario) {
        String sql = """
            SELECT a.fecha, a.estado, a.hora_ingreso, c.nombre_curso
            FROM asistencias a
            JOIN cursos c ON a.id_curso = c.id_curso
            WHERE a.id_estudiante = ?
            ORDER BY a.fecha DESC, a.id_asistencia DESC
            LIMIT 6
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            ResultSet rs = ps.executeQuery();
            boolean hay = false;
            while (rs.next()) {
                hay = true;
                String fecha   = rs.getDate("fecha").toLocalDate().format(FMT_FECHA);
                String estado  = rs.getString("estado");
                String hora    = rs.getString("hora_ingreso");
                String curso   = rs.getString("nombre_curso");
                HBox fila = crearFilaRegistro(fecha, hora, curso, estado);
                Platform.runLater(() -> listaUltimosRegistros.getChildren().add(fila));
            }
            if (!hay) Platform.runLater(() -> {
                if (lblSinRegistros != null) {
                    lblSinRegistros.setVisible(true);
                    lblSinRegistros.setManaged(true);
                }
            });
        } catch (SQLException e) {
            System.err.println("⚠ últimosRegistros: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  BUILDERS DE UI
    // ════════════════════════════════════════════════════════════════

    /**
     * Tarjeta de curso actual: nombre, catedrático, salón,
     * barra de progreso de asistencia y contadores.
     */
    private VBox crearCardCurso(String nombre, String seccion, String catedratico,
                                String salon, int clases, int presentes,
                                int tardanzas, int ausentes, int pct) {
        String colorPct = pct >= 80 ? "#27ae60" : pct >= 60 ? "#f39c12" : "#e74c3c";
        String borderColor = pct >= 80 ? "#1a3a2a" : pct >= 60 ? "#3a2a10" : "#3a1a1a";

        VBox card = new VBox(8);
        card.setStyle("-fx-background-color:#152035;-fx-border-color:" + borderColor + ";"
                + "-fx-border-width:1.5;-fx-border-radius:10;-fx-background-radius:10;"
                + "-fx-padding:12 14 12 14;");

        // Título + sección
        HBox titulo = new HBox(8);
        titulo.setAlignment(Pos.CENTER_LEFT);
        Label lblTitulo = new Label(nombre);
        lblTitulo.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#e8edf5;");
        Label lblSec = new Label("Sección " + seccion);
        lblSec.setStyle("-fx-background-color:#1a2a44;-fx-text-fill:#4fc3f7;"
                + "-fx-background-radius:12;-fx-padding:2 8;-fx-font-size:10px;");
        titulo.getChildren().addAll(lblTitulo, lblSec);

        // Catedrático y salón
        Label lblCat  = new Label("Catedrático: " + (catedratico != null ? catedratico : "—"));
        Label lblSalon = new Label("Salón: " + (salon != null ? salon : "—"));
        lblCat  .setStyle("-fx-text-fill:#8a9bbf;-fx-font-size:10px;");
        lblSalon.setStyle("-fx-text-fill:#8a9bbf;-fx-font-size:10px;");

        // Barra de progreso
        HBox barraRow = new HBox(10);
        barraRow.setAlignment(Pos.CENTER_LEFT);

        StackPane barraWrap = new StackPane();
        barraWrap.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(barraWrap, Priority.ALWAYS);

        // fondo gris
        Rectangle barraFondo = new Rectangle(0, 6);
        barraFondo.setArcWidth(6); barraFondo.setArcHeight(6);
        barraFondo.setFill(javafx.scene.paint.Color.web("#1e2d47"));
        barraFondo.widthProperty().bind(barraWrap.widthProperty());

        // relleno coloreado
        double fraccion = Math.min(pct / 100.0, 1.0);
        Rectangle barraFill = new Rectangle(0, 6);
        barraFill.setArcWidth(6); barraFill.setArcHeight(6);
        barraFill.setFill(javafx.scene.paint.Color.web(colorPct));
        barraFondo.widthProperty().addListener((obs, o, nv) ->
                barraFill.setWidth(nv.doubleValue() * fraccion));

        barraWrap.getChildren().addAll(barraFondo, barraFill);

        Label lblPct = new Label(pct + "%");
        lblPct.setStyle("-fx-text-fill:" + colorPct + ";-fx-font-size:11px;-fx-font-weight:bold;");
        lblPct.setMinWidth(32);

        barraRow.getChildren().addAll(barraWrap, lblPct);

        // Contadores P / T / A
        HBox contadores = new HBox(14);
        contadores.getChildren().addAll(
                miniContador("✅ " + presentes, "#27ae60", "Presentes"),
                miniContador("⏰ " + tardanzas, "#f39c12", "Tardanzas"),
                miniContador("❌ " + ausentes,  "#e74c3c", "Ausencias")
        );

        card.getChildren().addAll(titulo, lblCat, lblSalon, barraRow, contadores);
        return card;
    }

    private Label miniContador(String texto, String color, String tooltip) {
        Label l = new Label(texto);
        l.setStyle("-fx-text-fill:" + color + ";-fx-font-size:10px;");
        Tooltip.install(l, new Tooltip(tooltip));
        return l;
    }

    /** Tarjeta simple para historial de cursos anteriores */
    private VBox crearCardSimple(String titulo, String detalle, String colorDetalle) {
        VBox card = new VBox(4);
        card.setStyle("-fx-background-color:#152035;-fx-border-color:#1e2d47;"
                + "-fx-border-width:1;-fx-border-radius:8;-fx-background-radius:8;"
                + "-fx-padding:10 14 10 14;");
        Label t = new Label(titulo);
        t.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:#e8edf5;");
        Label d = new Label(detalle);
        d.setStyle("-fx-font-size:10px;-fx-text-fill:" + colorDetalle + ";");
        card.getChildren().addAll(t, d);
        return card;
    }

    /** Fila de un registro de asistencia reciente */
    private HBox crearFilaRegistro(String fecha, String hora, String curso, String estado) {
        String dotColor  = "PRESENTE".equals(estado) ? "#27ae60"
                : "TARDANZA".equals(estado) ? "#f39c12" : "#e74c3c";
        String badgeBg   = "PRESENTE".equals(estado) ? "#1a3a1a"
                : "TARDANZA".equals(estado) ? "#3a2a10" : "#3a1a1a";
        String badgeTxt  = "PRESENTE".equals(estado) ? "#27ae60"
                : "TARDANZA".equals(estado) ? "#f39c12" : "#e74c3c";
        String horaStr   = (hora != null && hora.length() >= 5) ? hora.substring(0, 5) : "";

        HBox fila = new HBox(10);
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.setStyle("-fx-padding:6 0;-fx-border-color:transparent transparent #1e2d47 transparent;"
                + "-fx-border-width:0 0 1 0;");

        // dot de color
        javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(4);
        dot.setFill(javafx.scene.paint.Color.web(dotColor));

        // fecha
        Label lblFecha = new Label(fecha + (horaStr.isEmpty() ? "" : "  " + horaStr));
        lblFecha.setStyle("-fx-text-fill:rgba(255,255,255,0.4);-fx-font-size:10px;");
        lblFecha.setMinWidth(110);

        // nombre curso
        Label lblCurso = new Label(curso);
        lblCurso.setStyle("-fx-text-fill:#c8d4e8;-fx-font-size:11px;");
        HBox.setHgrow(lblCurso, Priority.ALWAYS);

        // badge estado
        Label badge = new Label(estado);
        badge.setStyle("-fx-background-color:" + badgeBg + ";-fx-text-fill:" + badgeTxt + ";"
                + "-fx-background-radius:12;-fx-padding:2 8;-fx-font-size:9px;");

        fila.getChildren().addAll(dot, lblFecha, lblCurso, badge);
        return fila;
    }

    // ════════════════════════════════════════════════════════════════
    //  SCROLL HELPERS (botones sidebar)
    // ════════════════════════════════════════════════════════════════

    @FXML public void scrollResumen()  { scrollTo(0); }
    @FXML public void scrollCursos()   { scrollTo(anchorCursos   != null ? anchorCursos.getLayoutY()   : 0); }
    @FXML public void scrollHistorial(){ scrollTo(anchorHistorial != null ? anchorHistorial.getLayoutY() : 0); }

    private void scrollTo(double y) {
        if (scrollPane == null) return;
        double total = scrollPane.getContent().getBoundsInLocal().getHeight()
                - scrollPane.getViewportBounds().getHeight();
        if (total > 0) scrollPane.setVvalue(y / total);
    }

    // ════════════════════════════════════════════════════════════════
    //  ACCIONES
    // ════════════════════════════════════════════════════════════════

    @FXML public void verCarnet() {
        if (idUsuario == -1) return;
        String ruta = app.servicios.PDFService.getCarpetaPDF() + "carnet_" + idUsuario + ".pdf";
        if (new File(ruta).exists()) {
            try { Desktop.getDesktop().open(new File(ruta)); }
            catch (Exception e) { System.err.println("⚠ " + e.getMessage()); }
        }
    }

    @FXML public void cerrarSesion() {
        UsuarioDAO.logout();
        Main.navegarA("/fxml/Login.fxml", "Login", 900, 600);
    }
}