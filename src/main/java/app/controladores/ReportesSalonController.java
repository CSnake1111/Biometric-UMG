package app.controladores;

import app.conexion.Conexion;
import app.modelos.Instalacion;
import app.modelos.Salon;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.io.File;
import java.net.URL;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controlador — Reportes por Salón de Clase
 * Proceso 5 — Dashboard
 */
public class ReportesSalonController implements Initializable {

    @FXML private ComboBox<Instalacion> cmbInstalacion;
    @FXML private ComboBox<String>      cmbNivel;
    @FXML private ComboBox<Salon>       cmbSalon;
    @FXML private DatePicker            dpFecha;
    @FXML private VBox                  contenedorArbol;
    @FXML private Label                 lblTituloArbol;
    @FXML private Label                 lblTotalNodos;
    @FXML private ProgressIndicator     loadingReporte;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        dpFecha.setValue(LocalDate.now());
        cargarInstalaciones();
        cmbInstalacion.setOnAction(e -> cargarNiveles());
        cmbNivel.setOnAction(e -> cargarSalones());
    }

    private void cargarInstalaciones() {
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM instalaciones");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next())
                cmbInstalacion.getItems().add(
                        new Instalacion(rs.getInt("id_instalacion"), rs.getString("nombre")));
        } catch (SQLException e) { System.err.println("⚠ " + e.getMessage()); }
    }

    @FXML public void cargarNiveles() {
        cmbNivel.getItems().clear(); cmbSalon.getItems().clear();
        Instalacion inst = cmbInstalacion.getValue();
        if (inst == null) return;
        String sql = "SELECT DISTINCT nivel FROM salones WHERE id_instalacion = ? ORDER BY nivel";
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, inst.getIdInstalacion());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) cmbNivel.getItems().add(rs.getString("nivel"));
        } catch (SQLException e) { System.err.println("⚠ " + e.getMessage()); }
    }

    @FXML public void cargarSalones() {
        cmbSalon.getItems().clear();
        Instalacion inst = cmbInstalacion.getValue();
        String nivel = cmbNivel.getValue();
        if (inst == null || nivel == null) return;
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
                cmbSalon.getItems().add(s);
            }
        } catch (SQLException e) { System.err.println("⚠ " + e.getMessage()); }
    }

    // ══════════════════════════════════════════
    //  Árbol: Instalación → Nivel → Salón → Personas
    // ══════════════════════════════════════════
    @FXML public void generarReporteHistorico() {
        if (!validar()) return;
        contenedorArbol.getChildren().clear();
        loadingReporte.setVisible(true);
        Instalacion inst = cmbInstalacion.getValue();
        String      nivel = cmbNivel.getValue();
        Salon       salon = cmbSalon.getValue();
        lblTituloArbol.setText("🏛 " + inst.getNombre() + " / " + nivel + " / " + salon.getNombre() + " — Histórico");

        new Thread(() -> {
            List<String[]> personas = obtenerPersonasPorSalon(salon.getIdSalon(), null);
            Platform.runLater(() -> {
                loadingReporte.setVisible(false);
                construirArbol(inst, nivel, salon, personas, null);
                lblTotalNodos.setText(personas.size() + " personas registradas");
            });
        }, "bg-thread-12") {{ setDaemon(true); }}.start();
    }

    @FXML public void generarReporteFecha() {
        if (!validar()) return;
        LocalDate fecha = dpFecha.getValue();
        if (fecha == null) { new Alert(Alert.AlertType.WARNING, "Selecciona una fecha").showAndWait(); return; }
        contenedorArbol.getChildren().clear();
        loadingReporte.setVisible(true);
        Instalacion inst = cmbInstalacion.getValue();
        String      nivel = cmbNivel.getValue();
        Salon       salon = cmbSalon.getValue();
        lblTituloArbol.setText("📅 " + inst.getNombre() + " / " + salon.getNombre()
                + " — " + fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        new Thread(() -> {
            List<String[]> personas = obtenerPersonasPorSalon(salon.getIdSalon(), fecha);
            Platform.runLater(() -> {
                loadingReporte.setVisible(false);
                construirArbol(inst, nivel, salon, personas, fecha);
                lblTotalNodos.setText(personas.size() + " registros");
            });
        }, "bg-thread-13") {{ setDaemon(true); }}.start();
    }

    private void construirArbol(Instalacion inst, String nivel, Salon salon,
                                List<String[]> personas, LocalDate fecha) {
        // Nodo raíz: instalación
        contenedorArbol.getChildren().add(nodoTitulo(inst.getNombre(), "🏛", "#003366"));

        // Nivel 1: nivel del edificio
        HBox nNivel = nodoSeccion("📐 " + nivel, "#0d3a6b");
        VBox.setMargin(nNivel, new Insets(4, 0, 4, 30));
        contenedorArbol.getChildren().add(nNivel);

        // Nivel 2: salón
        String infoSalon = salon.getNombre()
                + (fecha != null ? " — " + fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : " — Histórico");
        HBox nSalon = nodoSeccion("🏫 " + infoSalon, "#152035");
        VBox.setMargin(nSalon, new Insets(4, 0, 4, 60));
        contenedorArbol.getChildren().add(nSalon);

        if (personas.isEmpty()) {
            Label lbl = new Label("Sin registros de ingreso en este salón");
            lbl.setStyle("-fx-text-fill: #4a5a78; -fx-font-size: 12px;");
            VBox.setMargin(lbl, new Insets(6, 0, 6, 90));
            contenedorArbol.getChildren().add(lbl);
            return;
        }

        // Nodos de personas
        for (String[] p : personas) {
            // p: {nombre, correo, foto, hora, tipoPersona, carne, carrera, estado}
            String estado = p.length > 7 ? p[7] : null;
            HBox np = nodoPersona(p[0], p[1], p[3], p[2], p[4], p[5], p[6], estado);
            VBox.setMargin(np, new Insets(3, 0, 3, 90));
            contenedorArbol.getChildren().add(np);
        }
    }

    // ─── Helpers visuales ───
    private Label nodoTitulo(String texto, String icono, String color) {
        Label l = new Label(icono + "  " + texto);
        l.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; " +
                "-fx-font-size: 13px; -fx-font-weight: bold; " +
                "-fx-padding: 10 20 10 20; -fx-background-radius: 8;");
        return l;
    }

    private HBox nodoSeccion(String texto, String bg) {
        HBox box = new HBox();
        box.setPadding(new Insets(8, 14, 8, 14));
        box.setStyle("-fx-background-color: " + bg + "; -fx-border-color: #1e2d47; " +
                "-fx-border-radius: 8; -fx-background-radius: 8;");
        Label l = new Label(texto);
        l.setStyle("-fx-text-fill: #e8edf5; -fx-font-size: 12px; -fx-font-weight: bold;");
        box.getChildren().add(l);
        return box;
    }

    private HBox nodoPersona(String nombre, String correo, String hora,
                             String foto, String tipo) {
        return nodoPersona(nombre, correo, hora, foto, tipo, null, null, null);
    }

    private HBox nodoPersona(String nombre, String correo, String hora,
                             String foto, String tipo, String carne, String carrera) {
        return nodoPersona(nombre, correo, hora, foto, tipo, carne, carrera, null);
    }

    private HBox nodoPersona(String nombre, String correo, String hora,
                             String foto, String tipo, String carne, String carrera, String estado) {
        HBox box = new HBox(12);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(10, 14, 10, 14));
        boolean esCatedratico = "Catedrático".equalsIgnoreCase(tipo) || "Catedratico".equalsIgnoreCase(tipo);
        String borde = esCatedratico ? "#f39c12" : "#27ae60";
        String fondo = esCatedratico ? "rgba(243,156,18,0.1)" : "rgba(39,174,96,0.1)";
        box.setStyle("-fx-background-color: " + fondo + "; -fx-border-color: " + borde + "; " +
                "-fx-border-radius: 10; -fx-background-radius: 10; -fx-border-width: 1;");

        Circle c = new Circle(5, Color.web(borde));

        // Foto — soporta rutas locales Y URLs de Supabase
        ImageView imgV = new ImageView();
        imgV.setFitWidth(48); imgV.setFitHeight(48); imgV.setPreserveRatio(true);
        imgV.setClip(new javafx.scene.shape.Rectangle(48, 48));
        if (foto != null && !foto.isBlank()) {
            try {
                if (foto.startsWith("http://") || foto.startsWith("https://")) {
                    imgV.setImage(new Image(foto, true));
                } else {
                    File f = new File(foto);
                    if (f.exists()) imgV.setImage(new Image(f.toURI().toString()));
                }
            } catch (Exception ignored) {}
        }

        VBox info = new VBox(2);
        Label ln = new Label(nombre);
        ln.setStyle("-fx-text-fill: #e8edf5; -fx-font-weight: bold; -fx-font-size: 12px;");
        Label lc = new Label((correo != null ? correo : "") + (esCatedratico ? "  [Catedrático]" : ""));
        lc.setStyle("-fx-text-fill: #4a5a78; -fx-font-size: 10px;");
        info.getChildren().addAll(ln, lc);
        if (carne != null && !carne.isBlank()) {
            Label lCarne = new Label("🪪 Carné: " + carne);
            lCarne.setStyle("-fx-text-fill: #8a9bbf; -fx-font-size: 10px;");
            info.getChildren().add(lCarne);
        }
        if (carrera != null && !carrera.isBlank()) {
            Label lCarrera = new Label("📚 " + carrera);
            lCarrera.setStyle("-fx-text-fill: #8a9bbf; -fx-font-size: 10px;");
            info.getChildren().add(lCarrera);
        }

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        VBox rightBox = new VBox(4);
        rightBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        Label lh = new Label(hora != null && !hora.isBlank() ? "⏱ " + hora.substring(0, Math.min(5, hora.length())) : "");
        lh.setStyle("-fx-text-fill: " + borde + "; -fx-font-size: 11px; -fx-font-weight: bold;");
        rightBox.getChildren().add(lh);
        if (estado != null && !estado.isBlank()) {
            String estColor = switch (estado) {
                case "PRESENTE"  -> "#27ae60";
                case "TARDANZA"  -> "#f39c12";
                case "AUSENTE"   -> "#e74c3c";
                default          -> "#8a9bbf";
            };
            String estIcon = switch (estado) {
                case "PRESENTE"  -> "✅";
                case "TARDANZA"  -> "⏰";
                case "AUSENTE"   -> "❌";
                default          -> "•";
            };
            Label lEst = new Label(estIcon + " " + estado);
            lEst.setStyle("-fx-text-fill: " + estColor + "; -fx-font-size: 10px; "
                    + "-fx-background-color: " + estColor + "22; "
                    + "-fx-padding: 2 8; -fx-background-radius: 10;");
            rightBox.getChildren().add(lEst);
        }

        box.getChildren().addAll(c, imgV, info, spacer, rightBox);
        return box;
    }

    // ══════════════════════════════════════════
    //  Consulta SQL — combina registro_ingresos Y asistencias
    // ══════════════════════════════════════════
    private List<String[]> obtenerPersonasPorSalon(int idSalon, LocalDate fecha) {
        List<String[]> lista = new ArrayList<>();
        String filtroFechaRI = fecha != null ? " AND CAST(ri.fecha_hora AS DATE) = ?" : "";
        String filtroFechaA  = fecha != null ? " AND a.fecha = ?" : "";

        // UNION: acceso físico por cámara de salón + asistencias confirmadas por catedrático/manual
        String sql =
                "SELECT " +
                        "    p.nombre || ' ' || p.apellido AS nombre, " +
                        "    p.correo, p.foto, " +
                        "    MAX(hora_origen) AS hora, " +
                        "    p.tipo_persona, " +
                        "    COALESCE(p.carne,   '') AS carne, " +
                        "    COALESCE(p.carrera, '') AS carrera, " +
                        "    MAX(estado_origen) AS estado " +
                        "FROM ( " +
                        "    SELECT ri.id_usuario, " +
                        "           TO_CHAR(ri.fecha_hora, 'HH24:MI:SS') AS hora_origen, " +
                        "           'PRESENTE' AS estado_origen " +
                        "    FROM registro_ingresos ri " +
                        "    WHERE ri.id_salon = ? AND ri.tipo_ingreso = 'SALON'" +
                        filtroFechaRI +
                        "    UNION " +
                        "    SELECT a.id_estudiante AS id_usuario, " +
                        "           COALESCE(a.hora_ingreso, '') AS hora_origen, " +
                        "           a.estado AS estado_origen " +
                        "    FROM asistencias a " +
                        "    JOIN cursos c ON a.id_curso = c.id_curso " +
                        "    WHERE c.id_salon = ?" +
                        filtroFechaA +
                        ") combinado " +
                        "JOIN usuarios p ON p.id_usuario = combinado.id_usuario " +
                        "GROUP BY p.id_usuario, p.nombre, p.apellido, p.correo, " +
                        "         p.foto, p.tipo_persona, p.carne, p.carrera " +
                        "ORDER BY p.tipo_persona DESC, p.apellido";

        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            int idx = 1;
            ps.setInt(idx++, idSalon);
            if (fecha != null) ps.setDate(idx++, Date.valueOf(fecha));
            ps.setInt(idx++, idSalon);
            if (fecha != null) ps.setDate(idx, Date.valueOf(fecha));
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                lista.add(new String[]{
                        rs.getString("nombre"),  rs.getString("correo"),
                        rs.getString("foto"),    rs.getString("hora"),
                        rs.getString("tipo_persona"), rs.getString("carne"),
                        rs.getString("carrera"), rs.getString("estado")
                });
        } catch (SQLException e) { System.err.println("⚠ obtenerPersonasPorSalon: " + e.getMessage()); }
        return lista;
    }

    private boolean validar() {
        if (cmbInstalacion.getValue() == null || cmbNivel.getValue() == null
                || cmbSalon.getValue() == null) {
            new Alert(Alert.AlertType.WARNING,
                    "Selecciona instalación, nivel y salón").showAndWait();
            return false;
        }
        return true;
    }
}