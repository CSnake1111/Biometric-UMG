package app.controladores;

import app.conexion.Conexion;
import app.dao.UsuarioDAO;
import app.dao.RestriccionDAO;
import app.modelos.Usuario;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.net.URL;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controlador — Módulo de Restricciones de Acceso
 * Proceso 5 del sistema BiometricUMG
 */
public class RestriccionesController implements Initializable {

    // ─── Buscar persona ───
    @FXML private TextField          txtBuscarPersona;
    @FXML private TableView<Usuario> tablaPersonas;
    @FXML private TableColumn<Usuario, String> colNombreP;
    @FXML private TableColumn<Usuario, String> colTipoP;
    @FXML private TableColumn<Usuario, String> colCorreoP;

    // ─── Formulario restricción ───
    @FXML private Label       lblPersonaSeleccionada;
    @FXML private TextArea    txtMotivo;
    @FXML private DatePicker  dpFechaInicio;
    @FXML private DatePicker  dpFechaFin;
    @FXML private Button      btnAgregar;
    @FXML private Label       lblMensajeForm;

    // ─── Tabla restricciones activas ───
    @FXML private TableView<String[]>           tablaRestricciones;
    @FXML private TableColumn<String[], String> colPersonaR;
    @FXML private TableColumn<String[], String> colMotivoR;
    @FXML private TableColumn<String[], String> colInicioR;
    @FXML private TableColumn<String[], String> colFinR;
    @FXML private TableColumn<String[], String> colEstadoR;

    private Usuario personaSeleccionada = null;
    private final ObservableList<Usuario>   listaPersonas      = FXCollections.observableArrayList();
    private final ObservableList<String[]>  listaRestricciones = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        configurarTablaPersonas();
        configurarTablaRestricciones();
        cargarPersonas("");
        cargarRestricciones();
        dpFechaInicio.setValue(LocalDate.now());
        dpFechaFin.setValue(LocalDate.now().plusDays(7));

