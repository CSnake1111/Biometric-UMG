package app.controladores;

import app.dao.AuditoriaDAO;
import app.dao.CursoDAO;
import app.dao.UsuarioDAO;
import app.modelos.Curso;
import app.modelos.Salon;
import app.modelos.Usuario;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * BiometricUMG 4.0 — GestionCursosController
 * Soporta crear, editar y eliminar cursos, e inscribir/remover estudiantes.
 */
public class GestionCursosController implements Initializable {

    /* ── Tabla cursos ── */
    @FXML private TableView<Curso>           tablaCursos;
    @FXML private TableColumn<Curso,String>  colNombreCurso;
    @FXML private TableColumn<Curso,String>  colSeccion;
    @FXML private TableColumn<Curso,String>  colHorario;
    @FXML private TableColumn<Curso,String>  colCatedratico;
    @FXML private TableColumn<Curso,String>  colSalon;

    /* ── Formulario ── */
    @FXML private Label              lblTituloForm;
    @FXML private TextField          txtNombreCurso;
    @FXML private TextField          txtSeccion;
    @FXML private ComboBox<String>   cmbDia;
    @FXML private TextField          txtHoraInicio;
    @FXML private TextField          txtHoraFin;
    @FXML private ComboBox<Usuario>  cmbCatedratico;
    @FXML private ComboBox<Salon>    cmbSalon;
    @FXML private Button             btnGuardarCurso;
    @FXML private Button             btnCancelarEdicion;
    @FXML private Label              lblMensajeCurso;

    /* ── Inscripción ── */
    @FXML private TableView<Usuario>          tablaInscritos;
    @FXML private TableColumn<Usuario,String> colNombreEst;
    @FXML private TableColumn<Usuario,String> colCarneEst;
    @FXML private ComboBox<Usuario>           cmbEstudiante;
    @FXML private Label                       lblMensajeInscripcion;

    private final ObservableList<Curso>   listaCursos    = FXCollections.observableArrayList();
    private final ObservableList<Usuario> listaInscritos = FXCollections.observableArrayList();

    // null = modo crear; non-null = modo editar
    private Curso cursoSeleccionado = null;
    private boolean modoEdicion = false;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        configurarTablaCursos();
        configurarTablaInscritos();
        cargarCombos();
        cargarCursos();

