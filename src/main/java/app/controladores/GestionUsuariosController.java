package app.controladores;

import app.dao.AuditoriaDAO;
import app.dao.UsuarioDAO;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * GestionUsuariosController — BiometricUMG 2.0
 *
 * Acciones disponibles por usuario:
 *   • Habilitar  — reactiva un usuario deshabilitado  (activo = 1)
 *   • Deshabilitar — desactiva un usuario             (activo = 0)
 *   • Desbloquear — limpia intentos fallidos y bloqueadoHasta
 *   • Resetear contraseña — genera nueva contraseña temporal y la muestra en diálogo
 *   • Cambiar rol
 *
 * El panel "Crear Usuario" se eliminó — los usuarios se crean desde
 * Registro de Personas (Proceso 1), donde se vinculan a una persona real.
 */
public class GestionUsuariosController implements Initializable {

    // ─── Tabla ───
    @FXML private TableView<String[]>          tablaUsuarios;
    @FXML private TableColumn<String[], String> colId, colUsuario, colNombreReal,
            colRol, colIntentos, colBloqueado,
            colUltimoLogin, colEstado;
    @FXML private TableColumn<String[], Void>  colAcciones;

    // ─── UI ───
    @FXML private TextField txtBuscar;
    @FXML private Label     lblMensaje;
    @FXML private Label     lblTotalCount;
    @FXML private Label     lblActivosCount;
    @FXML private Label     lblBloqueadosCount;

    private final ObservableList<String[]> listaCompleta = FXCollections.observableArrayList();
    private FilteredList<String[]>         listaFiltrada;

