package app.controladores;

import app.dao.AsistenciaDAO;
import app.dao.CursoDAO;
import app.dao.UsuarioDAO;
import app.modelos.Asistencia;
import app.modelos.Curso;
import app.modelos.Usuario;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;

import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * HistorialAsistenciasController
 *
 * Panel accesible desde HERRAMIENTAS → "Árbol de Asistencias" en el sidebar.
 * Muestra dos vistas del historial de un curso:
 *
 *  1. DETALLE DEL DÍA   — quién asistió, llegó tarde o faltó en una fecha específica.
 *  2. RESUMEN POR ESTUDIANTE — tabla con total de clases, presentes, tardanzas,
 *                              ausentes y % de asistencia para cada inscrito.
 *
 * La lista de fechas (panel izquierdo) se construye desde las asistencias
 * registradas en BD para ese curso — una entrada por cada fecha distinta.
 */
public class HistorialAsistenciasController implements Initializable {

    @FXML private ComboBox<Curso>   cmbCurso;
    @FXML private DatePicker        datePicker;
    @FXML private Label             lblTotalClases;
    @FXML private ProgressIndicator loading;
    @FXML private Label             lblEstado;

    // Panel izquierdo
    @FXML private VBox listaFechas;

    // Tabs
    @FXML private Button btnTabDia;
    @FXML private Button btnTabResumen;
    @FXML private VBox   tabDia;
    @FXML private VBox   tabResumen;

    // Tab Detalle del día
    @FXML private Label  lblFechaDia;
    @FXML private Label  lblStatsDia;
    @FXML private VBox   contenedorDia;

    // Tab Resumen
    @FXML private VBox   contenedorResumen;

    private static final DateTimeFormatter FMT_DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_DIA     = DateTimeFormatter.ofPattern("EEEE dd 'de' MMMM yyyy",
                                                              new Locale("es", "GT"));

