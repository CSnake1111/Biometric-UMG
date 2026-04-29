package app.controladores;

import app.dao.AsistenciaDAO;
import app.dao.AuditoriaDAO;
import app.dao.CursoDAO;
import app.dao.RegistroIngresoDAO;
import app.dao.UsuarioDAO;
import app.modelos.Asistencia;
import app.modelos.Curso;
import app.modelos.Usuario;
import app.servicios.CamaraService;
import app.servicios.EmailService;
import app.servicios.PDFService;
import app.servicios.ReconocimientoFacialService;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.awt.Desktop;
import java.io.File;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ArbolAsistenciasController implements Initializable, Liberable {

    // ── Árbol ────────────────────────────────────────────────────────
    @FXML private ComboBox<Curso>   cmbCurso;
    @FXML private Label             lblInfoCurso;
    @FXML private VBox              contenedorArbol;
    @FXML private Label             lblPresentes;
    @FXML private Label             lblAusentes;
    @FXML private Label             lblTardanzas;
    @FXML private Button            btnConfirmar;
    @FXML private Button            btnPDF;
    @FXML private ProgressIndicator loading;
    @FXML private Label             lblEstado;

    // ── Panel biométrico integrado ───────────────────────────────────
    @FXML private VBox      panelBiometrico;
    @FXML private ImageView imgCamaraBio;
    @FXML private Label     lblEstadoBio;
    @FXML private Button    btnIniciarBio;
    @FXML private Button    btnDetenerBio;

    // ── Panel identificado ───────────────────────────────────────────
    @FXML private VBox      panelIdentificado;
    @FXML private ImageView imgPersonaBio;
    @FXML private Label     lblNombreBio;
    @FXML private Label     lblEstadoAsistencia;

    // ── Panel asistencia manual ──────────────────────────────────────
    @FXML private VBox      panelManual;
    @FXML private VBox      listaManual;

    private int cursoIdSalon = -1;
    private int ultimoCursoAvisado = -1;

    private ScheduledExecutorService scheduler;
    private final AtomicBoolean enPausa = new AtomicBoolean(false);
    private boolean bioActivo = false;
    private static final long PAUSA_MS = 3_500;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        cargarCursos();
        cmbCurso.setOnAction(e -> cargarArbol());
        ReconocimientoFacialService.inicializar();
        ocultarPanelBio();
        if (panelManual != null) {
            panelManual.setVisible(false);
            panelManual.setManaged(false);
        }
    }

    @Override
    public void liberarRecursos() { detenerBiometrico(); }

    // ════════════════════════════════════════════════════════════════
    //  FIX: setCurso() — llamado desde DashboardCatedraticoController
    //  cuando el catedrático hace clic en un curso del sidebar.
    //  Selecciona el curso en el ComboBox y recarga el árbol.
    // ════════════════════════════════════════════════════════════════
    public void setCurso(Curso curso) {
        if (curso == null) return;
        // Buscar el mismo id en el ComboBox (evita duplicados por referencia)
        cmbCurso.getItems().stream()
                .filter(c -> c.getIdCurso() == curso.getIdCurso())
                .findFirst()
                .ifPresentOrElse(
                        c -> { cmbCurso.setValue(c); cargarArbol(); },
                        () -> {
                            // Si por alguna razón no está en la lista, agregarlo
                            cmbCurso.getItems().add(0, curso);
                            cmbCurso.setValue(curso);
                            cargarArbol();
                        }
                );
    }

    // ════════════════════════════════════════════════════════════════
    //  CARGA DE CURSOS Y ÁRBOL
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

    private void cargarCursos() {
        var sesion = UsuarioDAO.getSesionActiva();
        List<Curso> cursos = (sesion != null && "Catedratico".equals(sesion.getNombreRol()))
                ? CursoDAO.listarPorCatedratico(sesion.getIdPersona())
                : CursoDAO.listarTodos();
        cmbCurso.getItems().setAll(cursos);
        if (!cursos.isEmpty()) { cmbCurso.setValue(cursos.get(0)); cargarArbol(); }
    }

    private void cargarArbol() {
        Curso c = cmbCurso.getValue();
        if (c == null) return;
        cursoIdSalon = c.getIdSalon();

        if (loading != null) { loading.setVisible(true); loading.setManaged(true); }
        contenedorArbol.getChildren().clear();

        lblInfoCurso.setText(c.getNombreCurso() + " [" + c.getSeccion() + "]"
                + (c.getHorario().isBlank() ? "" : "  —  " + c.getHorario()));

        new Thread(() -> {
            List<Usuario>    inscritos = CursoDAO.listarEstudiantesInscritos(c.getIdCurso());
            List<Asistencia> lista     = AsistenciaDAO.obtenerAsistenciasHoy(c.getIdCurso());
            int[]            stats     = AsistenciaDAO.contarEstados(c.getIdCurso(), LocalDate.now());

            if (ultimoCursoAvisado != c.getIdCurso() && !inscritos.isEmpty()) {
                ultimoCursoAvisado = c.getIdCurso();
                String fecha = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                EmailService.enviarAvisoAsistencia(inscritos, c, fecha);
                Platform.runLater(() ->
                        lblEstado.setText("📧 Aviso de asistencia enviado a " + inscritos.size() + " estudiante(s).")
                );
            }

            Platform.runLater(() -> {
                if (loading != null) { loading.setVisible(false); loading.setManaged(false); }
                lblPresentes.setText("✅ Present: " + stats[0]);
                lblAusentes .setText("❌ Absent: "  + stats[1]);
                lblTardanzas.setText("⏰ Late: "    + stats[2]);

                if (lista.isEmpty() && !inscritos.isEmpty()) {
                    construirArbolDesdeInscritos(inscritos, c);
                } else {
                    construirArbol(lista, c);
                }

                // FIX: actualizar también el panel manual si está visible
                if (panelManual != null && panelManual.isVisible()) {
                    construirPanelManual(inscritos, lista, c);
                }
            });
        }, "load-arbol") {{ setDaemon(true); }}.start();
    }

    private void construirArbolDesdeInscritos(List<Usuario> inscritos, Curso c) {
        Label root = new Label("🏫 " + c.getNombreCurso() + " — " + LocalDate.now()
                + "  (sin asistencia registrada aún)");
        root.setStyle("-fx-background-color:#1a3a5c;-fx-text-fill:white;"
                + "-fx-padding:10 20;-fx-background-radius:8;"
                + "-fx-font-weight:bold;-fx-font-size:13px;");
        VBox rootBox = new VBox(8, root);
        rootBox.setAlignment(Pos.CENTER);

        FlowPane nodesPane = new FlowPane();
        nodesPane.setHgap(12); nodesPane.setVgap(12);
        nodesPane.setPadding(new Insets(10));
        nodesPane.setAlignment(Pos.CENTER);

        for (Usuario u : inscritos) {
            nodesPane.getChildren().add(crearNodoInscrito(u));
        }

        rootBox.getChildren().add(nodesPane);
        contenedorArbol.getChildren().setAll(rootBox);
    }

    private VBox crearNodoInscrito(Usuario u) {
        VBox box = new VBox(6);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(10));
        box.setPrefWidth(140);
        box.setStyle("-fx-background-color:#1a2a3a;"
                + "-fx-border-color:#3b82f6;"
                + "-fx-border-width:2;-fx-background-radius:10;-fx-border-radius:10;");

        ImageView photo = new ImageView();
        photo.setFitWidth(56); photo.setFitHeight(56); photo.setPreserveRatio(true);
        if (u.getFoto() != null) {
            try {
                Image img = cargarImagenFoto(u.getFoto());
                if (img != null) photo.setImage(img);
            } catch (Exception ignored) {}
        }
        photo.setClip(new Circle(28, 28, 28));

        Label status = new Label("⏳");
        status.setStyle("-fx-font-size:16px;");

        Label lbl = new Label(u.getNombre() + "\n" + u.getApellido());
        lbl.setStyle("-fx-text-fill:white;-fx-font-size:11px;-fx-alignment:center;");
        lbl.setWrapText(true); lbl.setMaxWidth(120);

        Label sinReg = new Label("Sin registro");
        sinReg.setStyle("-fx-text-fill:#94a3b8;-fx-font-size:10px;");

        box.getChildren().addAll(photo, status, lbl, sinReg);
        Tooltip.install(box, new Tooltip("Carné: " + (u.getCarne() != null ? u.getCarne() : "")
                + "\n" + (u.getCorreo() != null ? u.getCorreo() : "")
                + "\nEstado: Sin registro hoy"));
        return box;
    }

    private void construirArbol(List<Asistencia> lista, Curso c) {
        Label root = new Label("🏫 " + c.getNombreCurso() + " — " + LocalDate.now());
        root.setStyle("-fx-background-color:#1a4f8c;-fx-text-fill:white;"
                + "-fx-padding:10 20;-fx-background-radius:8;"
                + "-fx-font-weight:bold;-fx-font-size:13px;");
        VBox rootBox = new VBox(8, root);
        rootBox.setAlignment(Pos.CENTER);

        if (lista.isEmpty()) {
            Label empty = new Label("Sin registros hoy. Inicia la cámara biométrica, usa 'Asistencia Manual' o presiona 'Confirmar Asistencia'.");
            empty.setStyle("-fx-text-fill:#4a5a78;-fx-font-size:12px;");
            rootBox.getChildren().add(empty);
            contenedorArbol.getChildren().setAll(rootBox);
            return;
        }

        FlowPane nodesPane = new FlowPane();
        nodesPane.setHgap(12); nodesPane.setVgap(12);
        nodesPane.setPadding(new Insets(10));
        nodesPane.setAlignment(Pos.CENTER);
        for (Asistencia a : lista) nodesPane.getChildren().add(crearNodo(a));
        rootBox.getChildren().add(nodesPane);
        contenedorArbol.getChildren().setAll(rootBox);
    }

    private VBox crearNodo(Asistencia a) {
        boolean presente = a.isPresente();
        boolean tardanza = a.isTardanza();
        String bgColor = presente ? "#1a4a2a" : tardanza ? "#4a3a10" : "#4a1a1a";
        String bdColor = presente ? "#27ae60" : tardanza ? "#f39c12" : "#e74c3c";

        VBox box = new VBox(5);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(10));
        box.setPrefWidth(150);
        box.setStyle("-fx-background-color:" + bgColor + ";"
                + "-fx-border-color:" + bdColor + ";"
                + "-fx-border-width:2;-fx-background-radius:10;-fx-border-radius:10;");

        // Foto circular
        ImageView photo = new ImageView();
        photo.setFitWidth(60); photo.setFitHeight(60); photo.setPreserveRatio(true);
        if (a.getPersona() != null && a.getPersona().getFoto() != null) {
            try {
                Image img = cargarImagenFoto(a.getPersona().getFoto());
                if (img != null) photo.setImage(img);
            } catch (Exception ignored) {}
        }
        Circle clip = new Circle(30, 30, 30);
        photo.setClip(clip);

        // Estado
        String icon = presente ? "✅" : tardanza ? "⏰" : "❌";
        Label status = new Label(icon);
        status.setStyle("-fx-font-size:14px;");

        // Nombre
        String nombre = a.getPersona() != null
                ? a.getPersona().getNombre() + "\n" + a.getPersona().getApellido()
                : "ID:" + a.getIdEstudiante();
        Label lbl = new Label(nombre);
        lbl.setStyle("-fx-text-fill:white;-fx-font-size:11px;-fx-alignment:center;-fx-font-weight:bold;");
        lbl.setWrapText(true); lbl.setMaxWidth(130);

        box.getChildren().addAll(photo, status, lbl);

        // Carné
        if (a.getPersona() != null && a.getPersona().getCarne() != null
                && !a.getPersona().getCarne().isBlank()) {
            Label lCarne = new Label("🪪 " + a.getPersona().getCarne());
            lCarne.setStyle("-fx-text-fill:#8a9bbf;-fx-font-size:10px;");
            lCarne.setWrapText(true); lCarne.setMaxWidth(130);
            box.getChildren().add(lCarne);
        }

        // Carrera
        if (a.getPersona() != null && a.getPersona().getCarrera() != null
                && !a.getPersona().getCarrera().isBlank()) {
            Label lCarrera = new Label("📚 " + a.getPersona().getCarrera());
            lCarrera.setStyle("-fx-text-fill:#8a9bbf;-fx-font-size:10px;");
            lCarrera.setWrapText(true); lCarrera.setMaxWidth(130);
            box.getChildren().add(lCarrera);
        }

        // Hora de ingreso
        if (a.getHoraIngreso() != null && !a.getHoraIngreso().isBlank()) {
            Label hora = new Label("🕐 " + a.getHoraIngreso().substring(0, Math.min(5, a.getHoraIngreso().length())));
            hora.setStyle("-fx-text-fill:#aaa;-fx-font-size:10px;");
            box.getChildren().add(hora);
        }

        String carne  = a.getPersona() != null ? a.getPersona().getCarne()  : "";
        String correo = a.getPersona() != null ? a.getPersona().getCorreo() : "";
        Tooltip.install(box, new Tooltip("Carné: " + carne + "\n" + correo + "\nEstado: " + a.getEstado()));
        return box;
    }

    // ════════════════════════════════════════════════════════════════
    //  PANEL ASISTENCIA MANUAL
    //  Muestra una fila por estudiante con botones Presente/Tarde/Ausente
    // ════════════════════════════════════════════════════════════════

    @FXML
    public void togglePanelManual() {
        if (panelManual == null) return;
        boolean visible = !panelManual.isVisible();
        panelManual.setVisible(visible);
        panelManual.setManaged(visible);

        if (visible) {
            Curso c = cmbCurso.getValue();
            if (c == null) return;
            new Thread(() -> {
                List<Usuario>    inscritos = CursoDAO.listarEstudiantesInscritos(c.getIdCurso());
                List<Asistencia> lista     = AsistenciaDAO.obtenerAsistenciasHoy(c.getIdCurso());
                Platform.runLater(() -> construirPanelManual(inscritos, lista, c));
            }, "load-manual") {{ setDaemon(true); }}.start();
        }
    }

    private void construirPanelManual(List<Usuario> inscritos, List<Asistencia> asistencias, Curso curso) {
        if (listaManual == null) return;
        listaManual.getChildren().clear();

        String hora = LocalDateTime.now().format(FMT);

        for (Usuario u : inscritos) {
            // Estado actual (si ya tiene asistencia hoy)
            String estadoActual = asistencias.stream()
                    .filter(a -> a.getIdEstudiante() == u.getIdUsuario())
                    .map(Asistencia::getEstado)
                    .findFirst()
                    .orElse("SIN REGISTRO");

            HBox fila = new HBox(10);
            fila.setAlignment(Pos.CENTER_LEFT);
            fila.setPadding(new Insets(6, 12, 6, 12));
            fila.setStyle("-fx-background-color:#1a2744;-fx-background-radius:8;");

            // Foto miniatura
            ImageView mini = new ImageView();
            mini.setFitWidth(36); mini.setFitHeight(36); mini.setPreserveRatio(true);
            mini.setClip(new Circle(18, 18, 18));
            if (u.getFoto() != null) {
                try { Image img = cargarImagenFoto(u.getFoto()); if (img != null) mini.setImage(img); }
                catch (Exception ignored) {}
            }

            // Nombre + estado actual
            VBox info = new VBox(2);
            Label lblNombre = new Label(u.getNombreCompleto());
            lblNombre.setStyle("-fx-text-fill:white;-fx-font-size:12px;-fx-font-weight:bold;");
            Label lblEstadoActual = new Label(estadoActual);
            lblEstadoActual.setStyle("-fx-text-fill:" + colorEstado(estadoActual) + ";-fx-font-size:10px;");
            info.getChildren().addAll(lblNombre, lblEstadoActual);
            HBox.setHgrow(info, Priority.ALWAYS);

            // Botones de acción
            Button btnP = botonEstado("✅ Presente", "#27ae60");
            Button btnT = botonEstado("⏰ Tarde",    "#f39c12");
            Button btnA = botonEstado("❌ Ausente",  "#e74c3c");

            btnP.setOnAction(e -> registrarManual(curso, u, "PRESENTE",  hora, lblEstadoActual));
            btnT.setOnAction(e -> registrarManual(curso, u, "TARDANZA",  hora, lblEstadoActual));
            btnA.setOnAction(e -> registrarManual(curso, u, "AUSENTE",   null, lblEstadoActual));

            fila.getChildren().addAll(mini, info, btnP, btnT, btnA);
            listaManual.getChildren().add(fila);
        }

        if (inscritos.isEmpty()) {
            Label vacio = new Label("No hay estudiantes inscritos en este curso.");
            vacio.setStyle("-fx-text-fill:#4a5a78;-fx-padding:10;");
            listaManual.getChildren().add(vacio);
        }
    }

    private void registrarManual(Curso curso, Usuario est, String estado, String hora, Label lblEstadoActual) {
        new Thread(() -> {
            // FIX: usar registrarEstadoManual() para todos los estados.
            // El método anterior llamaba registrarPresente() para PRESENTE/TARDANZA,
            // lo cual recalculaba el estado según la hora (ignorando lo que el
            // catedrático eligió) y además no podía sobreescribir estados previos.
            // registrarEstadoManual() respeta el estado elegido y hace upsert completo.
            String horaFinal = hora != null ? hora : LocalDateTime.now().format(FMT);
            boolean ok = AsistenciaDAO.registrarEstadoManual(
                    curso.getIdCurso(),
                    est.getIdUsuario(),
                    estado,
                    "AUSENTE".equals(estado) ? null : horaFinal
            );
            Platform.runLater(() -> {
                if (ok) {
                    lblEstadoActual.setText(estado);
                    lblEstadoActual.setStyle("-fx-text-fill:" + colorEstado(estado) + ";-fx-font-size:10px;");
                    cargarArbol();
                } else {
                    lblEstado.setText("❌ Error al registrar asistencia manual.");
                }
            });
        }, "manual-reg") {{ setDaemon(true); }}.start();
    }

    private Button botonEstado(String texto, String color) {
        Button b = new Button(texto);
        b.setStyle("-fx-background-color:transparent;-fx-border-color:" + color + ";"
                + "-fx-border-radius:5;-fx-background-radius:5;-fx-text-fill:" + color + ";"
                + "-fx-font-size:11px;-fx-padding:4 10;-fx-cursor:hand;");
        b.setOnMouseEntered(e -> b.setStyle("-fx-background-color:" + color + "20;"
                + "-fx-border-color:" + color + ";"
                + "-fx-border-radius:5;-fx-background-radius:5;-fx-text-fill:" + color + ";"
                + "-fx-font-size:11px;-fx-padding:4 10;-fx-cursor:hand;"));
        b.setOnMouseExited(e -> b.setStyle("-fx-background-color:transparent;"
                + "-fx-border-color:" + color + ";"
                + "-fx-border-radius:5;-fx-background-radius:5;-fx-text-fill:" + color + ";"
                + "-fx-font-size:11px;-fx-padding:4 10;-fx-cursor:hand;"));
        return b;
    }

    private String colorEstado(String estado) {
        return switch (estado) {
            case "PRESENTE"    -> "#27ae60";
            case "TARDANZA"    -> "#f39c12";
            case "AUSENTE"     -> "#e74c3c";
            default            -> "#8a9bbf";
        };
    }

    // ════════════════════════════════════════════════════════════════
    //  RECONOCIMIENTO BIOMÉTRICO
    // ════════════════════════════════════════════════════════════════

    @FXML public void iniciarBiometrico() {
        Curso c = cmbCurso.getValue();
        if (c == null) { lblEstado.setText("⚠ Selecciona un curso primero."); return; }
        if (!ReconocimientoFacialService.isEntrenado()) { lblEstado.setText("⚠ Modelo facial no entrenado."); return; }
        // Usar índice de cámara compartida si ya fue seleccionado en otra pantalla
        int idxBio = ReconocimientoFacialService.getIndiceCamara();
        boolean ok = CamaraService.iniciarPreviewLocal(imgCamaraBio, idxBio);
        if (!ok) { lblEstado.setText("❌ Cámara no disponible."); return; }

        mostrarPanelBio();
        bioActivo = true;
        enPausa.set(false);
        lblEstadoBio.setText("🟢 Escaneando — " + c.getNombreCurso());
        btnIniciarBio.setDisable(true);
        btnDetenerBio.setDisable(false);

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "bio-asistencia");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::reconocer, 1, 1, TimeUnit.SECONDS);
    }

    @FXML public void detenerBiometrico() {
        bioActivo = false;
        enPausa.set(false);
        if (scheduler != null) {
            scheduler.shutdownNow();
            try { scheduler.awaitTermination(400, TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) {}
            scheduler = null;
        }
        ReconocimientoFacialService.liberarCamaraCompartida();
        CamaraService.detener();
        Platform.runLater(() -> {
            ocultarPanelBio();
            if (btnIniciarBio != null) btnIniciarBio.setDisable(false);
            if (btnDetenerBio != null) btnDetenerBio.setDisable(true);
            lblEstadoBio.setText("Cámara inactiva");
        });
    }

    private void reconocer() {
        if (!bioActivo || enPausa.get()) return;
        java.awt.image.BufferedImage frame = CamaraService.capturarFrame();
        int id = (frame != null)
                ? ReconocimientoFacialService.identificarDesdeFrame(frame)
                : ReconocimientoFacialService.identificarDesdeCamera();
        if (id == -1) return;
        Usuario u = UsuarioDAO.buscarPorIdPersona(id);
        if (u == null) return;
        Curso c = cmbCurso.getValue();
        if (c == null) return;
        boolean inscrito = CursoDAO.listarEstudiantesInscritos(c.getIdCurso())
                .stream().anyMatch(est -> est.getIdUsuario() == u.getIdUsuario());
        if (!inscrito) return;
        String hora = LocalDateTime.now().format(FMT);
        RegistroIngresoDAO.registrarPorSalon(u.getIdUsuario(), c.getIdSalon());
        AsistenciaDAO.registrarPresente(c.getIdCurso(), u.getIdUsuario(), hora);
        enPausa.set(true);
        Platform.runLater(() -> {
            mostrarIdentificado(u, hora);
            cargarArbol();
            PauseTransition espera = new PauseTransition(Duration.millis(PAUSA_MS));
            espera.setOnFinished(e -> {
                ocultarIdentificado();
                if (bioActivo) enPausa.set(false);
            });
            espera.play();
        });
    }

    private void mostrarIdentificado(Usuario u, String hora) {
        if (panelIdentificado == null) return;
        panelIdentificado.setVisible(true);
        panelIdentificado.setManaged(true);
        Image fotoImg = cargarImagenFoto(u.getFoto());
        if (fotoImg != null) imgPersonaBio.setImage(fotoImg);
        lblNombreBio.setText(u.getNombreCompleto());
        lblEstadoAsistencia.setText("✅ Asistencia registrada — " + hora);
        ScaleTransition st = new ScaleTransition(Duration.millis(280), panelIdentificado);
        st.setFromX(0.85); st.setToX(1); st.setFromY(0.85); st.setToY(1);
        st.play();
    }

    private void ocultarIdentificado() {
        if (panelIdentificado == null) return;
        panelIdentificado.setVisible(false);
        panelIdentificado.setManaged(false);
        imgPersonaBio.setImage(null);
    }

    private void mostrarPanelBio() {
        if (panelBiometrico == null) return;
        panelBiometrico.setVisible(true);
        panelBiometrico.setManaged(true);
    }

    private void ocultarPanelBio() {
        if (panelBiometrico == null) return;
        panelBiometrico.setVisible(false);
        panelBiometrico.setManaged(false);
        ocultarIdentificado();
    }

    // ════════════════════════════════════════════════════════════════
    //  CONFIRMAR ASISTENCIA MANUAL GLOBAL
    // ════════════════════════════════════════════════════════════════

    @FXML public void confirmarAsistencia() {
        Curso c = cmbCurso.getValue();
        if (c == null) return;
        var sesion = UsuarioDAO.getSesionActiva();
        int idCat = sesion != null ? sesion.getIdPersona() : -1;

        Alert conf = new Alert(Alert.AlertType.CONFIRMATION,
                "Confirmar asistencia para: " + c.getNombreCurso() + "?\n"
                        + "Los estudiantes sin registro serán marcados como AUSENTE.",
                ButtonType.YES, ButtonType.NO);
        conf.setHeaderText("Confirmar Asistencia");
        conf.showAndWait().ifPresent(bt -> {
            if (bt != ButtonType.YES) return;
            lblEstado.setText("⏳ Procesando...");
            new Thread(() -> {
                boolean ok = AsistenciaDAO.confirmarAsistencia(c.getIdCurso(), c.getIdSalon(), idCat);
                AuditoriaDAO.registrar("CONFIRMAR_ASISTENCIA", "ASISTENCIAS",
                        "Asistencia confirmada: " + c.getNombreCurso() + " [" + c.getSeccion() + "]");
                Platform.runLater(() -> {
                    lblEstado.setText(ok ? "✅ Asistencia confirmada." : "❌ Error al confirmar.");
                    if (ok) cargarArbol();
                });
            }, "confirmar-asistencia") {{ setDaemon(true); }}.start();
        });
    }

    // ════════════════════════════════════════════════════════════════
    //  GENERAR PDF
    // ════════════════════════════════════════════════════════════════

    @FXML public void generarPDF() {
        Curso c = cmbCurso.getValue();
        if (c == null) return;
        lblEstado.setText("⏳ Generando PDF...");
        var sesion = UsuarioDAO.getSesionActiva();

        new Thread(() -> {
            List<Asistencia> lista = AsistenciaDAO.obtenerAsistenciasHoy(c.getIdCurso());
            String ruta = PDFService.generarListaAsistencia(c, lista, LocalDate.now());
            if (ruta != null && sesion != null && sesion.getCorreo() != null) {
                EmailService.enviarListaAsistencia(sesion.getCorreo(), c, ruta);
            }
            AuditoriaDAO.registrar("GENERAR_PDF_ASISTENCIA", "ASISTENCIAS",
                    "PDF generado: " + c.getNombreCurso());
            Platform.runLater(() -> {
                if (ruta != null) {
                    lblEstado.setText("✅ PDF: " + ruta);
                    try { Desktop.getDesktop().open(new File(ruta)); } catch (Exception ignored) {}
                } else {
                    lblEstado.setText("❌ No se pudo generar el PDF.");
                }
            });
        }, "pdf-asistencia") {{ setDaemon(true); }}.start();
    }
}