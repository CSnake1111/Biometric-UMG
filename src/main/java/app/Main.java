package app;

import app.conexion.Conexion;
import app.servicios.IdiomaService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import app.controladores.Liberable;
import app.servicios.CamaraService;
import app.servicios.ReconocimientoFacialService;
import app.servicios.ServidorVerificacion;

import java.io.IOException;
import java.util.Objects;

public class Main extends Application {

    public static Stage primaryStage;

    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;

        stage.setOnCloseRequest(e -> { e.consume(); cerrarAplicacion(); });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            CamaraService.detener();
            ReconocimientoFacialService.liberarCamaraCompartida();
        }, "shutdown-hook"));

        ServidorVerificacion.iniciar();
        app.servicios.BackupService.iniciarBackupAutomatico(6);

        if (Conexion.getConexion() == null)
            System.err.println("⚠ No se pudo conectar a la base de datos.");

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 900, 600);
        aplicarTema(scene);

        // Reaccionar a cambios de tema en tiempo real
        IdiomaService.temaOscuroProperty.addListener((obs, old, oscuro) ->
                Platform.runLater(() -> aplicarTema(primaryStage.getScene())));

        stage.setTitle("BiometricUMG 2.0 — Universidad Mariano Gálvez");
        stage.setScene(scene);
        stage.setResizable(true);
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.centerOnScreen();

        try {
            stage.getIcons().add(new Image(
                    Objects.requireNonNull(getClass().getResourceAsStream("/images/logo_umg.png"))));
        } catch (Exception ignored) {}

        stage.show();
        root.setOpacity(0);
        animarEntrada(root);
    }

    public static void aplicarTema(Scene scene) {
        if (scene == null) return;
        String css = IdiomaService.isTemaOscuro() ? "/css/estilos.css" : "/css/estilos_claro.css";
        String cssUrl;
        try {
            cssUrl = Objects.requireNonNull(Main.class.getResource(css)).toExternalForm();
        } catch (Exception e) {
            cssUrl = Objects.requireNonNull(Main.class.getResource("/css/estilos.css")).toExternalForm();
        }
        // Aplicar en la escena raíz
        scene.getStylesheets().clear();
        scene.getStylesheets().add(cssUrl);
        // Aplicar en todos los nodos hijo que tengan sus propios stylesheets
        aplicarTemaRecursivo(scene.getRoot(), cssUrl);
    }

    private static void aplicarTemaRecursivo(javafx.scene.Parent root, String cssUrl) {
        if (root == null) return;
        if (!root.getStylesheets().isEmpty()) {
            root.getStylesheets().clear();
            root.getStylesheets().add(cssUrl);
        }
        for (javafx.scene.Node node : root.getChildrenUnmodifiable()) {
            if (node instanceof javafx.scene.Parent) {
                aplicarTemaRecursivo((javafx.scene.Parent) node, cssUrl);
            }
        }
    }

    private void animarEntrada(Parent root) {
        new Thread(() -> {
            for (double i = 0; i <= 1.0; i += 0.05) {
                final double op = i;
                Platform.runLater(() -> root.setOpacity(op));
                try { Thread.sleep(15); } catch (InterruptedException ignored) {}
            }
        }, "fade-in").start();
    }

    public static void navegarA(String fxmlPath, String titulo, double ancho, double alto) {
        ReconocimientoFacialService.cancelarEnrolamiento();
        try {
            if (primaryStage.getScene() != null &&
                    primaryStage.getScene().getUserData() instanceof Liberable liberable)
                liberable.liberarRecursos();
        } catch (Exception ignored) {}
        CamaraService.detener();
        ReconocimientoFacialService.liberarCamaraCompartida();
        ReconocimientoFacialService.resetCancelacion();

        try {
            FXMLLoader loader = new FXMLLoader(Main.class.getResource(fxmlPath));
            Parent root = loader.load();
            Scene scene = new Scene(root, ancho, alto);
            aplicarTema(scene);
            Object ctrl = loader.getController();
            if (ctrl instanceof Liberable) scene.setUserData(ctrl);
            primaryStage.setScene(scene);
            primaryStage.setTitle("BiometricUMG 2.0 — " + titulo);
            primaryStage.setWidth(ancho);
            primaryStage.setHeight(alto);
            primaryStage.setResizable(true);
            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(550);
            primaryStage.centerOnScreen();
            root.setOpacity(0);
            new Thread(() -> {
                for (double i = 0; i <= 1.0; i += 0.08) {
                    final double op = i;
                    Platform.runLater(() -> root.setOpacity(op));
                    try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                }
            }, "fade-nav").start();
        } catch (IOException e) {
            System.err.println("Error navegando a: " + fxmlPath);
            e.printStackTrace();
        }
    }

    @Override public void stop() { cerrarAplicacion(); }

    public static void cerrarAplicacion() {
        ReconocimientoFacialService.cancelarEnrolamiento();
        try {
            if (primaryStage != null && primaryStage.getScene() != null &&
                    primaryStage.getScene().getUserData() instanceof Liberable l)
                l.liberarRecursos();
        } catch (Exception ignored) {}
        Thread apagado = new Thread(() -> {
            try {
                CamaraService.detener();
                ReconocimientoFacialService.liberarCamaraCompartida();
                ServidorVerificacion.detener();
                app.servicios.BackupService.detener();
                Conexion.cerrarConexionPool();
            } catch (Exception ignored) {}
            try { Thread.sleep(800); } catch (InterruptedException ignored) {}
            System.exit(0);
        }, "shutdown-thread");
        apagado.setDaemon(false);
        apagado.start();
        Platform.runLater(() -> { try { if (primaryStage != null) primaryStage.close(); } catch (Exception ignored) {} Platform.exit(); });
    }

    public static void main(String[] args) { launch(args); }
}
