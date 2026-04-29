package app.controladores;

import app.dao.AuditoriaDAO;
import app.dao.UsuarioDAO;
import app.modelos.Usuario;
import app.seguridad.Seguridad;
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
 * BiometricUMG 4.0 — CambiarPass
 * Acceso exclusivo para Administrador.
 * Permite cambiar la contraseña de cualquier usuario del sistema.
 */
public class CambiarPass implements Initializable {

    @FXML private ComboBox<String>             cmbUsuario;
    @FXML private PasswordField                txtNueva;
    @FXML private PasswordField                txtConfirmar;
    @FXML private Label                        lblFuerza;
    @FXML private Label                        lblMensaje;

    @FXML private TableView<String[]>          tablaHistorial;
    @FXML private TableColumn<String[],String> colHFecha;
    @FXML private TableColumn<String[],String> colHUsuario;
    @FXML private TableColumn<String[],String> colHAdmin;
    @FXML private TableColumn<String[],String> colHResultado;

    private final ObservableList<String[]> historial   = FXCollections.observableArrayList();
    // Mapa nombre_usuario → idUsuario
    private final java.util.Map<String, Integer> idPorNombre = new java.util.HashMap<>();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        Usuario sesion = UsuarioDAO.getSesionActiva();
        // FIX: usar equalsIgnoreCase y null-safe, igual que DashboardController.esAdmin()
        // El equals exacto bloqueaba al admin si getNombreRol() tenía variación de mayúsculas
        // o era null (cuando la sesión venía de buscarPorIdPersona con LEFT JOIN fallido)
        if (!esAdmin(sesion)) {
            mostrarMensaje("⛔ Acceso denegado. Solo el Administrador puede cambiar contraseñas.", false);
            deshabilitarFormulario();
            return;
        }

        configurarTabla();
        cargarUsuarios();
        cargarHistorial();