        txtBuscarPersona.textProperty().addListener((o, old, nv) -> cargarPersonas(nv));
        tablaPersonas.getSelectionModel().selectedItemProperty().addListener(
                (o, old, nv) -> seleccionarPersona(nv));
    }

    // ══════════════════════════════════════════
    //  Tabla personas
    // ══════════════════════════════════════════
    private void configurarTablaPersonas() {
        colNombreP.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getNombreCompleto()));
        colTipoP.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getTipoPersona()));
        colCorreoP.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getCorreo()));
        tablaPersonas.setItems(listaPersonas);
    }

    private void cargarPersonas(String filtro) {
        List<Usuario> lista = filtro.isEmpty()
                ? UsuarioDAO.listarTodos()
                : UsuarioDAO.buscar(filtro);
        listaPersonas.setAll(lista);
    }

    private void seleccionarPersona(Usuario p) {
        personaSeleccionada = p;
        if (p != null) {
            lblPersonaSeleccionada.setText("👤 " + p.getNombreCompleto()
                    + " — " + p.getTipoPersona());
            lblPersonaSeleccionada.setStyle(
                    "-fx-text-fill: #4080ff; -fx-font-weight: bold; -fx-font-size: 13px;");
            btnAgregar.setDisable(false);

            // Verificar si ya tiene restricción activa
            if (RestriccionDAO.tieneRestriccionActiva(p.getIdUsuario())) {
                mostrarMensajeForm("⚠ Esta persona ya tiene una restricción activa", false);
            } else {
                lblMensajeForm.setText("");
            }
        }
    }

    // ══════════════════════════════════════════
    //  Tabla restricciones activas
    // ══════════════════════════════════════════
    private void configurarTablaRestricciones() {
        colPersonaR.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[0]));
        colMotivoR .setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[1]));
        colInicioR .setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[2]));
        colFinR    .setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[3]));
        colEstadoR .setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[4]));

        // Colorear filas según estado
        tablaRestricciones.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(String[] item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else if ("ACTIVA".equals(item[4])) {
                    setStyle("-fx-background-color: rgba(231,76,60,0.12);");
                } else {
                    setStyle("-fx-background-color: rgba(39,174,96,0.08);");
                }
            }
        });

        tablaRestricciones.setItems(listaRestricciones);
    }

    private void cargarRestricciones() {
        List<String[]> lista = new ArrayList<>();
        String sql = """
            SELECT r.id_restriccion,
                   p.nombre || ' ' || p.apellido AS persona,
                   r.motivo,
                   TO_CHAR(r.fecha_inicio, 'DD/MM/YYYY') AS inicio,
                   TO_CHAR(r.fecha_fin, 'DD/MM/YYYY') AS fin,
                   CASE
                       WHEN r.fecha_fin >= CURRENT_DATE
                        AND r.fecha_inicio <= CURRENT_DATE
                       THEN 'ACTIVA'
                       WHEN r.fecha_fin < CURRENT_DATE
                       THEN 'VENCIDA'
                       ELSE 'FUTURA'
                   END AS estado
            FROM restricciones r
            JOIN usuarios p ON r.id_usuario = p.id_usuario
            ORDER BY r.fecha_fin DESC
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(new String[]{
                        rs.getString("persona"),
                        rs.getString("motivo"),
                        rs.getString("inicio"),
                        rs.getString("fin"),
                        rs.getString("estado")
                });
            }
        } catch (SQLException e) {
            System.err.println("⚠ Error cargando restricciones: " + e.getMessage());
        }
        listaRestricciones.setAll(lista);
    }

    // ══════════════════════════════════════════
    //  Agregar restricción
    // ══════════════════════════════════════════
    @FXML
    public void agregarRestriccion() {
        if (personaSeleccionada == null) {
            mostrarMensajeForm("⚠ Selecciona una persona de la tabla", false);
            return;
        }
        if (txtMotivo.getText().trim().isEmpty()) {
            mostrarMensajeForm("⚠ El motivo es obligatorio", false);
            return;
        }
        if (dpFechaInicio.getValue() == null || dpFechaFin.getValue() == null) {
            mostrarMensajeForm("⚠ Define las fechas de inicio y fin", false);
            return;
        }
        if (dpFechaFin.getValue().isBefore(dpFechaInicio.getValue())) {
            mostrarMensajeForm("⚠ La fecha de fin debe ser posterior al inicio", false);
            return;
        }

        boolean ok = RestriccionDAO.agregar(
                personaSeleccionada.getIdUsuario(),
                txtMotivo.getText().trim(),
                dpFechaInicio.getValue(),
                dpFechaFin.getValue()
        );

        if (ok) {
            mostrarMensajeForm("✅ Restricción agregada para " +
                    personaSeleccionada.getNombreCompleto(), true);
            txtMotivo.clear();
            cargarRestricciones();
            tablaPersonas.getSelectionModel().clearSelection();
            personaSeleccionada = null;
            lblPersonaSeleccionada.setText("Ninguna persona seleccionada");
            lblPersonaSeleccionada.setStyle("-fx-text-fill: #4a5a78; -fx-font-size: 12px;");
            btnAgregar.setDisable(true);
        } else {
            mostrarMensajeForm("❌ Error al agregar restricción", false);
        }
    }

    // ══════════════════════════════════════════
    //  Eliminar restricción seleccionada
    // ══════════════════════════════════════════
    @FXML
    public void eliminarRestriccion() {
        String[] seleccion = tablaRestricciones.getSelectionModel().getSelectedItem();
        if (seleccion == null) {
            mostrarMensajeForm("⚠ Selecciona una restricción de la tabla inferior", false);
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "¿Eliminar la restricción de " + seleccion[0] + "?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirmar eliminación");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(r -> {
            if (r == ButtonType.YES) {
                // Obtener ID de la restricción
                eliminarRestriccionBD(seleccion[0]);
                cargarRestricciones();
                mostrarMensajeForm("✅ Restricción eliminada correctamente", true);
            }
        });
    }

    private void eliminarRestriccionBD(String nombrePersona) {
        String sql = """
            DELETE FROM restricciones
            WHERE id_restriccion = (
                SELECT r.id_restriccion FROM restricciones r
                JOIN usuarios u ON r.id_usuario = u.id_usuario
                WHERE u.nombre || ' ' || u.apellido = ?
                LIMIT 1
            )
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, nombrePersona);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("⚠ Error eliminando: " + e.getMessage());
        }
    }

    @FXML public void actualizarLista() { cargarRestricciones(); cargarPersonas(""); }

    private void mostrarMensajeForm(String msg, boolean exito) {
        lblMensajeForm.setText(msg);
        lblMensajeForm.setStyle(exito
                ? "-fx-text-fill: #27ae60; -fx-font-size: 12px;"
                : "-fx-text-fill: #e74c3c; -fx-font-size: 12px;");
    }
}