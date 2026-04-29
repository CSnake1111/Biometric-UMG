package app.controladores;

import app.servicios.ReconocimientoFacialService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controlador — Entrenamiento del Modelo Facial
 */
public class EntrenamientoController implements Initializable {

    @FXML private Label           lblEstadoOpenCV;
    @FXML private Label           lblPersonasEnroladas;
    @FXML private Label           lblModeloExiste;
    @FXML private ProgressBar     progEntrenamiento;
    @FXML private Label           lblEstadoEntrenamiento;
    @FXML private Button          btnEntrenar;
    @FXML private TextArea        txtLog;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        actualizarEstado();
    }

    private void actualizarEstado() {
        boolean opencvOk = ReconocimientoFacialService.inicializar();
        lblEstadoOpenCV.setText(opencvOk ? "✅ OpenCV cargado correctamente" : "❌ OpenCV no disponible");
        lblEstadoOpenCV.setStyle(opencvOk
                ? "-fx-text-fill: #27ae60; -fx-font-size: 12px;"
                : "-fx-text-fill: #e74c3c; -fx-font-size: 12px;");

        int personas = contarPersonasConMuestras();
        lblPersonasEnroladas.setText(personas + " personas con muestras faciales");

        boolean modeloOk = ReconocimientoFacialService.isEntrenado();
        lblModeloExiste.setText(modeloOk
                ? "✅ Modelo en memoria (" + personas + " persona(s) enrolada(s))"
                : "⚠ No hay muestras enroladas aún — registra personas primero");
        lblModeloExiste.setStyle(modeloOk
                ? "-fx-text-fill: #27ae60; -fx-font-size: 12px;"
                : "-fx-text-fill: #f39c12; -fx-font-size: 12px;");

        btnEntrenar.setDisable(!opencvOk || personas == 0);
    }

    @FXML public void iniciarEntrenamiento() {
        btnEntrenar.setDisable(true);
        progEntrenamiento.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        lblEstadoEntrenamiento.setText("🔄 Entrenando modelo...");
        txtLog.clear();

        new Thread(() -> {
            log("Iniciando entrenamiento del modelo facial...");
            log("Leyendo muestras de la carpeta data/rostros/");

            boolean ok = ReconocimientoFacialService.entrenarModelo();

            Platform.runLater(() -> {
                progEntrenamiento.setProgress(ok ? 1.0 : 0);
                if (ok) {
                    lblEstadoEntrenamiento.setText("✅ Modelo entrenado y guardado exitosamente");
                    lblEstadoEntrenamiento.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 13px;");
                    log("✅ Modelo guardado en: data/modelo_facial.xml");
                    log("✅ Mapa de labels guardado en: data/labels.txt");
                } else {
                    lblEstadoEntrenamiento.setText("❌ Error durante el entrenamiento");
                    lblEstadoEntrenamiento.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 13px;");
                    log("❌ Verifica que existan muestras en data/rostros/");
                }
                btnEntrenar.setDisable(false);
                actualizarEstado();
            });
        }, "bg-thread-21") {{ setDaemon(true); }}.start();
    }

    @FXML public void actualizarEstadoBtn() { actualizarEstado(); }

    private void log(String msg) {
        Platform.runLater(() -> txtLog.appendText(msg + "\n"));
    }

    private int contarPersonasConMuestras() {
        File carpeta = new File("data/rostros/");
        if (!carpeta.exists()) return 0;
        File[] subdirs = carpeta.listFiles(File::isDirectory);
        return subdirs != null ? subdirs.length : 0;
    }
}
