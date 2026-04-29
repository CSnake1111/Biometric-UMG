// ═══════════════════════════════════════════════════════════
//  ListaPersonasController.java  — BiometricUMG 4.0
//  Usa Usuario en lugar de Persona / PersonaDAO
// ═══════════════════════════════════════════════════════════
package app.controladores;

import app.dao.AuditoriaDAO;
import app.dao.UsuarioDAO;
import app.modelos.Usuario;
import app.servicios.CamaraService;
import app.servicios.EmailService;
import app.servicios.FirmaService;
import app.servicios.PDFService;
import app.servicios.QRService;
import app.servicios.ReconocimientoFacialService;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;

import java.awt.Desktop;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicBoolean;

public class ListaPersonasController implements Initializable {

    @FXML private TextField                      txtBuscar;
    @FXML private ComboBox<String>               cmbFiltroTipo;
    @FXML private TableView<Usuario>             tablaPersonas;
    @FXML private TableColumn<Usuario, String>   colId;
    @FXML private TableColumn<Usuario, String>   colNombre;
    @FXML private TableColumn<Usuario, String>   colCarne;
    @FXML private TableColumn<Usuario, String>   colTipo;
    @FXML private TableColumn<Usuario, String>   colCarrera;
    @FXML private TableColumn<Usuario, String>   colCorreo;
    @FXML private TableColumn<Usuario, String>   colEstado;
    @FXML private Label                          lblTotal;

    @FXML private ImageView imgDetalle;
    @FXML private Label     lblNombreDetalle;
    @FXML private Label     lblCorreoDetalle;
    @FXML private Label     lblTipoDetalle;
    @FXML private Label     lblCarreraDetalle;
    @FXML private Label     lblFechaDetalle;