    // ══════════════════════════════════════════
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        configurarTabla();
        configurarBusqueda();
        cargarUsuarios();
    }

    // ══════════════════════════════════════════
    //  Tabla
    // ══════════════════════════════════════════
    private void configurarTabla() {

        // Columnas de datos simples
        colId          .setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[0]));
        colUsuario     .setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[1]));
        colNombreReal  .setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue()[8] != null ? c.getValue()[8] : "—"));
        colRol         .setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[2]));
        colIntentos    .setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[3]));
        colBloqueado   .setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue()[4] != null ? c.getValue()[4] : "—"));
        colUltimoLogin .setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue()[5] != null ? c.getValue()[5] : "—"));
        colEstado      .setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[6]));

        // Columna Estado — coloreada
        colEstado.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                switch (item) {
                    case "Activo"   -> setStyle("-fx-text-fill:#27ae60;-fx-font-weight:bold;");
                    case "Inactivo" -> setStyle("-fx-text-fill:#e74c3c;-fx-font-weight:bold;");
                    case "Bloqueado"-> setStyle("-fx-text-fill:#f39c12;-fx-font-weight:bold;");
                    default         -> setStyle("-fx-text-fill:#aaa;");
                }
            }
        });

        // Columna Acciones — botones dinámicos según estado real del usuario
        colAcciones.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }

                String[] row   = getTableView().getItems().get(getIndex());
                String estado  = row[6]; // "Activo" | "Inactivo" | "Bloqueado"
                String usuario = row[1];
                int    id      = Integer.parseInt(row[0]);

                HBox box = new HBox(4);

                // ── Habilitar / Deshabilitar ──
                if ("Inactivo".equals(estado)) {
                    Button btnEnable = boton("✅ Habilitar", "#1a4f2a", "#4ade80");
                    btnEnable.setOnAction(e -> accionHabilitar(id, usuario));
                    box.getChildren().add(btnEnable);
                } else {
                    Button btnDis = boton("🚫 Deshabilitar", "#5c2020", "#ff6b6b");
                    btnDis.setOnAction(e -> accionDeshabilitar(id, usuario));
                    box.getChildren().add(btnDis);
                }

                // ── Desbloquear (solo si tiene intentos fallidos o está bloqueado) ──
                boolean tieneBloq = row[4] != null && !row[4].equals("—") && !row[4].isBlank();
                boolean tieneIntentos = !"0".equals(row[3]);
                if (tieneBloq || tieneIntentos || "Bloqueado".equals(estado)) {
                    Button btnUnlock = boton("🔓 Desbloquear", "#1a3a5c", "#4fc3f7");
                    btnUnlock.setOnAction(e -> accionDesbloquear(id, usuario));
                    box.getChildren().add(btnUnlock);
                }

                // ── Resetear contraseña ──
                Button btnReset = boton("🔑 Reset Pass", "#3a2f0a", "#f1c40f");
                btnReset.setOnAction(e -> accionResetPassword(id, usuario));
                box.getChildren().add(btnReset);

                // ── Cambiar Rol ──
                Button btnRol = boton("👤 Rol", "#1a1a3a", "#9b59b6");
                btnRol.setOnAction(e -> accionCambiarRol(id, usuario, row[2]));
                box.getChildren().add(btnRol);

                setGraphic(box);
            }
        });

        listaFiltrada = new FilteredList<>(listaCompleta, p -> true);
        tablaUsuarios.setItems(listaFiltrada);
    }

    /** Crea un botón compacto con fondo y color de texto */
    private Button boton(String texto, String bg, String fg) {
        Button b = new Button(texto);
        b.setStyle("-fx-background-color:" + bg + ";-fx-text-fill:" + fg + ";" +
                "-fx-padding:3 8;-fx-background-radius:5;-fx-cursor:hand;" +
                "-fx-font-size:10px;-fx-font-weight:bold;");
        return b;
    }

    // ══════════════════════════════════════════
    //  Búsqueda en tiempo real
    // ══════════════════════════════════════════
    private void configurarBusqueda() {
        txtBuscar.textProperty().addListener((obs, old, nv) -> {
            String q = nv == null ? "" : nv.toLowerCase().trim();
            listaFiltrada.setPredicate(row -> {
                if (q.isEmpty()) return true;
                // Buscar en usuario, nombre real, rol y estado
                return row[1].toLowerCase().contains(q)
                        || (row[8] != null && row[8].toLowerCase().contains(q))
                        || row[2].toLowerCase().contains(q)
                        || row[6].toLowerCase().contains(q);
            });
        });
    }

    // ══════════════════════════════════════════
    //  Acciones
    // ══════════════════════════════════════════

    private void accionHabilitar(int id, String usuario) {
        if (!confirmar("¿Habilitar al usuario '" + usuario + "'?")) return;
        new Thread(() -> {
            boolean ok = UsuarioDAO.habilitarUsuario(id);
            if (ok) AuditoriaDAO.registrarAccion("Usuario habilitado: " + usuario, "GESTION_USUARIOS");
            Platform.runLater(() -> {
                msg(ok ? "✅ Usuario '" + usuario + "' habilitado correctamente."
                        : "❌ No se pudo habilitar al usuario.", ok);
                if (ok) cargarUsuarios();
            });
        }, "accion-habilitar") {{ setDaemon(true); }}.start();
    }

    private void accionDeshabilitar(int id, String usuario) {
        // Protección: no deshabilitar al admin
        if ("admin".equalsIgnoreCase(usuario)) {
            msg("⚠ No se puede deshabilitar al usuario 'admin'.", false);
            return;
        }
        if (!confirmar("¿Deshabilitar al usuario '" + usuario + "'?\n\nNo podrá iniciar sesión hasta que sea habilitado de nuevo.")) return;
        new Thread(() -> {
            boolean ok = UsuarioDAO.deshabilitarUsuario(id);
            if (ok) AuditoriaDAO.registrarAccion("Usuario deshabilitado: " + usuario, "GESTION_USUARIOS");
            Platform.runLater(() -> {
                msg(ok ? "🚫 Usuario '" + usuario + "' deshabilitado."
                        : "❌ No se pudo deshabilitar al usuario.", ok);
                if (ok) cargarUsuarios();
            });
        }, "accion-deshabilitar") {{ setDaemon(true); }}.start();
    }

    private void accionDesbloquear(int id, String usuario) {
        new Thread(() -> {
            boolean ok = UsuarioDAO.desbloquear(usuario);
            if (ok) AuditoriaDAO.registrarAccion("Usuario desbloqueado: " + usuario, "GESTION_USUARIOS");
            Platform.runLater(() -> {
                msg(ok ? "🔓 Usuario '" + usuario + "' desbloqueado. Intentos fallidos reiniciados."
                        : "❌ No se pudo desbloquear al usuario.", ok);
                if (ok) cargarUsuarios();
            });
        }, "accion-desbloquear") {{ setDaemon(true); }}.start();
    }

    private void accionResetPassword(int id, String usuario) {
        // Diálogo de confirmación con contraseña nueva temporal
        TextInputDialog dialog = new TextInputDialog("Umg2026!");
        dialog.setTitle("Resetear Contraseña");
        dialog.setHeaderText("Nueva contraseña temporal para: " + usuario);
        dialog.setContentText("Contraseña nueva:");
        // Estilo oscuro
        dialog.getDialogPane().setStyle(
                "-fx-background-color:#111827;-fx-border-color:#4fc3f7;-fx-border-radius:8;");
        dialog.getDialogPane().lookup(".content-text") .setStyle("-fx-text-fill:white;");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(nuevaPass -> {
            if (nuevaPass.trim().isEmpty()) {
                msg("⚠ La contraseña no puede estar vacía.", false); return;
            }
            new Thread(() -> {
                boolean ok = UsuarioDAO.cambiarPassword(id, nuevaPass.trim());
                if (ok) AuditoriaDAO.registrarAccion(
                        "Contraseña reseteada por admin: " + usuario, "GESTION_USUARIOS");
                Platform.runLater(() -> {
                    if (ok) {
                        // Mostrar contraseña en alerta informativa
                        Alert info = new Alert(Alert.AlertType.INFORMATION);
                        info.setTitle("Contraseña Reseteada");
                        info.setHeaderText("✅ Contraseña actualizada para: " + usuario);
                        info.setContentText(
                                "Nueva contraseña temporal:\n\n  " + nuevaPass.trim() +
                                        "\n\nComunícasela al usuario para que la cambie en su próximo inicio de sesión.");
                        info.getDialogPane().setStyle("-fx-background-color:#111827;");
                        info.showAndWait();
                        msg("✅ Contraseña de '" + usuario + "' reseteada.", true);
                    } else {
                        msg("❌ No se pudo resetear la contraseña. " +
                                "Verifica que cumpla los requisitos mínimos.", false);
                    }
                });
            }, "accion-reset-pass") {{ setDaemon(true); }}.start();
        });
    }

    private void accionCambiarRol(int id, String usuario, String rolActual) {
        ChoiceDialog<String> dialog = new ChoiceDialog<>(rolActual,
                "Administrador", "Catedratico", "Estudiante", "Seguridad", "Mantenimiento", "Administrativo");
        dialog.setTitle("Cambiar Rol");
        dialog.setHeaderText("Cambiar rol de: " + usuario);
        dialog.setContentText("Nuevo rol:");
        dialog.getDialogPane().setStyle("-fx-background-color:#111827;");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(nuevoRol -> {
            if (nuevoRol.equals(rolActual)) {
                msg("⚠ El rol seleccionado es el mismo que el actual.", false); return;
            }
            int idRol = switch (nuevoRol) {
                case "Administrador"  -> 1;
                case "Catedratico"    -> 2;
                case "Estudiante"     -> 3;
                case "Seguridad"      -> 4;
                case "Mantenimiento"  -> 5;
                case "Administrativo" -> 6;
                default               -> 3;
            };
            new Thread(() -> {
                boolean ok = UsuarioDAO.cambiarRol(id, idRol);
                if (ok) AuditoriaDAO.registrarAccion(
                        "Rol cambiado: " + usuario + " → " + nuevoRol, "GESTION_USUARIOS");
                Platform.runLater(() -> {
                    msg(ok ? "✅ Rol de '" + usuario + "' actualizado a: " + nuevoRol
                            : "❌ No se pudo cambiar el rol.", ok);
                    if (ok) cargarUsuarios();
                });
            }, "accion-cambiar-rol") {{ setDaemon(true); }}.start();
        });
    }

    // ══════════════════════════════════════════
    //  Cargar datos
    // ══════════════════════════════════════════
    @FXML public void refrescar() {
        txtBuscar.clear();
        cargarUsuarios();
    }

    private void cargarUsuarios() {
        new Thread(() -> {
            List<String[]> data = UsuarioDAO.listarTodosConNombre();
            Platform.runLater(() -> {
                listaCompleta.setAll(data);
                actualizarContadores(data);
            });
        }, "load-usuarios") {{ setDaemon(true); }}.start();
    }

    private void actualizarContadores(List<String[]> data) {
        long activos    = data.stream().filter(r -> "Activo".equals(r[6])).count();
        long inactivos  = data.stream().filter(r -> !"Activo".equals(r[6])).count();
        lblTotalCount    .setText(String.valueOf(data.size()));
        lblActivosCount  .setText(String.valueOf(activos));
        lblBloqueadosCount.setText(String.valueOf(inactivos));
    }

    // ══════════════════════════════════════════
    //  Helpers UI
    // ══════════════════════════════════════════
    private boolean confirmar(String mensaje) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, mensaje, ButtonType.YES, ButtonType.NO);
        a.setHeaderText("Confirmar acción");
        a.getDialogPane().setStyle("-fx-background-color:#111827;");
        return a.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private void msg(String text, boolean ok) {
        lblMensaje.setText(text);
        lblMensaje.setStyle(ok
                ? "-fx-text-fill:#27ae60;-fx-font-weight:bold;-fx-font-size:12px;"
                : "-fx-text-fill:#e74c3c;-fx-font-weight:bold;-fx-font-size:12px;");
    }
}