    private LocalDate fechaSeleccionada = LocalDate.now();
    private boolean tabDiaActiva = true;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        datePicker.setValue(LocalDate.now());
        cargarComboCursos();
        cmbCurso.setOnAction(e -> recargarTodo());
    }

    // ── Llamado desde DashboardCatedraticoController para preseleccionar curso ──
    public void setCurso(Curso curso) {
        if (curso == null) return;
        cmbCurso.getItems().stream()
                .filter(c -> c.getIdCurso() == curso.getIdCurso())
                .findFirst()
                .ifPresent(c -> {
                    cmbCurso.setValue(c);
                    recargarTodo();
                });
    }

    private void cargarComboCursos() {
        var sesion = UsuarioDAO.getSesionActiva();
        List<Curso> cursos = (sesion != null && "Catedratico".equals(sesion.getNombreRol()))
                ? CursoDAO.listarPorCatedratico(sesion.getIdPersona())
                : CursoDAO.listarTodos();
        cmbCurso.getItems().setAll(cursos);
        if (!cursos.isEmpty()) {
            cmbCurso.setValue(cursos.get(0));
            recargarTodo();
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  RECARGA COMPLETA
    // ════════════════════════════════════════════════════════════════

    private void recargarTodo() {
        Curso c = cmbCurso.getValue();
        if (c == null) return;
        mostrarCargando(true);

        new Thread(() -> {
            // Obtener todas las fechas distintas con asistencias para este curso
            List<LocalDate> fechas    = AsistenciaDAO.obtenerFechasConAsistencia(c.getIdCurso());
            List<Usuario>   inscritos = CursoDAO.listarEstudiantesInscritos(c.getIdCurso());

            // Construir mapa: fecha → lista de asistencias
            Map<LocalDate, List<Asistencia>> porFecha = new LinkedHashMap<>();
            for (LocalDate f : fechas) {
                porFecha.put(f, AsistenciaDAO.obtenerAsistenciasCursoFecha(c.getIdCurso(), f));
            }

            Platform.runLater(() -> {
                mostrarCargando(false);
                lblTotalClases.setText(fechas.size() + " clase(s) registrada(s)");
                construirListaFechas(fechas, c);

                // Cargar la vista activa
                LocalDate target = fechas.contains(fechaSeleccionada)
                        ? fechaSeleccionada
                        : (fechas.isEmpty() ? LocalDate.now() : fechas.get(fechas.size() - 1));
                fechaSeleccionada = target;

                List<Asistencia> delDia = porFecha.getOrDefault(target, List.of());
                construirDetalleDia(delDia, target);
                construirResumenEstudiantes(inscritos, porFecha, fechas.size());
            });
        }, "historial-load") {{ setDaemon(true); }}.start();
    }

    // ════════════════════════════════════════════════════════════════
    //  PANEL IZQUIERDO — lista de fechas
    // ════════════════════════════════════════════════════════════════

    private void construirListaFechas(List<LocalDate> fechas, Curso curso) {
        listaFechas.getChildren().clear();

        if (fechas.isEmpty()) {
            Label lbl = new Label("Sin clases registradas aún.");
            lbl.setStyle("-fx-text-fill:#4a5a78;-fx-font-size:11px;-fx-padding:10;");
            listaFechas.getChildren().add(lbl);
            return;
        }

        // Ordenar descendente (más reciente primero)
        List<LocalDate> ordenadas = fechas.stream()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());

        for (LocalDate fecha : ordenadas) {
            boolean esHoy    = fecha.equals(LocalDate.now());
            boolean esActiva = fecha.equals(fechaSeleccionada);

            VBox celda = new VBox(2);
            celda.setPadding(new Insets(8, 12, 8, 12));
            celda.setCursor(javafx.scene.Cursor.HAND);
            celda.setStyle(esActiva
                    ? "-fx-background-color:rgba(79,195,247,0.18);-fx-background-radius:7;"
                    : "-fx-background-color:transparent;-fx-background-radius:7;");

            String diaNombre = fecha.getDayOfWeek()
                    .getDisplayName(TextStyle.FULL, new Locale("es"));
            diaNombre = Character.toUpperCase(diaNombre.charAt(0)) + diaNombre.substring(1);

            Label lblFecha = new Label(fecha.format(FMT_DISPLAY)
                    + (esHoy ? "  ★ Hoy" : ""));
            lblFecha.setStyle("-fx-text-fill:" + (esActiva ? "#4fc3f7" : "white")
                    + ";-fx-font-size:12px;-fx-font-weight:bold;");

            Label lblDia = new Label(diaNombre);
            lblDia.setStyle("-fx-text-fill:#8a9bbf;-fx-font-size:10px;");

            celda.getChildren().addAll(lblFecha, lblDia);

            // Hover
            celda.setOnMouseEntered(e -> {
                if (!fecha.equals(fechaSeleccionada))
                    celda.setStyle("-fx-background-color:rgba(79,195,247,0.08);-fx-background-radius:7;");
            });
            celda.setOnMouseExited(e -> {
                if (!fecha.equals(fechaSeleccionada))
                    celda.setStyle("-fx-background-color:transparent;-fx-background-radius:7;");
            });

            celda.setOnMouseClicked(e -> {
                fechaSeleccionada = fecha;
                datePicker.setValue(fecha);
                // Refrescar panel izquierdo (para mover highlight)
                Curso cur = cmbCurso.getValue();
                if (cur != null) {
                    mostrarCargando(true);
                    new Thread(() -> {
                        List<Asistencia> asist = AsistenciaDAO.obtenerAsistenciasCursoFecha(
                                cur.getIdCurso(), fecha);
                        Platform.runLater(() -> {
                            mostrarCargando(false);
                            List<LocalDate> fl = AsistenciaDAO.obtenerFechasConAsistencia(cur.getIdCurso());
                            construirListaFechas(fl, cur);
                            construirDetalleDia(asist, fecha);
                        });
                    }, "fecha-click") {{ setDaemon(true); }}.start();
                }
            });

            listaFechas.getChildren().add(celda);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  TAB 1 — DETALLE DEL DÍA
    // ════════════════════════════════════════════════════════════════

    @FXML
    public void verDia() {
        LocalDate fecha = datePicker.getValue();
        if (fecha == null) return;
        fechaSeleccionada = fecha;
        Curso c = cmbCurso.getValue();
        if (c == null) return;
        mostrarCargando(true);
        new Thread(() -> {
            List<Asistencia> lista = AsistenciaDAO.obtenerAsistenciasCursoFecha(c.getIdCurso(), fecha);
            List<LocalDate>  fl    = AsistenciaDAO.obtenerFechasConAsistencia(c.getIdCurso());
            Platform.runLater(() -> {
                mostrarCargando(false);
                construirListaFechas(fl, c);
                construirDetalleDia(lista, fecha);
                mostrarTabDia();
            });
        }, "ver-dia") {{ setDaemon(true); }}.start();
    }

    private void construirDetalleDia(List<Asistencia> lista, LocalDate fecha) {
        contenedorDia.getChildren().clear();
        String fechaStr = Character.toUpperCase(FMT_DIA.format(fecha).charAt(0))
                + FMT_DIA.format(fecha).substring(1);
        lblFechaDia.setText(fechaStr);

        if (lista.isEmpty()) {
            Label lbl = new Label("No hay registros de asistencia para esta fecha.");
            lbl.setStyle("-fx-text-fill:#4a5a78;-fx-font-size:12px;-fx-padding:20;");
            contenedorDia.getChildren().add(lbl);
            lblStatsDia.setText("");
            return;
        }

        long presentes = lista.stream().filter(Asistencia::isPresente).count();
        long tardanzas = lista.stream().filter(Asistencia::isTardanza).count();
        long ausentes  = lista.stream().filter(Asistencia::isAusente).count();
        lblStatsDia.setText("✅ " + presentes + "   ⏰ " + tardanzas + "   ❌ " + ausentes);

        // Encabezado de tabla
        HBox header = crearFilaTabla("Estudiante", "Carné", "Estado", "Hora ingreso", true);
        contenedorDia.getChildren().add(header);

        for (Asistencia a : lista) {
            String nombre = a.getPersona() != null
                    ? a.getPersona().getNombre() + " " + a.getPersona().getApellido()
                    : "ID:" + a.getIdEstudiante();
            String carne  = a.getPersona() != null && a.getPersona().getCarne() != null
                    ? a.getPersona().getCarne() : "—";
            String hora   = a.getHoraIngreso() != null && !a.getHoraIngreso().isBlank()
                    ? a.getHoraIngreso().substring(0, Math.min(5, a.getHoraIngreso().length()))
                    : "—";

            HBox fila = crearFilaTabla(nombre, carne, a.getEstado(), hora, false);

            // Colorear fila según estado
            String bg = a.isPresente() ? "rgba(39,174,96,0.07)"
                      : a.isTardanza() ? "rgba(243,156,18,0.08)"
                      : "rgba(231,76,60,0.07)";
            fila.setStyle("-fx-background-color:" + bg + ";-fx-background-radius:6;");
            contenedorDia.getChildren().add(fila);
        }
    }

    private HBox crearFilaTabla(String col1, String col2, String col3, String col4, boolean esHeader) {
        HBox fila = new HBox();
        fila.setPadding(new Insets(7, 12, 7, 12));
        fila.setAlignment(Pos.CENTER_LEFT);

        String styleBase = esHeader
                ? "-fx-text-fill:#4fc3f7;-fx-font-size:11px;-fx-font-weight:bold;"
                : "-fx-text-fill:white;-fx-font-size:12px;";

        Label l1 = new Label(col1);
        l1.setPrefWidth(240);
        l1.setStyle(styleBase);

        Label l2 = new Label(col2);
        l2.setPrefWidth(100);
        l2.setStyle(styleBase + "-fx-text-fill:#8a9bbf;");

        // Badge de estado
        Label l3;
        if (!esHeader) {
            l3 = new Label(estadoIcono(col3) + " " + col3);
            String color = colorEstado(col3);
            l3.setStyle("-fx-text-fill:" + color + ";-fx-font-size:11px;-fx-font-weight:bold;");
        } else {
            l3 = new Label(col3);
            l3.setStyle(styleBase);
        }
        l3.setPrefWidth(110);

        Label l4 = new Label(col4);
        l4.setPrefWidth(90);
        l4.setStyle(esHeader ? styleBase : "-fx-text-fill:#8a9bbf;-fx-font-size:11px;");

        fila.getChildren().addAll(l1, l2, l3, l4);
        if (esHeader) {
            fila.setStyle("-fx-background-color:#152035;-fx-background-radius:6;");
        }
        return fila;
    }

    // ════════════════════════════════════════════════════════════════
    //  TAB 2 — RESUMEN POR ESTUDIANTE
    // ════════════════════════════════════════════════════════════════

    private void construirResumenEstudiantes(List<Usuario> inscritos,
                                             Map<LocalDate, List<Asistencia>> porFecha,
                                             int totalClases) {
        contenedorResumen.getChildren().clear();

        if (inscritos.isEmpty()) {
            Label lbl = new Label("No hay estudiantes inscritos en este curso.");
            lbl.setStyle("-fx-text-fill:#4a5a78;-fx-font-size:12px;-fx-padding:20;");
            contenedorResumen.getChildren().add(lbl);
            return;
        }

        // Encabezado
        HBox header = crearFilaResumen("Estudiante", "Carné", "Presentes",
                "Tardanzas", "Ausentes", "Total", "Asistencia %", true);
        contenedorResumen.getChildren().add(header);

        // Calcular stats por estudiante
        for (Usuario u : inscritos) {
            int presentes = 0, tardanzas = 0, ausentes = 0;
            for (List<Asistencia> asist : porFecha.values()) {
                for (Asistencia a : asist) {
                    if (a.getIdEstudiante() == u.getIdUsuario()) {
                        if (a.isPresente())       presentes++;
                        else if (a.isTardanza())  tardanzas++;
                        else if (a.isAusente())   ausentes++;
                    }
                }
            }
            int efectivas = presentes + tardanzas; // tardanza cuenta como asistencia
            double pct = totalClases > 0 ? (efectivas * 100.0 / totalClases) : 0;

            HBox fila = crearFilaResumen(
                    u.getNombreCompleto(),
                    u.getCarne() != null ? u.getCarne() : "—",
                    String.valueOf(presentes),
                    String.valueOf(tardanzas),
                    String.valueOf(ausentes),
                    String.valueOf(totalClases),
                    String.format("%.1f%%", pct),
                    false
            );

            // Barra de progreso embebida en la fila
            ProgressBar bar = new ProgressBar(pct / 100.0);
            bar.setPrefWidth(80);
            bar.setPrefHeight(6);
            String barColor = pct >= 80 ? "#27ae60" : pct >= 60 ? "#f39c12" : "#e74c3c";
            bar.setStyle("-fx-accent:" + barColor + ";");

            // Agregar barra al final de la fila
            fila.getChildren().add(bar);

            // Color de fondo según % asistencia
            String bg = pct >= 80 ? "rgba(39,174,96,0.06)"
                      : pct >= 60 ? "rgba(243,156,18,0.07)"
                      : "rgba(231,76,60,0.06)";
            fila.setStyle("-fx-background-color:" + bg + ";-fx-background-radius:6;");

            contenedorResumen.getChildren().add(fila);
        }
    }

    private HBox crearFilaResumen(String nombre, String carne, String pres,
                                  String tard, String aus, String total,
                                  String pct, boolean esHeader) {
        HBox fila = new HBox(0);
        fila.setPadding(new Insets(8, 12, 8, 12));
        fila.setAlignment(Pos.CENTER_LEFT);

        String st = esHeader
                ? "-fx-text-fill:#4fc3f7;-fx-font-size:11px;-fx-font-weight:bold;"
                : "-fx-text-fill:white;-fx-font-size:12px;";
        String stNum = esHeader ? st : "-fx-text-fill:#cbd5e1;-fx-font-size:12px;";

        Label lNombre = new Label(nombre); lNombre.setPrefWidth(210); lNombre.setStyle(st);
        Label lCarne  = new Label(carne);  lCarne.setPrefWidth(90);   lCarne.setStyle(esHeader ? st : "-fx-text-fill:#8a9bbf;-fx-font-size:11px;");
        Label lPres   = new Label(pres);   lPres.setPrefWidth(75);    lPres.setStyle(esHeader ? st : "-fx-text-fill:#27ae60;-fx-font-weight:bold;");
        Label lTard   = new Label(tard);   lTard.setPrefWidth(75);    lTard.setStyle(esHeader ? st : "-fx-text-fill:#f39c12;-fx-font-weight:bold;");
        Label lAus    = new Label(aus);    lAus.setPrefWidth(75);     lAus.setStyle(esHeader ? st : "-fx-text-fill:#e74c3c;-fx-font-weight:bold;");
        Label lTotal  = new Label(total);  lTotal.setPrefWidth(55);   lTotal.setStyle(stNum);
        Label lPct    = new Label(pct);    lPct.setPrefWidth(70);

        if (!esHeader) {
            double val = 0;
            try { val = Double.parseDouble(pct.replace("%", "")); } catch (Exception ignored) {}
            String c = val >= 80 ? "#27ae60" : val >= 60 ? "#f39c12" : "#e74c3c";
            lPct.setStyle("-fx-text-fill:" + c + ";-fx-font-weight:bold;-fx-font-size:12px;");
        } else {
            lPct.setStyle(st);
        }

        fila.getChildren().addAll(lNombre, lCarne, lPres, lTard, lAus, lTotal, lPct);

        if (esHeader) fila.setStyle("-fx-background-color:#152035;-fx-background-radius:6;");
        return fila;
    }

    // ════════════════════════════════════════════════════════════════
    //  TABS — toggle entre Detalle del día y Resumen
    // ════════════════════════════════════════════════════════════════

    @FXML
    public void mostrarTabDia() {
        tabDiaActiva = true;
        tabDia.setVisible(true);    tabDia.setManaged(true);
        tabResumen.setVisible(false); tabResumen.setManaged(false);
        btnTabDia.setStyle(
                "-fx-background-color:rgba(79,195,247,0.15);" +
                "-fx-border-color:transparent transparent #4fc3f7 transparent;" +
                "-fx-border-width:0 0 2 0;-fx-text-fill:#4fc3f7;-fx-font-weight:bold;" +
                "-fx-padding:11 20;-fx-background-radius:0;-fx-cursor:hand;");
        btnTabResumen.setStyle(
                "-fx-background-color:transparent;-fx-border-color:transparent;" +
                "-fx-text-fill:#8a9bbf;-fx-font-size:12px;" +
                "-fx-padding:11 20;-fx-background-radius:0;-fx-cursor:hand;");
    }

    @FXML
    public void mostrarTabResumen() {
        tabDiaActiva = false;
        tabDia.setVisible(false);    tabDia.setManaged(false);
        tabResumen.setVisible(true); tabResumen.setManaged(true);
        btnTabResumen.setStyle(
                "-fx-background-color:rgba(79,195,247,0.15);" +
                "-fx-border-color:transparent transparent #4fc3f7 transparent;" +
                "-fx-border-width:0 0 2 0;-fx-text-fill:#4fc3f7;-fx-font-weight:bold;" +
                "-fx-padding:11 20;-fx-background-radius:0;-fx-cursor:hand;");
        btnTabDia.setStyle(
                "-fx-background-color:transparent;-fx-border-color:transparent;" +
                "-fx-text-fill:#8a9bbf;-fx-font-size:12px;" +
                "-fx-padding:11 20;-fx-background-radius:0;-fx-cursor:hand;");
    }

    // ════════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════════

    private void mostrarCargando(boolean activo) {
        if (loading == null) return;
        loading.setVisible(activo);
        loading.setManaged(activo);
    }

    private String colorEstado(String estado) {
        return switch (estado) {
            case "PRESENTE"  -> "#27ae60";
            case "TARDANZA"  -> "#f39c12";
            case "AUSENTE"   -> "#e74c3c";
            default          -> "#8a9bbf";
        };
    }

    private String estadoIcono(String estado) {
        return switch (estado) {
            case "PRESENTE"  -> "✅";
            case "TARDANZA"  -> "⏰";
            case "AUSENTE"   -> "❌";
            default          -> "⏳";
        };
    }
}