        txtNueva.textProperty().addListener((obs, old, nv) -> {
            if (nv == null || nv.isEmpty()) { lblFuerza.setText(""); return; }
            String err = Seguridad.validarContrasena(nv);
            if (err == null) {
                lblFuerza.setText("✅ Contraseña fuerte");
                lblFuerza.setStyle("-fx-text-fill:#27ae60;-fx-font-size:11px;");
            } else {
                lblFuerza.setText("⚠ " + err);
                lblFuerza.setStyle("-fx-text-fill:#e67e22;-fx-font-size:11px;");
            }
        });
    }

    // ══════════════════════════════════════════
    //  ACCIÓN PRINCIPAL
    // ══════════════════════════════════════════
    @FXML
    public void onCambiar() {
        String usuarioSeleccionado = cmbUsuario.getValue();
        String nueva               = txtNueva.getText();
        String confirmar           = txtConfirmar.getText();

        if (usuarioSeleccionado == null || usuarioSeleccionado.isBlank()) {
            mostrarMensaje("⚠ Selecciona un usuario.", false); return; }
        if (nueva.isEmpty()) {
            mostrarMensaje("⚠ Ingresa la nueva contraseña.", false); return; }
        if (!nueva.equals(confirmar)) {
            mostrarMensaje("⚠ Las contraseñas no coinciden.", false);
            txtConfirmar.clear(); return; }
        String err = Seguridad.validarContrasena(nueva);
        if (err != null) { mostrarMensaje("⚠ " + err, false); return; }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "¿Cambiar la contraseña del usuario '" + usuarioSeleccionado + "'?",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Confirmar cambio de contraseña");
        confirm.showAndWait().ifPresent(bt -> {
            if (bt != ButtonType.YES) return;

            new Thread(() -> {
                Integer idUsuario = idPorNombre.get(usuarioSeleccionado);
                boolean ok = idUsuario != null && UsuarioDAO.cambiarPassword(idUsuario, nueva);

                String admin = UsuarioDAO.getSesionActiva() != null
                        ? UsuarioDAO.getSesionActiva().getUsuario() : "admin";

                AuditoriaDAO.registrar(
                        "CAMBIO_PASSWORD", "GESTION_USUARIOS",
                        (ok ? "Contraseña cambiada exitosamente para: "
                                : "Fallo al cambiar contraseña de: ")
                                + usuarioSeleccionado + " (por " + admin + ")"
                );

                Platform.runLater(() -> {
                    if (ok) {
                        mostrarMensaje("✅ Contraseña de '" + usuarioSeleccionado + "' actualizada.", true);
                        txtNueva.clear(); txtConfirmar.clear();
                        lblFuerza.setText(""); cmbUsuario.setValue(null);
                    } else {
                        mostrarMensaje("❌ No se pudo cambiar la contraseña. Verifica los logs.", false);
                    }
                    cargarHistorial();
                });
            }, "cambio-password") {{ setDaemon(true); }}.start();
        });
    }

    @FXML
    public void onRefrescar() { cargarUsuarios(); cargarHistorial(); }

    // ══════════════════════════════════════════
    //  CARGA DE DATOS
    // ══════════════════════════════════════════

    /**
     * FIX: UsuarioDAO.listarTodos() retorna List<Usuario>, no List<String[]>.
     * Se itera sobre la lista de objetos Usuario para poblar el ComboBox.
     */
    private void cargarUsuarios() {
        new Thread(() -> {
            List<Usuario> todos = UsuarioDAO.listarTodos();
            java.util.Map<String, Integer> mapa = new java.util.HashMap<>();
            List<String> nombres = todos.stream()
                    .peek(u -> mapa.put(u.getUsuario(), u.getIdUsuario()))
                    .map(Usuario::getUsuario)
                    .filter(n -> n != null && !n.isBlank())
                    .sorted()
                    .toList();
            Platform.runLater(() -> {
                idPorNombre.clear();
                idPorNombre.putAll(mapa);
                String actual = cmbUsuario.getValue();
                cmbUsuario.setItems(FXCollections.observableArrayList(nombres));
                if (actual != null && nombres.contains(actual)) cmbUsuario.setValue(actual);
            });
        }, "cargar-usuarios") {{ setDaemon(true); }}.start();
    }

    private void cargarHistorial() {
        new Thread(() -> {
            List<app.modelos.AuditoriaAccion> acciones =
                    AuditoriaDAO.listarAcciones("GESTION_USUARIOS", null, 100);

            List<String[]> rows = acciones.stream()
                    .filter(a -> a.getDescripcion() != null
                            && a.getDescripcion().contains("Contraseña"))
                    .map(a -> new String[]{
                            a.getFechaHora() != null
                                    ? a.getFechaHora().format(
                                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
                                    : "—",
                            extraerUsuarioDescripcion(a.getDescripcion()),
                            a.getNombreUsuario() != null ? a.getNombreUsuario() : "—",
                            a.getResultado()     != null ? a.getResultado()     : "—"
                    })
                    .toList();

            Platform.runLater(() -> historial.setAll(rows));
        }, "cargar-historial") {{ setDaemon(true); }}.start();
    }

    private String extraerUsuarioDescripcion(String desc) {
        try {
            int ini = desc.indexOf("para: ");
            if (ini == -1) ini = desc.indexOf("de: ");
            if (ini == -1) return "—";
            ini = desc.indexOf(": ", ini) + 2;
            int fin = desc.indexOf(" (por", ini);
            return fin > ini ? desc.substring(ini, fin) : desc.substring(ini);
        } catch (Exception e) { return "—"; }
    }

    // ══════════════════════════════════════════
    //  UI
    // ══════════════════════════════════════════
    private void configurarTabla() {
        colHFecha    .setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[0]));
        colHUsuario  .setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[1]));
        colHAdmin    .setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[2]));
        colHResultado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[3]));

        colHResultado.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle("EXITOSO".equals(item)
                        ? "-fx-text-fill:#27ae60;-fx-font-weight:bold;"
                        : "-fx-text-fill:#e74c3c;-fx-font-weight:bold;");
            }
        });
        tablaHistorial.setItems(historial);
    }

    private void mostrarMensaje(String texto, boolean ok) {
        lblMensaje.setText(texto);
        lblMensaje.setStyle(ok
                ? "-fx-text-fill:#27ae60;-fx-font-weight:bold;-fx-font-size:12px;"
                : "-fx-text-fill:#e74c3c;-fx-font-weight:bold;-fx-font-size:12px;");
    }

    /** Tolerante a null, variantes de texto, y tipo_persona 'Administrativo' que podría
     *  haber quedado en la sesión si el campo nombre_rol no se resolvió correctamente. */
    private boolean esAdmin(Usuario u) {
        if (u == null) return false;
        // 1) por nombre de rol (caso normal)
        String rol = u.getNombreRol();
        if (rol != null && (rol.equalsIgnoreCase("Administrador") || rol.equalsIgnoreCase("Admin")))
            return true;
        // 2) por id_rol = 1 (Administrador en la BD)
        if (u.getIdRol() == 1) return true;
        // 3) fallback: re-consultar el rol desde BD por si la sesión vino sin JOIN
        if (u.getIdUsuario() > 0) {
            String rolBD = UsuarioDAO.resolverNombreRol(u.getIdRol());
            if (rolBD != null && rolBD.equalsIgnoreCase("Administrador")) return true;
        }
        return false;
    }

    private void deshabilitarFormulario() {
        cmbUsuario.setDisable(true);
        txtNueva.setDisable(true);
        txtConfirmar.setDisable(true);
    }
}