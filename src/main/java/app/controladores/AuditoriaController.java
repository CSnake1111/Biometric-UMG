package app.controladores;

import app.dao.AuditoriaDAO;
import app.dao.UsuarioDAO;
import app.modelos.AlertaSistema;
import app.modelos.AuditoriaAccion;
import app.modelos.IntentoLogin;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

public class AuditoriaController implements Initializable {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @FXML private TabPane tabPane;

    /* ── Actions tab ── */
    @FXML private TableView<AuditoriaAccion>         tablaAcciones;
    @FXML private TableColumn<AuditoriaAccion,String> colFechaA, colUsuarioA, colRolA,
                                                      colModuloA, colAccionA, colDescA,
                                                      colResultadoA, colIPA;
    @FXML private TextField    txtFiltro;
    @FXML private ComboBox<String> cmbModulo, cmbResultado;
    @FXML private Label        lblTotalAcciones;

    /* ── Login attempts tab ── */
    @FXML private TableView<IntentoLogin>         tablaIntentos;
    @FXML private TableColumn<IntentoLogin,String> colFechaI, colUsuarioI, colIPI,
                                                   colMetodoI, colResultadoI;
    @FXML private Label lblTotalIntentos, lblFallidosHoy;

    /* ── Alerts tab ── */
    @FXML private TableView<AlertaSistema>         tablaAlertas;
    @FXML private TableColumn<AlertaSistema,String> colFechaAl, colTipoAl, colDescAl,
                                                    colIPAl, colEstadoAl;
    @FXML private TableColumn<AlertaSistema,Void>   colAccionAl;
    @FXML private CheckBox chkSoloActivas;
    @FXML private Label    lblAlertasActivas;

    /* ── Summary labels ── */
    @FXML private Label lblResTotal, lblResExitosas, lblResFallidas, lblResAlertas;

