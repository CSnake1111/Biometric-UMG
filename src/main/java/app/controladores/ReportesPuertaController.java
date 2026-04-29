package app.controladores;

import app.conexion.Conexion;
import app.modelos.Instalacion;
import app.modelos.Puerta;
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
 * Controlador — Reportes por Puerta de Entrada
 * Proceso 5 — Dashboard de reportes
 */
public class ReportesPuertaController implements Initializable {

    @FXML private ComboBox<Instalacion> cmbInstalacion;
    @FXML private ComboBox<Puerta>      cmbPuerta;
    @FXML private DatePicker            dpFecha;
    @FXML private ComboBox<String>      cmbOrden;
    @FXML private VBox                  contenedorArbol;
    @FXML private Label                 lblTituloArbol;
    @FXML private Label                 lblTotalNodos;
    @FXML private ProgressIndicator     loadingReporte;
    @FXML private TabPane               tabsReporte;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        cmbOrden.getItems().addAll("Ascendente (más temprano primero)",
                "Descendente (más reciente primero)");
        cmbOrden.setValue("Ascendente (más temprano primero)");
        dpFecha.setValue(LocalDate.now());
        cargarInstalaciones();
        cmbInstalacion.setOnAction(e -> cargarPuertas());
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

    @FXML public void cargarPuertas() {
        cmbPuerta.getItems().clear();
        Instalacion inst = cmbInstalacion.getValue();
        if (inst == null) return;
        String sql = "SELECT * FROM puertas WHERE id_instalacion = ?";
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, inst.getIdInstalacion());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Puerta p = new Puerta();
                p.setIdPuerta(rs.getInt("id_puerta"));
                p.setNombre  (rs.getString("nombre"));
                cmbPuerta.getItems().add(p);
            }
            if (!cmbPuerta.getItems().isEmpty()) cmbPuerta.setValue(cmbPuerta.getItems().get(0));
        } catch (SQLException e) { System.err.println("⚠ " + e.getMessage()); }
    }

    // ══════════════════════════════════════════
    //  Reporte histórico (árbol por fechas)
    // ══════════════════════════════════════════
    @FXML public void generarReporteHistorico() {
        if (!validarSeleccion()) return;
        contenedorArbol.getChildren().clear();
        loadingReporte.setVisible(true);

        Instalacion inst  = cmbInstalacion.getValue();
        Puerta      puerta = cmbPuerta.getValue();
        lblTituloArbol.setText("📊 Histórico — " + inst.getNombre() + " / " + puerta.getNombre());

        new Thread(() -> {
            List<LocalDate> fechas = obtenerFechasConIngreso(puerta.getIdPuerta());
            Platform.runLater(() -> {
                loadingReporte.setVisible(false);
                construirArbolHistorico(inst, puerta, fechas);
                lblTotalNodos.setText(fechas.size() + " días con registros");
            });
        }, "bg-thread-10") {{ setDaemon(true); }}.start();
    }

    // ══════════════════════════════════════════
    //  Reporte por fecha específica
    // ══════════════════════════════════════════
    @FXML public void generarReporteFecha() {
        if (!validarSeleccion()) return;
        LocalDate fecha = dpFecha.getValue();
        if (fecha == null) {
            new Alert(Alert.AlertType.WARNING, "Selecciona una fecha").showAndWait();
            return;
        }
        contenedorArbol.getChildren().clear();
        loadingReporte.setVisible(true);

        Instalacion inst   = cmbInstalacion.getValue();
        Puerta      puerta = cmbPuerta.getValue();
        boolean asc = cmbOrden.getValue() != null &&
                cmbOrden.getValue().startsWith("Asc");

        lblTituloArbol.setText("📅 " + inst.getNombre() + " / " + puerta.getNombre()
                + " — " + fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        new Thread(() -> {
            List<String[]> ingresos = obtenerIngresosPorFecha(puerta.getIdPuerta(), fecha, asc);
            Platform.runLater(() -> {
                loadingReporte.setVisible(false);
                construirArbolFecha(inst, puerta, fecha, ingresos);
                lblTotalNodos.setText(ingresos.size() + " ingresos ese día");
            });
        }, "bg-thread-11") {{ setDaemon(true); }}.start();
    }

    // ══════════════════════════════════════════
    //  Construcción visual — árbol histórico
    //  Raíz → Fechas → (click → carga personas)
    // ══════════════════════════════════════════
    private void construirArbolHistorico(Instalacion inst, Puerta puerta, List<LocalDate> fechas) {
        // Nodo raíz
        contenedorArbol.getChildren().add(nodoRaiz(inst.getNombre() + " / " + puerta.getNombre(), "🏛"));

        if (fechas.isEmpty()) {
            Label lbl = new Label("No hay registros de ingreso para esta puerta");
            lbl.setStyle("-fx-text-fill: #4a5a78; -fx-font-size: 12px;");
            VBox.setMargin(lbl, new Insets(10, 0, 0, 40));
            contenedorArbol.getChildren().add(lbl);
            return;
        }

        for (LocalDate fecha : fechas) {
            HBox nodoFecha = nodoFecha(fecha, puerta.getIdPuerta());
            VBox.setMargin(nodoFecha, new Insets(4, 0, 4, 40));
            contenedorArbol.getChildren().add(nodoFecha);

            // ✅ CORRECCIÓN: insertar el VBox de subnodos justo después
            if (nodoFecha.getUserData() instanceof VBox subNodos) {
                VBox.setMargin(subNodos, new Insets(0, 0, 0, 80));
                contenedorArbol.getChildren().add(subNodos);
            }
        }
    }

    // ══════════════════════════════════════════
    //  Construcción visual — árbol por fecha
    //  Raíz → Puerta → Personas con hora
    // ══════════════════════════════════════════
    private void construirArbolFecha(Instalacion inst, Puerta puerta,
                                     LocalDate fecha, List<String[]> ingresos) {
        // Nodo raíz instalación
        contenedorArbol.getChildren().add(nodoRaiz(inst.getNombre(), "🏛"));

        // Nivel 1: puerta
        HBox nodoPuerta = nodoNivel1(puerta.getNombre(), "🚪",
                fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        VBox.setMargin(nodoPuerta, new Insets(4, 0, 4, 40));
        contenedorArbol.getChildren().add(nodoPuerta);

        if (ingresos.isEmpty()) {
            Label lbl = new Label("Sin ingresos en esta fecha");
            lbl.setStyle("-fx-text-fill: #4a5a78; -fx-font-size: 11px;");
            VBox.setMargin(lbl, new Insets(4, 0, 4, 80));
            contenedorArbol.getChildren().add(lbl);
            return;
        }

        // Nivel 2: personas
        for (String[] p : ingresos) {
            // p: {nombre, correo, foto, hora, tipoPersona, carne, carrera}
            HBox nodoPersona = nodoPersona(p[0], p[1], p[3], p[2], true, p[5], p[6]);
            VBox.setMargin(nodoPersona, new Insets(3, 0, 3, 80));
            contenedorArbol.getChildren().add(nodoPersona);
        }
    }

    // ──────────────────────── Helpers de nodos ────────────────────────

    private Label nodoRaiz(String texto, String icono) {
        Label lbl = new Label(icono + "  " + texto);
        lbl.setStyle("""
            -fx-background-color: #003366;
            -fx-text-fill: white;
            -fx-font-size: 13px;
            -fx-font-weight: bold;
            -fx-padding: 10 20 10 20;
            -fx-background-radius: 8;
            """);
        return lbl;
    }

    private HBox nodoNivel1(String nombre, String icono, String detalle) {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(8, 14, 8, 14));
        box.setStyle("""
            -fx-background-color: #0d3a6b;
            -fx-border-color: #1a4f8c;
            -fx-border-radius: 8;
            -fx-background-radius: 8;
            """);

        Label lbl = new Label(icono + "  " + nombre);
        lbl.setStyle("-fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold;");

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        Label det = new Label(detalle);
        det.setStyle("-fx-text-fill: #8a9bbf; -fx-font-size: 11px;");

        box.getChildren().addAll(lbl, spacer, det);
        return box;
    }

    private HBox nodoFecha(LocalDate fecha, int idPuerta) {
        String fechaStr = fecha.format(DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM 'de' yyyy"));
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(8, 14, 8, 14));
        box.setStyle("""
            -fx-background-color: #152035;
            -fx-border-color: #1e2d47;
            -fx-border-radius: 8;
            -fx-background-radius: 8;
            -fx-cursor: hand;
            """);
        box.setOnMouseEntered(e ->
                box.setStyle(box.getStyle().replace("#152035", "#1c2d4a")));
        box.setOnMouseExited(e ->
                box.setStyle(box.getStyle().replace("#1c2d4a", "#152035")));

        // Al hacer click, expandir/colapsar personas de ese día
        Label lblFecha = new Label("📅  " + fechaStr);
        lblFecha.setStyle("-fx-text-fill: #e8edf5; -fx-font-size: 12px;");

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        // Contar ingresos de ese día
        int count = contarIngresosPorFecha(idPuerta, fecha);
        Label lblCount = new Label(count + " ingresos");
        lblCount.setStyle("-fx-background-color: #1a4f8c; -fx-text-fill: white; " +
                "-fx-font-size: 10px; -fx-padding: 3 10 3 10; -fx-background-radius: 20;");

        Label flecha = new Label("▶");
        flecha.setStyle("-fx-text-fill: #4a5a78; -fx-font-size: 10px;");

        box.getChildren().addAll(lblFecha, spacer, lblCount, flecha);

        // Expandir al hacer click
        VBox subNodos = new VBox(4);
        subNodos.setVisible(false); subNodos.setManaged(false);
        VBox.setMargin(subNodos, new Insets(0, 0, 0, 40));

        box.setOnMouseClicked(e -> {
            if (subNodos.isVisible()) {
                subNodos.setVisible(false); subNodos.setManaged(false);
                flecha.setText("▶");
            } else {
                if (subNodos.getChildren().isEmpty()) {
                    cargarPersonasEnSubnodo(subNodos, idPuerta, fecha);
                }
                subNodos.setVisible(true); subNodos.setManaged(true);
                flecha.setText("▼");
            }
        });

        // Truco: insertar subNodos justo después de este nodo
        box.setUserData(subNodos);
        return box;
    }

    private void cargarPersonasEnSubnodo(VBox subNodos, int idPuerta, LocalDate fecha) {
        List<String[]> ingresos = obtenerIngresosPorFecha(idPuerta, fecha, true);
        for (String[] p : ingresos) {
            // p: {nombre, correo, foto, hora, tipoPersona, carne, carrera}
            HBox np = nodoPersona(p[0], p[1], p[3], p[2], true, p[5], p[6]);
            VBox.setMargin(np, new Insets(2, 0, 2, 0));
            subNodos.getChildren().add(np);
        }
        if (ingresos.isEmpty()) {
            Label lbl = new Label("Sin registros");
            lbl.setStyle("-fx-text-fill: #4a5a78; -fx-font-size: 11px;");
            subNodos.getChildren().add(lbl);
        }
    }

    private HBox nodoPersona(String nombre, String correo, String hora,
                             String rutaFoto, boolean presente) {
        return nodoPersona(nombre, correo, hora, rutaFoto, presente, null, null, null);
    }

    private HBox nodoPersona(String nombre, String correo, String hora,
                             String rutaFoto, boolean presente,
                             String carne, String carrera) {
        return nodoPersona(nombre, correo, hora, rutaFoto, presente, carne, carrera, null);
    }

    private HBox nodoPersona(String nombre, String correo, String hora,
                             String rutaFoto, boolean presente,
                             String carne, String carrera, String estado) {
        HBox box = new HBox(12);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(10, 14, 10, 14));
        box.setStyle("""
            -fx-background-color: rgba(39,174,96,0.10);
            -fx-border-color: #27ae60;
            -fx-border-radius: 10;
            -fx-background-radius: 10;
            -fx-border-width: 1;
            """);

        // Indicador
        Circle c = new Circle(5);
        c.setFill(Color.web("#27ae60"));

        // Foto — soporta rutas locales Y URLs de Supabase
        ImageView foto = new ImageView();
        foto.setFitWidth(48); foto.setFitHeight(48);
        foto.setPreserveRatio(true);
        foto.setClip(new javafx.scene.shape.Rectangle(48, 48));
        if (rutaFoto != null && !rutaFoto.isBlank()) {
            try {
                if (rutaFoto.startsWith("http://") || rutaFoto.startsWith("https://")) {
                    foto.setImage(new Image(rutaFoto, true));
                } else {
                    File f = new File(rutaFoto);
                    if (f.exists()) foto.setImage(new Image(f.toURI().toString()));
                }
            } catch (Exception ignored) {}
        }

        // Info: nombre + correo + carné + carrera
        VBox info = new VBox(2);
        Label lNombre = new Label(nombre);
        lNombre.setStyle("-fx-text-fill: #e8edf5; -fx-font-weight: bold; -fx-font-size: 12px;");
        Label lCorreo = new Label(correo != null ? correo : "");
        lCorreo.setStyle("-fx-text-fill: #4a5a78; -fx-font-size: 10px;");
        info.getChildren().addAll(lNombre, lCorreo);
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
        Label lHora = new Label(hora != null && !hora.isBlank()
                ? "⏱ " + hora.substring(0, Math.min(5, hora.length())) : "");
        lHora.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 11px; -fx-font-weight: bold;");
        rightBox.getChildren().add(lHora);
        if (estado != null && !estado.isBlank()) {
            String estColor = switch (estado) {
                case "PRESENTE"  -> "#27ae60";
                case "TARDANZA"  -> "#f39c12";
                case "AUSENTE"   -> "#e74c3c";
                default          -> "#8a9bbf";
            };
            String estIcon = switch (estado) {
                case "PRESENTE" -> "✅";
                case "TARDANZA" -> "⏰";
                case "AUSENTE"  -> "❌";
                default         -> "•";
            };
            Label lEst = new Label(estIcon + " " + estado);
            lEst.setStyle("-fx-text-fill: " + estColor + "; -fx-font-size: 10px; "
                    + "-fx-background-color: " + estColor + "22; "
                    + "-fx-padding: 2 8; -fx-background-radius: 10;");
            rightBox.getChildren().add(lEst);
        }

        box.getChildren().addAll(c, foto, info, spacer, rightBox);
        return box;
    }

    // ══════════════════════════════════════════
    //  Consultas SQL
    // ══════════════════════════════════════════
    private List<LocalDate> obtenerFechasConIngreso(int idPuerta) {
        List<LocalDate> lista = new ArrayList<>();
        String sql = """
            SELECT DISTINCT CAST(fecha_hora AS DATE) AS fecha
            FROM registro_ingresos
            WHERE id_puerta = ? AND tipo_ingreso = 'PUERTA'
            ORDER BY fecha DESC
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idPuerta);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) lista.add(rs.getDate("fecha").toLocalDate());
        } catch (SQLException e) { System.err.println("⚠ " + e.getMessage()); }
        return lista;
    }

    private List<String[]> obtenerIngresosPorFecha(int idPuerta, LocalDate fecha, boolean asc) {
        List<String[]> lista = new ArrayList<>();
        String sql = """
            SELECT p.nombre || ' ' || p.apellido AS nombre,
                   p.correo, p.foto,
                   TO_CHAR(ri.fecha_hora, 'HH24:MI:SS') AS hora,
                   p.tipo_persona,
                   COALESCE(p.carne, '') AS carne,
                   COALESCE(p.carrera, '') AS carrera
            FROM registro_ingresos ri
            JOIN usuarios p ON ri.id_usuario = p.id_usuario
            WHERE ri.id_puerta = ?
              AND CAST(ri.fecha_hora AS DATE) = ?
              AND ri.tipo_ingreso = 'PUERTA'
            ORDER BY ri.fecha_hora \
            """ + (asc ? "ASC" : "DESC");
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt (1, idPuerta);
            ps.setDate(2, Date.valueOf(fecha));
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                lista.add(new String[]{
                        rs.getString("nombre"), rs.getString("correo"),
                        rs.getString("foto"),   rs.getString("hora"),
                        rs.getString("tipo_persona"), rs.getString("carne"),
                        rs.getString("carrera")
                });
        } catch (SQLException e) { System.err.println("⚠ " + e.getMessage()); }
        return lista;
    }

    private int contarIngresosPorFecha(int idPuerta, LocalDate fecha) {
        String sql = """
            SELECT COUNT(*) FROM registro_ingresos
            WHERE id_puerta = ? AND CAST(fecha_hora AS DATE) = ? AND tipo_ingreso = 'PUERTA'
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idPuerta); ps.setDate(2, Date.valueOf(fecha));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { System.err.println("⚠ " + e.getMessage()); }
        return 0;
    }

    private boolean validarSeleccion() {
        if (cmbInstalacion.getValue() == null || cmbPuerta.getValue() == null) {
            new Alert(Alert.AlertType.WARNING, "Selecciona instalación y puerta").showAndWait();
            return false;
        }
        return true;
    }

    @FXML public void exportarPDF() {
        if (!validarSeleccion()) return;
        Puerta puerta = cmbPuerta.getValue();
        LocalDate fecha = dpFecha.getValue() != null ? dpFecha.getValue() : LocalDate.now();
        boolean asc = cmbOrden.getValue() == null || cmbOrden.getValue().startsWith("Asc");
        List<String[]> ingresos = obtenerIngresosPorFecha(puerta.getIdPuerta(), fecha, asc);
        // Reformatear para PDF: {nombre, correo, hora, tipo}
        List<String[]> dataPDF = new ArrayList<>();
        for (String[] r : ingresos) dataPDF.add(new String[]{r[0], r[1], r[3], "Puerta"});
        String ruta = app.servicios.PDFService.generarPDFIngresos(puerta.getNombre(), fecha, dataPDF, "Puerta");
        if (ruta != null) {
            new Alert(Alert.AlertType.INFORMATION, "PDF generado en:\n" + ruta).showAndWait();
            try { java.awt.Desktop.getDesktop().open(new java.io.File(ruta)); } catch (Exception ignored) {}
        } else {
            new Alert(Alert.AlertType.ERROR, "Error generando el PDF").showAndWait();
        }
    }
}