        // Al hacer clic en un curso: cargar inscritos Y entrar en modo edición
        tablaCursos.getSelectionModel().selectedItemProperty()
                .addListener((o, old, nv) -> {
                    if (nv != null) seleccionarCurso(nv);
                });
    }

    /* ─────────────────────── Configuración tablas ─────────────────────── */

    private void configurarTablaCursos() {
        colNombreCurso.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombreCurso()));
        colSeccion    .setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSeccion()));
        colHorario    .setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getHorario()));
        colCatedratico.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombreCatedratico()));
        colSalon      .setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombreSalon()));
        tablaCursos.setItems(listaCursos);
    }

    private void configurarTablaInscritos() {
        colNombreEst.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombreCompleto()));
        colCarneEst .setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getCarne() != null ? c.getValue().getCarne() : "—"));
        tablaInscritos.setItems(listaInscritos);
    }

    /* ─────────────────────── Combos ─────────────────────── */

    private void cargarCombos() {
        cmbDia.setItems(FXCollections.observableArrayList(
                "Lunes","Martes","Miércoles","Jueves","Viernes","Sábado","Domingo"));

        List<Usuario> cats = UsuarioDAO.listarPorTipo("Catedrático");
        cmbCatedratico.setItems(FXCollections.observableArrayList(cats));
        cmbCatedratico.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Usuario u, boolean empty) {
                super.updateItem(u, empty);
                setText(empty || u == null ? "" : u.getNombreCompleto());
            }
        });
        cmbCatedratico.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Usuario u, boolean empty) {
                super.updateItem(u, empty);
                setText(empty || u == null ? "" : u.getNombreCompleto());
            }
        });

        List<Salon> salones = CursoDAO.listarSalones();
        cmbSalon.setItems(FXCollections.observableArrayList(salones));
        cmbSalon.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Salon s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? "" : s.getNombre() + " — " + s.getNivel());
            }
        });
        cmbSalon.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Salon s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? "" : s.getNombre() + " — " + s.getNivel());
            }
        });

        List<Usuario> estudiantes = UsuarioDAO.listarPorTipo("Estudiante");
        cmbEstudiante.setItems(FXCollections.observableArrayList(estudiantes));
        cmbEstudiante.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Usuario u, boolean empty) {
                super.updateItem(u, empty);
                setText(empty || u == null ? ""
                        : (u.getCarne() != null ? u.getCarne() + " — " : "") + u.getNombreCompleto());
            }
        });
        cmbEstudiante.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Usuario u, boolean empty) {
                super.updateItem(u, empty);
                setText(empty || u == null ? ""
                        : (u.getCarne() != null ? u.getCarne() + " — " : "") + u.getNombreCompleto());
            }
        });
    }

    /* ─────────────────────── Carga de datos ─────────────────────── */

    private void cargarCursos() {
        new Thread(() -> {
            List<Curso> data = CursoDAO.listarTodos();
            Platform.runLater(() -> listaCursos.setAll(data));
        }, "load-cursos") {{ setDaemon(true); }}.start();
    }

    /* ─────────────────────── Selección de curso ─────────────────────── */

    private void seleccionarCurso(Curso c) {
        cursoSeleccionado = c;

        // Cargar inscritos en background
        new Thread(() -> {
            List<Usuario> inscritos = CursoDAO.listarEstudiantesInscritos(c.getIdCurso());
            Platform.runLater(() -> listaInscritos.setAll(inscritos));
        }, "load-inscritos") {{ setDaemon(true); }}.start();

        // Rellenar formulario con datos del curso seleccionado
        entrarModoEdicion(c);
    }

    private void entrarModoEdicion(Curso c) {
        modoEdicion = true;

        txtNombreCurso.setText(c.getNombreCurso() != null ? c.getNombreCurso() : "");
        txtSeccion    .setText(c.getSeccion()      != null ? c.getSeccion()      : "");
        txtHoraInicio .setText(c.getHoraInicio()   != null ? c.getHoraInicio()   : "");
        txtHoraFin    .setText(c.getHoraFin()       != null ? c.getHoraFin()      : "");
        cmbDia        .setValue(c.getDiaSemana());

        // Seleccionar catedrático en el combo por id
        cmbCatedratico.getItems().stream()
                .filter(u -> u.getIdUsuario() == c.getIdCatedratico())
                .findFirst().ifPresent(cmbCatedratico::setValue);

        // Seleccionar salón en el combo por id
        cmbSalon.getItems().stream()
                .filter(s -> s.getIdSalon() == c.getIdSalon())
                .findFirst().ifPresent(cmbSalon::setValue);

        // Cambiar título y botón
        lblTituloForm.setText("✏  Editar Curso");
        btnGuardarCurso.setText("💾  Actualizar Curso");
        btnCancelarEdicion.setVisible(true);
        btnCancelarEdicion.setManaged(true);
        lblMensajeCurso.setText("");
    }

    /* ─────────────────────── Acciones del formulario ─────────────────────── */

    @FXML public void cancelarEdicion() {
        modoEdicion = false;
        cursoSeleccionado = null;
        tablaCursos.getSelectionModel().clearSelection();
        limpiarFormCurso();
        listaInscritos.clear();
        lblMensajeCurso.setText("");
    }

    @FXML public void guardarCurso() {
        if (txtNombreCurso.getText().trim().isEmpty()) {
            msg(lblMensajeCurso, "⚠ El nombre del curso es obligatorio.", false); return; }
        if (cmbCatedratico.getValue() == null) {
            msg(lblMensajeCurso, "⚠ Selecciona un catedrático.", false); return; }
        if (cmbSalon.getValue() == null) {
            msg(lblMensajeCurso, "⚠ Selecciona un salón.", false); return; }

        Curso c = modoEdicion ? cursoSeleccionado : new Curso();
        c.setNombreCurso  (txtNombreCurso.getText().trim());
        c.setSeccion      (txtSeccion.getText().trim());
        c.setDiaSemana    (cmbDia.getValue());
        c.setHoraInicio   (txtHoraInicio.getText().trim());
        c.setHoraFin      (txtHoraFin.getText().trim());
        c.setIdCatedratico(cmbCatedratico.getValue().getIdUsuario());
        c.setIdSalon      (cmbSalon.getValue().getIdSalon());

        if (modoEdicion) {
            // ── Actualizar curso existente ──
            Curso finalC = c;
            new Thread(() -> {
                boolean ok = CursoDAO.actualizar(finalC);
                if (ok) AuditoriaDAO.registrar("EDITAR_CURSO", "CURSOS",
                        "Curso actualizado: " + finalC.getNombreCurso() + " [" + finalC.getSeccion() + "]");
                Platform.runLater(() -> {
                    msg(lblMensajeCurso,
                            ok ? "✅ Curso actualizado correctamente." : "❌ Error al actualizar el curso.", ok);
                    if (ok) { cargarCursos(); }
                });
            }, "update-curso") {{ setDaemon(true); }}.start();

        } else {
            // ── Insertar nuevo curso ──
            Curso finalC = c;
            new Thread(() -> {
                int id = CursoDAO.insertar(finalC);
                boolean ok = id > 0;
                if (ok) AuditoriaDAO.registrar("CREAR_CURSO", "CURSOS",
                        "Curso creado: " + finalC.getNombreCurso() + " [" + finalC.getSeccion() + "]");
                Platform.runLater(() -> {
                    msg(lblMensajeCurso,
                            ok ? "✅ Curso guardado correctamente." : "❌ Error al guardar el curso.", ok);
                    if (ok) { limpiarFormCurso(); cargarCursos(); }
                });
            }, "save-curso") {{ setDaemon(true); }}.start();
        }
    }

    @FXML public void eliminarCurso() {
        if (cursoSeleccionado == null) {
            msg(lblMensajeCurso, "⚠ Selecciona un curso de la tabla.", false); return; }
        Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                "¿Desactivar el curso: " + cursoSeleccionado.getNombreCurso() + "?",
                ButtonType.YES, ButtonType.NO);
        a.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) {
                CursoDAO.eliminar(cursoSeleccionado.getIdCurso());
                AuditoriaDAO.registrar("ELIMINAR_CURSO", "CURSOS",
                        "Curso desactivado: " + cursoSeleccionado.getNombreCurso());
                cancelarEdicion();
                cargarCursos();
            }
        });
    }

    /* ─────────────────────── Inscripción ─────────────────────── */

    @FXML public void inscribirEstudiante() {
        if (cursoSeleccionado == null) {
            msg(lblMensajeInscripcion, "⚠ Selecciona un curso primero.", false); return; }
        if (cmbEstudiante.getValue() == null) {
            msg(lblMensajeInscripcion, "⚠ Selecciona un estudiante.", false); return; }

        Usuario est = cmbEstudiante.getValue();
        new Thread(() -> {
            boolean ok = CursoDAO.inscribirEstudiante(
                    cursoSeleccionado.getIdCurso(), est.getIdUsuario());
            if (ok) AuditoriaDAO.registrar("INSCRIBIR_ESTUDIANTE", "CURSOS",
                    est.getNombreCompleto() + " inscrito en " + cursoSeleccionado.getNombreCurso());
            Platform.runLater(() -> {
                msg(lblMensajeInscripcion,
                        ok ? "✅ Estudiante inscrito." : "❌ No se pudo inscribir al estudiante.", ok);
                if (ok) seleccionarCurso(cursoSeleccionado);
            });
        }, "inscribir") {{ setDaemon(true); }}.start();
    }

    @FXML public void desinscribirEstudiante() {
        if (cursoSeleccionado == null || tablaInscritos.getSelectionModel().isEmpty()) {
            msg(lblMensajeInscripcion, "⚠ Selecciona un curso y un estudiante de la lista.", false); return; }

        Usuario est = tablaInscritos.getSelectionModel().getSelectedItem();
        new Thread(() -> {
            boolean ok = CursoDAO.desinscribirEstudiante(
                    cursoSeleccionado.getIdCurso(), est.getIdUsuario());
            if (ok) AuditoriaDAO.registrar("DESINSCRIBIR_ESTUDIANTE", "CURSOS",
                    est.getNombreCompleto() + " removido de " + cursoSeleccionado.getNombreCurso());
            Platform.runLater(() -> {
                msg(lblMensajeInscripcion,
                        ok ? "✅ Estudiante removido." : "❌ No se pudo remover al estudiante.", ok);
                if (ok) seleccionarCurso(cursoSeleccionado);
            });
        }, "desinscribir") {{ setDaemon(true); }}.start();
    }

    /* ─────────────────────── Utilidades ─────────────────────── */

    private void limpiarFormCurso() {
        modoEdicion = false;
        txtNombreCurso.clear(); txtSeccion.clear();
        txtHoraInicio.clear();  txtHoraFin.clear();
        cmbDia.setValue(null);
        cmbCatedratico.setValue(null);
        cmbSalon.setValue(null);
        lblTituloForm.setText("Nuevo Curso");
        btnGuardarCurso.setText("💾  Guardar Curso");
        btnCancelarEdicion.setVisible(false);
        btnCancelarEdicion.setManaged(false);
    }

    private void msg(Label lbl, String text, boolean ok) {
        lbl.setText(text);
        lbl.setStyle(ok ? "-fx-text-fill:#27ae60;-fx-font-weight:bold;"
                : "-fx-text-fill:#e74c3c;-fx-font-weight:bold;");
    }
}