    private final ObservableList<AuditoriaAccion> listaAcciones = FXCollections.observableArrayList();
    private FilteredList<AuditoriaAccion> filtrada;
    private final ObservableList<IntentoLogin>    listaIntentos = FXCollections.observableArrayList();
    private final ObservableList<AlertaSistema>   listaAlertas  = FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        if (!esAdmin()) { mostrarDenegado(); return; }
        configurarTablaAcciones();
        configurarTablaIntentos();
        configurarTablaAlertas();
        configurarFiltros();
        cargarTodo();
    }

    private boolean esAdmin() {
        var u = UsuarioDAO.getSesionActiva();
        if (u == null || u.getNombreRol() == null) return false;
        String rol = u.getNombreRol();
        return rol.equalsIgnoreCase("Administrador") || rol.equalsIgnoreCase("Admin");
    }

    private void mostrarDenegado() {
        tablaAcciones.setPlaceholder(new Label("⛔ Access denied — Administrators only"));
        tablaIntentos.setPlaceholder(new Label("⛔ Access denied"));
        tablaAlertas .setPlaceholder(new Label("⛔ Access denied"));
    }

    private void configurarTablaAcciones() {
        colFechaA    .setCellValueFactory(c -> s(c.getValue().getFechaHora() != null ? c.getValue().getFechaHora().format(FMT) : "—"));
        colUsuarioA  .setCellValueFactory(c -> s(c.getValue().getNombreUsuario()));
        colRolA      .setCellValueFactory(c -> s(c.getValue().getRol()));
        colModuloA   .setCellValueFactory(c -> s(c.getValue().getModulo()));
        colAccionA   .setCellValueFactory(c -> s(c.getValue().getAccion()));
        colDescA     .setCellValueFactory(c -> s(c.getValue().getDescripcion()));
        colResultadoA.setCellValueFactory(c -> s(c.getValue().getResultado()));
        colIPA       .setCellValueFactory(c -> s(c.getValue().getIp()));
        colResultadoA.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty||v==null) { setText(null); setStyle(""); return; }
                setText(v);
                setStyle("EXITOSO".equals(v)
                    ? "-fx-text-fill:#27ae60;-fx-font-weight:bold;"
                    : "-fx-text-fill:#e74c3c;-fx-font-weight:bold;");
            }
        });
        filtrada = new FilteredList<>(listaAcciones, p -> true);
        tablaAcciones.setItems(filtrada);
    }

    private void configurarTablaIntentos() {
        colFechaI    .setCellValueFactory(c -> s(c.getValue().getFechaHora() != null ? c.getValue().getFechaHora().format(FMT) : "—"));
        colUsuarioI  .setCellValueFactory(c -> s(c.getValue().getUsuario()));
        colIPI       .setCellValueFactory(c -> s(c.getValue().getIp()));
        colMetodoI   .setCellValueFactory(c -> s(c.getValue().getMetodo()));
        colResultadoI.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isExitoso() ? "✅ OK" : "❌ Failed"));
        colResultadoI.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty||v==null) { setText(null); setStyle(""); return; }
                setText(v); setStyle(v.startsWith("✅") ? "-fx-text-fill:#27ae60;" : "-fx-text-fill:#e74c3c;");
            }
        });
        tablaIntentos.setItems(listaIntentos);
    }

    private void configurarTablaAlertas() {
        colFechaAl.setCellValueFactory(c -> s(c.getValue().getFechaHora() != null ? c.getValue().getFechaHora().format(FMT) : "—"));
        colTipoAl .setCellValueFactory(c -> s(c.getValue().getTipo()));
        colDescAl .setCellValueFactory(c -> s(c.getValue().getDescripcion()));
        colIPAl   .setCellValueFactory(c -> s(c.getValue().getIp()));
        colEstadoAl.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isResuelta() ? "✅ Resolved" : "⚠ Active"));
        colEstadoAl.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty||v==null) { setText(null); setStyle(""); return; }
                setText(v); setStyle(v.startsWith("✅") ? "-fx-text-fill:#27ae60;" : "-fx-text-fill:#e67e22;-fx-font-weight:bold;");
            }
        });
        colAccionAl.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Resolve");
            { btn.setStyle("-fx-background-color:#1a4f2a;-fx-text-fill:white;-fx-padding:3 8;-fx-background-radius:4;-fx-cursor:hand;-fx-font-size:10px;");
              btn.setOnAction(e -> {
                AlertaSistema al = getTableView().getItems().get(getIndex());
                if (!al.isResuelta()) {
                    AuditoriaDAO.resolverAlerta(al.getIdAlerta());
                    AuditoriaDAO.registrarAccion("Alert resolved: " + al.getTipo(), "AUDITORIA");
                    cargarAlertas();
                }
              });
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                AlertaSistema al = getTableView().getItems().get(getIndex());
                btn.setDisable(al.isResuelta());
                setGraphic(btn);
            }
        });
        tablaAlertas.setItems(listaAlertas);
    }

    private void configurarFiltros() {
        cmbModulo.setItems(FXCollections.observableArrayList(
            "Todos","LOGIN","PERSONAS","ASISTENCIAS","INGRESOS",
            "RESTRICCIONES","CURSOS","AUDITORIA","SEGURIDAD","GESTION_USUARIOS"));
        cmbModulo.setValue("Todos");
        cmbResultado.setItems(FXCollections.observableArrayList("Todos","EXITOSO","FALLIDO"));
        cmbResultado.setValue("Todos");
        txtFiltro.textProperty().addListener((obs,o,nv) -> aplicarFiltro());
        cmbModulo.valueProperty().addListener((obs,o,nv) -> aplicarFiltro());
        cmbResultado.valueProperty().addListener((obs,o,nv) -> aplicarFiltro());
    }

    private void aplicarFiltro() {
        String txt = txtFiltro.getText().toLowerCase();
        String mod = cmbModulo.getValue();
        String res = cmbResultado.getValue();
        filtrada.setPredicate(a -> {
            boolean ok = txt.isBlank()
                || (a.getNombreUsuario() != null && a.getNombreUsuario().toLowerCase().contains(txt))
                || (a.getDescripcion()   != null && a.getDescripcion()  .toLowerCase().contains(txt))
                || (a.getAccion()        != null && a.getAccion()       .toLowerCase().contains(txt));
            boolean okM = "Todos".equals(mod) || mod.equals(a.getModulo());
            boolean okR = "Todos".equals(res) || res.equals(a.getResultado());
            return ok && okM && okR;
        });
        lblTotalAcciones.setText("Records: " + filtrada.size());
    }

    @FXML public void onRefrescarAcciones() { cargarAcciones(); }
    @FXML public void onRefrescarIntentos() { cargarIntentos(); }
    @FXML public void onRefrescarAlertas()  { cargarAlertas(); }

    @FXML public void onDesbloquear() {
        IntentoLogin sel = tablaIntentos.getSelectionModel().getSelectedItem();
        if (sel == null) { alerta("Select a login attempt first."); return; }
        Alert c = new Alert(Alert.AlertType.CONFIRMATION,
            "Unlock user: " + sel.getUsuario() + "?", ButtonType.YES, ButtonType.NO);
        c.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) {
                AuditoriaDAO.desbloquearUsuario(sel.getUsuario());
                cargarIntentos();
                alerta("✅ User unlocked.");
            }
        });
    }

    private void cargarTodo() {
        new Thread(() -> {
            List<AuditoriaAccion> acc = AuditoriaDAO.listarAcciones(null, null, 500);
            List<IntentoLogin>    int_ = AuditoriaDAO.listarIntentos(null, 200);
            List<AlertaSistema>   ale = AuditoriaDAO.listarAlertas(false);
            int[] res = AuditoriaDAO.resumenHoy();
            int act = AuditoriaDAO.contarAlertasActivas();
            Platform.runLater(() -> {
                listaAcciones.setAll(acc);
                listaIntentos.setAll(int_);
                listaAlertas .setAll(ale);
                lblTotalAcciones.setText("Records: " + acc.size());
                lblTotalIntentos.setText("Total: " + int_.size());
                lblAlertasActivas.setText("Active alerts: " + act);
                long hoyFail = int_.stream().filter(i -> !i.isExitoso()
                    && i.getFechaHora() != null
                    && i.getFechaHora().toLocalDate().equals(java.time.LocalDate.now()))
                    .count();
                lblFallidosHoy.setText("Failures today: " + hoyFail);
                if (lblResTotal    != null) lblResTotal   .setText(String.valueOf(res[0]));
                if (lblResExitosas != null) lblResExitosas.setText(String.valueOf(res[1]));
                if (lblResFallidas != null) lblResFallidas.setText(String.valueOf(res[2]));
                if (lblResAlertas  != null) lblResAlertas .setText(String.valueOf(act));
                aplicarFiltro();
            });
        }, "load-auditoria") {{ setDaemon(true); }}.start();
    }

    private void cargarAcciones() {
        new Thread(() -> {
            List<AuditoriaAccion> acc = AuditoriaDAO.listarAcciones(null, null, 500);
            Platform.runLater(() -> { listaAcciones.setAll(acc); aplicarFiltro(); });
        }, "load-acciones") {{ setDaemon(true); }}.start();
    }

    private void cargarIntentos() {
        new Thread(() -> {
            List<IntentoLogin> ints = AuditoriaDAO.listarIntentos(null, 200);
            Platform.runLater(() -> listaIntentos.setAll(ints));
        }, "load-intentos") {{ setDaemon(true); }}.start();
    }

    private void cargarAlertas() {
        new Thread(() -> {
            List<AlertaSistema> ale = AuditoriaDAO.listarAlertas(chkSoloActivas.isSelected());
            int act = AuditoriaDAO.contarAlertasActivas();
            Platform.runLater(() -> {
                listaAlertas.setAll(ale);
                lblAlertasActivas.setText("Active alerts: " + act);
            });
        }, "load-alertas") {{ setDaemon(true); }}.start();
    }

    private void alerta(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setHeaderText(null); a.showAndWait();
    }

    private SimpleStringProperty s(String v) { return new SimpleStringProperty(v != null ? v : "—"); }
}