    private final ObservableList<Usuario> listaUsuarios = FXCollections.observableArrayList();
    private Usuario usuarioSeleccionado = null;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        cmbFiltroTipo.getItems().addAll("Todos","Estudiante","Catedrático",
                "Mantenimiento","Seguridad","Administrativo");
        cmbFiltroTipo.setValue("Todos");
        configurarColumnas();
        cargarUsuarios();
        txtBuscar.textProperty().addListener((o, old, nv) -> filtrar());
        cmbFiltroTipo.setOnAction(e -> filtrar());
        tablaPersonas.getSelectionModel().selectedItemProperty()
                .addListener((o, old, nv) -> mostrarDetalle(nv));
    }

    private void configurarColumnas() {
        colId    .setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getIdUsuario())));
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombreCompleto()));
        colCarne .setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getCarne() != null ? c.getValue().getCarne() : "—"));
        colTipo  .setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTipoPersona()));
        colCarrera.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getCarrera() != null ? c.getValue().getCarrera() : "—"));
        colCorreo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCorreo()));
        colEstado.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().isEstado() ? "✅ Activo" : "❌ Inactivo"));

        tablaPersonas.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Usuario item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) { setStyle(""); return; }
                setStyle(item.isEstado() ? "" : "-fx-opacity:0.5;");
            }
        });
        tablaPersonas.setItems(listaUsuarios);
    }

    // FIX #7: helper que soporta tanto URL Supabase (https://...) como ruta local
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

    private void cargarUsuarios() {
        listaUsuarios.setAll(UsuarioDAO.listarTodos());
        actualizarTotal();
    }

    private void filtrar() {
        String texto = txtBuscar.getText().trim();
        String tipo  = cmbFiltroTipo.getValue();
        List<Usuario> base = texto.isEmpty()
                ? UsuarioDAO.listarTodos() : UsuarioDAO.buscar(texto);
        if (!"Todos".equals(tipo))
            base = base.stream().filter(u -> tipo.equalsIgnoreCase(u.getTipoPersona())).toList();
        listaUsuarios.setAll(base);
        actualizarTotal();
    }

    private void mostrarDetalle(Usuario u) {
        if (u == null) return;
        usuarioSeleccionado = u;
        lblNombreDetalle .setText(u.getNombreCompleto());
        lblCorreoDetalle .setText(u.getCorreo());
        lblTipoDetalle   .setText(u.getTipoPersona());
        lblCarreraDetalle.setText(u.getCarrera() != null ? u.getCarrera() : "—");
        lblFechaDetalle  .setText(u.getFechaRegistro() != null
                ? u.getFechaRegistro().toLocalDate().toString() : "—");

        // FIX #7: soportar URL Supabase (https://...) y ruta local
        Image fotoImg = cargarImagenFoto(u.getFoto());
        if (fotoImg != null) {
            imgDetalle.setImage(fotoImg);
            double radio = imgDetalle.getFitWidth() / 2;
            imgDetalle.setClip(new Circle(radio, radio, radio));
        } else {
            imgDetalle.setImage(null);
        }
    }

    @FXML public void verCarnet() {
        if (usuarioSeleccionado == null) { alerta("Selecciona un usuario"); return; }
        String ruta = PDFService.getCarpetaPDF() + "carnet_" + usuarioSeleccionado.getIdUsuario() + ".pdf";
        if (!new File(ruta).exists()) { alerta("El carnet no existe. Usa 'Regenerar Carnet' primero."); return; }
        try { Desktop.getDesktop().open(new File(ruta)); }
        catch (Exception e) { alerta("No se pudo abrir el PDF: " + e.getMessage()); }
    }

    @FXML public void regenerarCarnet() {
        if (usuarioSeleccionado == null) { alerta("Selecciona un usuario"); return; }
        new Thread(() -> {
            try {
                FirmaService.inicializar();
                String firma = FirmaService.generarFirmaCompleta(
                        usuarioSeleccionado.getIdUsuario(),
                        usuarioSeleccionado.getNombreCompleto(),
                        usuarioSeleccionado.getCarne() != null ? usuarioSeleccionado.getCarne() : "",
                        usuarioSeleccionado.getCorreo()
                );
                QRService.generarPaginaVerificacion(usuarioSeleccionado, firma);
                QRService.generarCodigoQR(usuarioSeleccionado.getIdUsuario(),
                        usuarioSeleccionado.getNombreCompleto(), usuarioSeleccionado.getCarne());
                String ruta = PDFService.generarCarnet(usuarioSeleccionado);
                Platform.runLater(() -> {
                    if (ruta != null) {
                        info("✅ Carnet regenerado\n✅ QR actualizado");
                        try { Desktop.getDesktop().open(new File(ruta)); } catch (Exception ignored) {}
                    } else { alerta("❌ No se pudo regenerar el carnet"); }
                });
            } catch (Exception e) {
                Platform.runLater(() -> alerta("❌ Error: " + e.getMessage()));
            }
        }, "pdf-thread") {{ setDaemon(true); }}.start();
    }

    @FXML public void reenviarCorreo() {
        if (usuarioSeleccionado == null) { alerta("Selecciona un usuario"); return; }
        String ruta = PDFService.getCarpetaPDF() + "carnet_" + usuarioSeleccionado.getIdUsuario() + ".pdf";
        if (!new File(ruta).exists()) { alerta("El carnet no existe. Regenera primero."); return; }
        new Thread(() -> {
            boolean ok = EmailService.enviarCarnet(
                    usuarioSeleccionado.getCorreo(), usuarioSeleccionado.getNombreCompleto(), ruta);
            Platform.runLater(() -> info(ok
                    ? "✅ Carnet reenviado a " + usuarioSeleccionado.getCorreo()
                    : "❌ No se pudo enviar."));
        }, "email-thread") {{ setDaemon(true); }}.start();
    }

    @FXML public void enviarWhatsApp() {
        if (usuarioSeleccionado == null) { alerta("Selecciona un usuario"); return; }
        String tel = usuarioSeleccionado.getTelefono();
        if (tel == null || tel.isEmpty()) { alerta("Sin número de teléfono registrado"); return; }
        try {
            tel = tel.replaceAll("[^0-9]", "");
            if (tel.length() == 8) tel = "502" + tel;
            String msg = "🎓 *UMG La Florida*\nHola *" + usuarioSeleccionado.getNombreCompleto()
                    + "*,\nTu carnet fue enviado a: " + usuarioSeleccionado.getCorreo()
                    + "\n_BiometricUMG 4.0_";
            String url = "https://wa.me/" + tel + "?text=" + java.net.URLEncoder.encode(msg, "UTF-8");
            Desktop.getDesktop().browse(new java.net.URI(url));
        } catch (Exception e) { alerta("❌ Error: " + e.getMessage()); }
    }

    @FXML public void desactivarPersona() {
        Usuario sel = tablaPersonas.getSelectionModel().getSelectedItem();
        if (sel == null) { alerta("Selecciona un usuario"); return; }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "¿Desactivar a " + sel.getNombreCompleto() + "?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(r -> {
            if (r == ButtonType.YES) {
                UsuarioDAO.desactivar(sel.getIdUsuario());
                AuditoriaDAO.registrarAccion("Usuario desactivado: " + sel.getNombreCompleto(), "LISTA_USUARIOS");
                cargarUsuarios();
            }
        });
    }

    @FXML public void enviarMasivo() {
        List<Usuario> activos = listaUsuarios.stream()
                .filter(u -> u.isEstado() && u.getCorreo() != null).toList();
        if (activos.isEmpty()) { alerta("No hay usuarios activos"); return; }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "¿Enviar carnet a " + activos.size() + " usuarios?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(r -> {
            if (r != ButtonType.YES) return;
            new Thread(() -> {
                int ok = 0, err = 0;
                for (Usuario u : activos) {
                    String ruta = PDFService.getCarpetaPDF() + "carnet_" + u.getIdUsuario() + ".pdf";
                    if (!new File(ruta).exists()) ruta = PDFService.generarCarnet(u);
                    if (ruta != null && EmailService.enviarCarnet(u.getCorreo(), u.getNombreCompleto(), ruta)) ok++;
                    else err++;
                    try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                }
                final int of = ok, ef = err;
                Platform.runLater(() -> info("✅ Enviados: " + of + " | ❌ Fallidos: " + ef));
            }, "masivo-thread") {{ setDaemon(true); }}.start();
        });
    }

    @FXML public void actualizarLista() { cargarUsuarios(); }
    private void actualizarTotal()      { lblTotal.setText(listaUsuarios.size() + " usuarios"); }
    private void alerta(String msg)     { new Alert(Alert.AlertType.WARNING,     msg, ButtonType.OK).showAndWait(); }
    private void info(String msg)       { new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait(); }

    @FXML public void editarPersona() {
        if (usuarioSeleccionado == null) { alerta("Selecciona un usuario"); return; }

        Dialog<Usuario> dialog = new Dialog<>();
        dialog.setTitle("Editar Usuario");
        dialog.setHeaderText("Editando: " + usuarioSeleccionado.getNombreCompleto());
        dialog.getDialogPane().setPrefWidth(520);
        dialog.getDialogPane().setStyle("-fx-background-color:#0e1628;");

        ButtonType btnGuardar  = new ButtonType("💾 Guardar", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancelar = new ButtonType("Cancelar",   javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(btnGuardar, btnCancelar);

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(12); grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20));
        grid.setStyle("-fx-background-color:#0e1628;");

        String lbl = "-fx-text-fill:#8a9bbf;-fx-font-size:11px;-fx-font-weight:bold;";
        String fld = "-fx-background-color:#0d1829;-fx-text-fill:#e8edf5;" +
                "-fx-border-color:#1e2d47;-fx-border-radius:6;-fx-background-radius:6;-fx-padding:8;";

        TextField tN  = campo(usuarioSeleccionado.getNombre(),   fld, 200);
        TextField tA  = campo(usuarioSeleccionado.getApellido(), fld, 200);
        TextField tT  = campo(usuarioSeleccionado.getTelefono() != null ? usuarioSeleccionado.getTelefono() : "", fld, 200);
        TextField tC  = campo(usuarioSeleccionado.getCorreo(),   fld, 200);
        TextField tCr = campo(usuarioSeleccionado.getCarne()   != null ? usuarioSeleccionado.getCarne()   : "", fld, 200);
        TextField tCa = campo(usuarioSeleccionado.getCarrera() != null ? usuarioSeleccionado.getCarrera() : "", fld, 200);

        ComboBox<String> cmbTipo = new ComboBox<>();
        cmbTipo.getItems().addAll("Estudiante","Catedrático","Mantenimiento","Seguridad","Administrativo");
        cmbTipo.setValue(usuarioSeleccionado.getTipoPersona());
        cmbTipo.setStyle(fld); cmbTipo.setPrefWidth(200);

        addRow(grid, 0, "NOMBRE *",  lbl, tN);
        addRow(grid, 1, "APELLIDO *",lbl, tA);
        addRow(grid, 2, "TELÉFONO",  lbl, tT);
        addRow(grid, 3, "CORREO *",  lbl, tC);
        addRow(grid, 4, "CARNÉ",     lbl, tCr);
        addRow(grid, 5, "CARRERA",   lbl, tCa);
        addRow(grid, 6, "TIPO",      lbl, cmbTipo);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().lookupButton(btnGuardar)
                .setStyle("-fx-background-color:#1a4f8c;-fx-text-fill:white;-fx-background-radius:6;-fx-font-weight:bold;");
        dialog.getDialogPane().lookupButton(btnCancelar)
                .setStyle("-fx-background-color:#152035;-fx-text-fill:#8a9bbf;-fx-background-radius:6;");

        dialog.setResultConverter(b -> {
            if (b != btnGuardar) return null;
            if (tN.getText().trim().isEmpty() || tA.getText().trim().isEmpty()) {
                alerta("Nombre y apellido son obligatorios"); return null; }
            Usuario act = new Usuario();
            act.setIdUsuario  (usuarioSeleccionado.getIdUsuario());
            act.setNombre     (tN.getText().trim());
            act.setApellido   (tA.getText().trim());
            act.setTelefono   (tT.getText().trim());
            act.setCorreo     (tC.getText().trim());
            act.setCarne      (tCr.getText().trim());
            act.setCarrera    (tCa.getText().trim());
            act.setTipoPersona(cmbTipo.getValue());
            act.setFoto       (usuarioSeleccionado.getFoto());
            act.setQrCodigo   (usuarioSeleccionado.getQrCodigo());
            // FIX: recalcular id_rol desde el tipo seleccionado, NO copiar el rol viejo.
            // Si el tipo cambió (ej: Estudiante → Seguridad) el rol DEBE cambiar también.
            int nuevoIdRol = switch (cmbTipo.getValue() != null ? cmbTipo.getValue() : "") {
                case "Catedrático", "Catedratico" -> 2;
                case "Estudiante"                 -> 3;
                case "Seguridad"                  -> 4;
                case "Mantenimiento"               -> 5;
                case "Administrativo"              -> 6;
                default                           -> usuarioSeleccionado.getIdRol(); // sin cambio si no se reconoce
            };
            act.setIdRol  (nuevoIdRol);
            act.setActivo (usuarioSeleccionado.isActivo());
            return act;
        });

        dialog.showAndWait().ifPresent(act -> new Thread(() -> {
            boolean ok = UsuarioDAO.actualizar(act);
            if (ok) {
                FirmaService.inicializar();
                String firma = FirmaService.generarFirmaCompleta(
                        act.getIdUsuario(), act.getNombreCompleto(),
                        act.getCarne() != null ? act.getCarne() : "", act.getCorreo());
                QRService.generarPaginaVerificacion(act, firma);
                QRService.generarCodigoQR(act.getIdUsuario(), act.getNombreCompleto(), act.getCarne());
                String rutaPDF = PDFService.generarCarnet(act);
                Platform.runLater(() -> {
                    usuarioSeleccionado = act;
                    cargarUsuarios(); mostrarDetalle(act);
                    info("✅ Datos actualizados\n✅ Carnet regenerado");
                    if (rutaPDF != null) try { Desktop.getDesktop().open(new File(rutaPDF)); } catch (Exception ignored) {}
                });
            } else {
                Platform.runLater(() -> alerta("❌ No se pudieron guardar los cambios"));
            }
        }, "edit-thread") {{ setDaemon(true); }}.start());
    }

    @FXML public void reenrolarRostro() {
        if (usuarioSeleccionado == null) { alerta("Selecciona un usuario"); return; }

        // ── Obtener cámaras disponibles antes de abrir el diálogo ──
        List<String> nombresCamaras = CamaraService.listarCamaras();
        if (nombresCamaras.isEmpty()) {
            alerta("❌ No se detectó ninguna cámara conectada.\n\nVerifica que la cámara esté conectada y no esté siendo usada por otra aplicación.");
            return;
        }

        // ────────────────────────────────────────────────────────
        //  Construir ventana de re-enrolamiento con preview
        // ────────────────────────────────────────────────────────
        Stage ventana = new Stage();
        ventana.setTitle("Re-enrolamiento — " + usuarioSeleccionado.getNombreCompleto());
        ventana.initModality(Modality.APPLICATION_MODAL);
        ventana.setResizable(false);

        // Preview de cámara
        ImageView imgPreview = new ImageView();
        imgPreview.setFitWidth(380);
        imgPreview.setFitHeight(285);
        imgPreview.setPreserveRatio(true);
        imgPreview.setStyle("-fx-background-color:#0a0a14;");

        // Canvas de guía facial (óvalo animado)
        Canvas canvasGuia = new Canvas(380, 285);
        canvasGuia.setMouseTransparent(true);
        StackPane stackCam = new StackPane(imgPreview, canvasGuia);
        stackCam.setStyle("-fx-background-color:#0a0a14;-fx-border-color:#1e2d47;-fx-border-width:1;-fx-border-radius:8;-fx-background-radius:8;");
        stackCam.setPrefSize(380, 285);

        Label lblPlaceholder = new Label("📷  Sin cámara activa\nPresiona \"Iniciar cámara\" para comenzar");
        lblPlaceholder.setStyle("-fx-text-fill:#4a6080;-fx-font-size:13px;-fx-text-alignment:center;");
        lblPlaceholder.setAlignment(Pos.CENTER);
        lblPlaceholder.setWrapText(true);
        stackCam.getChildren().add(lblPlaceholder);

        // Selector de cámara
        ComboBox<String> cmbCamara = new ComboBox<>();
        cmbCamara.getItems().addAll(nombresCamaras);
        cmbCamara.setValue(nombresCamaras.get(0));
        cmbCamara.setMaxWidth(Double.MAX_VALUE);
        cmbCamara.setStyle("-fx-background-color:#0d1829;-fx-text-fill:#e8edf5;-fx-border-color:#1e2d47;-fx-border-radius:6;-fx-background-radius:6;");

        // Estado y progreso
        Label lblEstado = new Label("⏸  Cámara inactiva");
        lblEstado.setStyle("-fx-text-fill:#8a9bbf;-fx-font-size:12px;");
        ProgressBar pb = new ProgressBar(0);
        pb.setMaxWidth(Double.MAX_VALUE);
        pb.setVisible(false);

        Label lblMuestras = new Label("Muestras: 0 / 80");
        lblMuestras.setStyle("-fx-text-fill:#4a90e2;-fx-font-size:11px;-fx-font-weight:bold;");

        // ── Botones ──
        Button btnIniciarCam  = new Button("▶  Iniciar cámara");
        Button btnDetenerCam  = new Button("⏹  Detener cámara");
        Button btnEnrolar     = new Button("🎯  Iniciar enrolamiento");
        Button btnCancelar    = new Button("✕  Cancelar");

        String estPrimary  = "-fx-background-color:#1a4f8c;-fx-text-fill:white;-fx-background-radius:7;-fx-font-weight:bold;-fx-padding:8 18;";
        String estSecond   = "-fx-background-color:#152035;-fx-text-fill:#8a9bbf;-fx-background-radius:7;-fx-border-color:#1e2d47;-fx-border-radius:7;-fx-padding:8 18;";
        String estDanger   = "-fx-background-color:#3d1515;-fx-text-fill:#ff7070;-fx-background-radius:7;-fx-border-color:#5c2020;-fx-border-radius:7;-fx-padding:8 18;";
        String estSuccess  = "-fx-background-color:#0e3d2a;-fx-text-fill:#4ade80;-fx-background-radius:7;-fx-font-weight:bold;-fx-padding:8 18;";

        btnIniciarCam .setStyle(estPrimary);
        btnDetenerCam .setStyle(estSecond);  btnDetenerCam.setDisable(true);
        btnEnrolar    .setStyle(estSuccess); btnEnrolar.setDisable(true);
        btnCancelar   .setStyle(estDanger);

        HBox filaBotones = new HBox(10, btnIniciarCam, btnDetenerCam, btnEnrolar, btnCancelar);
        filaBotones.setAlignment(Pos.CENTER_LEFT);

        // ── Layout ──
        Label lblTituloSelector = new Label("📹  Cámara a utilizar:");
        lblTituloSelector.setStyle("-fx-text-fill:#8a9bbf;-fx-font-size:11px;-fx-font-weight:bold;");

        VBox layout = new VBox(12);
        layout.setPadding(new Insets(20));
        layout.setStyle("-fx-background-color:#0e1628;");
        layout.getChildren().addAll(
                new Label("🔄  Re-enrolamiento: " + usuarioSeleccionado.getNombreCompleto()) {{
                    setStyle("-fx-text-fill:#e8edf5;-fx-font-size:14px;-fx-font-weight:bold;");
                }},
                lblTituloSelector, cmbCamara,
                stackCam,
                lblEstado, pb, lblMuestras,
                filaBotones
        );

        // ── Estado interno del diálogo ──
        AtomicBoolean camaraActiva   = new AtomicBoolean(false);
        AtomicBoolean enrolando      = new AtomicBoolean(false);
        int[] indiceSeleccionado     = { 0 };
        double[] guiaPhase           = { 0.0 };

        // Animación guía facial
        javafx.animation.AnimationTimer timerGuia = new javafx.animation.AnimationTimer() {
            @Override public void handle(long now) {
                guiaPhase[0] += 0.04;
                double w = canvasGuia.getWidth(), h = canvasGuia.getHeight();
                GraphicsContext gc = canvasGuia.getGraphicsContext2D();
                gc.clearRect(0, 0, w, h);
                if (!camaraActiva.get()) return;
                double cx = w/2, cy = h/2-10, rx = 72, ry = 90;
                double pulse = 1.0 + 0.015 * Math.sin(guiaPhase[0]);
                double erx = rx*pulse, ery = ry*pulse;
                gc.setStroke(Color.web("#00d4ff", 0.8)); gc.setLineWidth(2.2);
                gc.strokeOval(cx-erx, cy-ery, erx*2, ery*2);
                double scanY = cy-ery+(ery*2)*((Math.sin(guiaPhase[0]*0.7)+1)/2);
                double halfW = erx*Math.sqrt(Math.max(0,1-Math.pow((scanY-cy)/ery,2)));
                if (halfW > 2) {
                    gc.setStroke(Color.web("#00d4ff", 0.5+0.3*Math.abs(Math.sin(guiaPhase[0]*0.7))));
                    gc.setLineWidth(1.5); gc.strokeLine(cx-halfW, scanY, cx+halfW, scanY);
                }
            }
        };
        timerGuia.start();

        // ── Lógica Iniciar Cámara ──
        Runnable iniciarCamara = () -> {
            int idx = cmbCamara.getSelectionModel().getSelectedIndex();
            if (idx < 0) idx = 0;
            indiceSeleccionado[0] = idx;

            btnIniciarCam.setDisable(true);
            lblEstado.setText("🔄  Iniciando cámara [" + nombresCamaras.get(idx) + "]...");
            lblEstado.setStyle("-fx-text-fill:#f1c40f;-fx-font-size:12px;");

            final int idxFinal = idx;
            new Thread(() -> {
                if (idxFinal >= 0) ReconocimientoFacialService.setIndiceCamara(idxFinal);
                boolean ok = CamaraService.iniciarPreviewLocal(imgPreview, idxFinal);
                Platform.runLater(() -> {
                    if (ok) {
                        camaraActiva.set(true);
                        lblPlaceholder.setVisible(false);
                        btnIniciarCam.setDisable(true);
                        btnDetenerCam.setDisable(false);
                        btnEnrolar.setDisable(false);
                        cmbCamara.setDisable(true);
                        lblEstado.setText("✅  Cámara activa — " + nombresCamaras.get(idxFinal));
                        lblEstado.setStyle("-fx-text-fill:#27ae60;-fx-font-size:12px;");
                    } else {
                        camaraActiva.set(false);
                        btnIniciarCam.setDisable(false);
                        lblEstado.setText("❌  No se pudo abrir esa cámara. Selecciona otra.");
                        lblEstado.setStyle("-fx-text-fill:#e74c3c;-fx-font-size:12px;");
                    }
                });
            }, "cam-init") {{ setDaemon(true); }}.start();
        };

        Runnable detenerCamara = () -> {
            ReconocimientoFacialService.cancelarEnrolamiento();
            CamaraService.detener();
            camaraActiva.set(false);
            enrolando.set(false);
            lblPlaceholder.setVisible(true);
            btnIniciarCam.setDisable(false);
            btnDetenerCam.setDisable(true);
            btnEnrolar.setDisable(true);
            cmbCamara.setDisable(false);
            pb.setVisible(false);
            pb.setProgress(0);
            lblEstado.setText("⏸  Cámara detenida");
            lblEstado.setStyle("-fx-text-fill:#8a9bbf;-fx-font-size:12px;");
            lblMuestras.setText("Muestras: 0 / 80");
        };

        btnIniciarCam.setOnAction(e -> iniciarCamara.run());
        btnDetenerCam.setOnAction(e -> {
            if (enrolando.get()) {
                ReconocimientoFacialService.cancelarEnrolamiento();
            }
            detenerCamara.run();
        });

        // ── Lógica Enrolamiento ──
        int idUsuario = usuarioSeleccionado.getIdUsuario();
        btnEnrolar.setOnAction(e -> {
            if (!camaraActiva.get()) { lblEstado.setText("⚠  Primero inicia la cámara"); return; }
            enrolando.set(true);
            btnEnrolar.setDisable(true);
            btnIniciarCam.setDisable(true);
            cmbCamara.setDisable(true);
            pb.setVisible(true);
            pb.setProgress(0);

            new Thread(() -> {
                try {
                    // Borrar muestras anteriores en BD
                    try (var con = app.conexion.Conexion.nuevaConexion();
                         var ps2 = con.prepareStatement("DELETE FROM muestras_faciales WHERE id_usuario=?")) {
                        ps2.setInt(1, idUsuario);
                        ps2.executeUpdate();
                    }
                    // Borrar carpeta de rostros en disco
                    File carpetaRostros = new File("data/rostros/" + idUsuario + "/");
                    if (carpetaRostros.exists()) {
                        File[] archivos = carpetaRostros.listFiles();
                        if (archivos != null) for (File f : archivos) f.delete();
                    }

                    ReconocimientoFacialService.resetCancelacion();

                    // Fases de enrolamiento con instrucciones visuales
                    String[][] fases = {
                            {"😐  Mira al frente",              "30"},
                            {"👈  Gira levemente a la izquierda","20"},
                            {"👉  Gira levemente a la derecha",  "20"},
                            {"🙂  Inclina levemente la cabeza",  "10"}
                    };
                    int totalMuestras = 80;
                    int acumuladas    = 0;

                    for (int f2 = 0; f2 < fases.length; f2++) {
                        if (ReconocimientoFacialService.isCancelado()) break;
                        final String instruccion  = fases[f2][0];
                        final int    muestrasFase = Integer.parseInt(fases[f2][1]);
                        final int    faseNum      = f2 + 1;

                        // Cuenta regresiva
                        for (int c = 3; c >= 1; c--) {
                            if (ReconocimientoFacialService.isCancelado()) break;
                            final int cc = c;
                            Platform.runLater(() -> {
                                lblEstado.setText(instruccion + "   ⏳ Prepárate... " + cc);
                                lblEstado.setStyle("-fx-text-fill:#f1c40f;-fx-font-size:12px;");
                            });
                            Thread.sleep(1000);
                        }
                        if (ReconocimientoFacialService.isCancelado()) break;

                        Platform.runLater(() -> {
                            lblEstado.setText("📡 Fase " + faseNum + "/4 — " + instruccion);
                            lblEstado.setStyle("-fx-text-fill:#4a90e2;-fx-font-size:12px;");
                        });

                        // Enrolar esta fase usando el CamaraService activo
                        int cap = ReconocimientoFacialService.enrolarDesdeFramesSarxos(
                                idUsuario, muestrasFase, acumuladas, null, null);
                        acumuladas += cap;
                        final int total = acumuladas;
                        final double prog = (double) total / totalMuestras;
                        Platform.runLater(() -> {
                            pb.setProgress(prog);
                            lblMuestras.setText("Muestras: " + total + " / " + totalMuestras);
                            lblEstado.setText("✅ Fase " + faseNum + " — " + total + " muestras");
                            lblEstado.setStyle("-fx-text-fill:#27ae60;-fx-font-size:12px;");
                        });
                        Thread.sleep(500);
                    }

                    if (acumuladas > 0) {
                        ReconocimientoFacialService.entrenarModelo();
                        ReconocimientoFacialService.inicializar();
                    }
                    AuditoriaDAO.registrarAccion(
                            "Re-enrolamiento facial: " + usuarioSeleccionado.getNombreCompleto()
                                    + " (" + acumuladas + " muestras)", "LISTA_USUARIOS");

                    final int totalFinal = acumuladas;
                    Platform.runLater(() -> {
                        enrolando.set(false);
                        pb.setProgress(1.0);
                        if (totalFinal > 0) {
                            lblEstado.setText("🎉  ¡Listo! " + totalFinal + " muestras capturadas. Modelo actualizado.");
                            lblEstado.setStyle("-fx-text-fill:#27ae60;-fx-font-size:12px;-fx-font-weight:bold;");
                            btnEnrolar.setText("🔄  Re-enrolar de nuevo");
                            btnEnrolar.setDisable(false);
                        } else {
                            lblEstado.setText("❌  0 muestras. Asegúrate de que tu rostro sea visible en la cámara.");
                            lblEstado.setStyle("-fx-text-fill:#e74c3c;-fx-font-size:12px;");
                            btnEnrolar.setDisable(false);
                        }
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        enrolando.set(false);
                        lblEstado.setText("❌  Error: " + ex.getMessage());
                        lblEstado.setStyle("-fx-text-fill:#e74c3c;-fx-font-size:12px;");
                        btnEnrolar.setDisable(false);
                    });
                }
            }, "reenrolar-thread") {{ setDaemon(true); }}.start();
        });

        btnCancelar.setOnAction(e -> {
            ReconocimientoFacialService.cancelarEnrolamiento();
            timerGuia.stop();
            CamaraService.detener();
            ventana.close();
        });

        ventana.setOnCloseRequest(e -> {
            ReconocimientoFacialService.cancelarEnrolamiento();
            timerGuia.stop();
            CamaraService.detener();
        });

        Scene escena = new Scene(layout, 460, 580);
        ventana.setScene(escena);
        ventana.show();
    }

    private TextField campo(String val, String style, double w) {
        TextField tf = new TextField(val);
        tf.setStyle(style); tf.setPrefWidth(w);
        return tf;
    }

    private void addRow(javafx.scene.layout.GridPane g, int row, String label,
                        String lblStyle, javafx.scene.Node field) {
        Label l = new Label(label); l.setStyle(lblStyle);
        g.add(l, 0, row); g.add(field, 1, row);
    }

    @FXML public void actualizarFoto() {
        if (usuarioSeleccionado == null) { alerta("Selecciona un usuario"); return; }
        FileChooser fc = new FileChooser();
        fc.setTitle("Seleccionar nueva foto");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Imágenes", "*.jpg", "*.jpeg", "*.png"));
        File archivo = fc.showOpenDialog(tablaPersonas.getScene().getWindow());
        if (archivo == null) return;
        new Thread(() -> {
            try {
                String carpeta = "data/fotos/";
                new File(carpeta).mkdirs();
                String destino = carpeta + usuarioSeleccionado.getNombre().replaceAll("\\s+","_")
                        + "_" + System.currentTimeMillis() + ".jpg";
                Files.copy(archivo.toPath(), new File(destino).toPath(), StandardCopyOption.REPLACE_EXISTING);
                UsuarioDAO.actualizarFoto(usuarioSeleccionado.getIdUsuario(), destino);
                usuarioSeleccionado.setFoto(destino);
                // Regenerar carnet con nueva foto
                FirmaService.inicializar();
                String firma = FirmaService.generarFirmaCompleta(
                        usuarioSeleccionado.getIdUsuario(), usuarioSeleccionado.getNombreCompleto(),
                        usuarioSeleccionado.getCarne() != null ? usuarioSeleccionado.getCarne() : "",
                        usuarioSeleccionado.getCorreo());
                QRService.generarPaginaVerificacion(usuarioSeleccionado, firma);
                QRService.generarCodigoQR(usuarioSeleccionado.getIdUsuario(),
                        usuarioSeleccionado.getNombreCompleto(), usuarioSeleccionado.getCarne());
                String rutaPDF = PDFService.generarCarnet(usuarioSeleccionado);
                Platform.runLater(() -> {
                    mostrarDetalle(usuarioSeleccionado);
                    cargarUsuarios();
                    info("✅ Foto actualizada\n✅ Carnet regenerado con la nueva foto");
                    if (rutaPDF != null) try { Desktop.getDesktop().open(new File(rutaPDF)); } catch (Exception ignored) {}
                });
            } catch (Exception e) {
                Platform.runLater(() -> alerta("❌ Error actualizando foto: " + e.getMessage()));
            }
        }, "foto-thread") {{ setDaemon(true); }}.start();
    }
}