package app.controladores;

import app.Main;
import app.dao.UsuarioDAO;
import javafx.animation.FadeTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Dashboard del personal de Seguridad
 * Solo puede ver ingreso por puerta y lista de restricciones
 */
public class IngresoController implements Initializable {

    @FXML private Label     lblUsuario;
    @FXML private Label     lblTitulo;
    @FXML private StackPane contenedor;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        var u = UsuarioDAO.getSesionActiva();
        if (u != null) lblUsuario.setText(u.getUsuario());
        cargarPantalla("/fxml/IngresoPuerta.fxml", "Control de Ingreso — Puerta Principal");
    }

    private void cargarPantalla(String fxml, String titulo) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Parent root = loader.load();
            contenedor.getChildren().setAll(root);
            lblTitulo.setText(titulo);
            root.setOpacity(0);
            FadeTransition ft = new FadeTransition(Duration.millis(200), root);
            ft.setToValue(1);
            ft.play();
        } catch (IOException e) { System.err.println("❌ " + e.getMessage()); }
    }

    @FXML public void irIngresoPuerta()   { cargarPantalla("/fxml/IngresoPuerta.fxml", "Control de Ingreso — Puerta"); }
    @FXML public void irRestricciones()   { cargarPantalla("/fxml/Restricciones.fxml", "Personas con Restricción"); }
    @FXML public void cerrarSesion()      { UsuarioDAO.logout(); Main.navegarA("/fxml/Login.fxml", "Login", 900, 600); }
}
