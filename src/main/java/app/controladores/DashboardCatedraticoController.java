package app.controladores;

import app.Main;
import app.dao.CursoDAO;
import app.dao.UsuarioDAO;
import app.modelos.Curso;
import app.modelos.Usuario;
import javafx.animation.FadeTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Dashboard del Catedrático — FIX completo
 *
 * Bugs corregidos:
 *  • Sidebar "Mis Cursos": cada botón ahora pasa el Curso seleccionado al
 *    ArbolAsistenciasController mediante setCurso(), en vez de recargar vacío.
 *  • Al iniciar se selecciona el primer curso automáticamente.
 *  • El botón global "Árbol de Asistencias" también carga el primer curso.
 *  • Si el ArbolController ya está activo, solo se cambia el curso (no recarga).
 */
public class DashboardCatedraticoController implements Initializable {

    @FXML private Label     lblUsuario;
    @FXML private Label     lblTitulo;
    @FXML private StackPane contenedor;
    @FXML private VBox      listaCursosSidebar;

    private List<Curso> cursosCatedratico;
    private ArbolAsistenciasController arbolController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        Usuario u = UsuarioDAO.getSesionActiva();
        if (u != null) lblUsuario.setText(u.getUsuario());
        cargarCursosSidebar();
    }

    // ─────────────────────────────────────────────────────────────
    //  SIDEBAR
    // ─────────────────────────────────────────────────────────────

    private void cargarCursosSidebar() {
        Usuario u = UsuarioDAO.getSesionActiva();
        cursosCatedratico = (u != null)
                ? CursoDAO.listarPorCatedratico(u.getIdUsuario())
                : CursoDAO.listarTodos();

        listaCursosSidebar.getChildren().clear();

        if (cursosCatedratico.isEmpty()) {
            Label lbl = new Label("Sin cursos asignados");
            lbl.setStyle("-fx-text-fill:rgba(255,255,255,0.35);-fx-font-size:11px;-fx-padding:8 16;");
            listaCursosSidebar.getChildren().add(lbl);
            abrirArbolConCurso(null);
            return;
        }

        for (Curso c : cursosCatedratico) {
            Button btn = new Button("  \uD83D\uDCDA  " + c.getNombreCurso() + " [" + c.getSeccion() + "]");
            btn.getStyleClass().add("btn-menu");
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setOnAction(e -> {
                listaCursosSidebar.getChildren().forEach(n -> {
                    if (n instanceof Button b) b.setStyle("");
                });
                btn.setStyle("-fx-background-color:rgba(79,195,247,0.15);-fx-text-fill:#4fc3f7;");
                abrirArbolConCurso(c);
            });
            listaCursosSidebar.getChildren().add(btn);
        }

        // Seleccionar primer curso al iniciar
        if (!listaCursosSidebar.getChildren().isEmpty()) {
            Button primeroBtn = (Button) listaCursosSidebar.getChildren().get(0);
            primeroBtn.setStyle("-fx-background-color:rgba(79,195,247,0.15);-fx-text-fill:#4fc3f7;");
        }
        abrirArbolConCurso(cursosCatedratico.get(0));
    }

    // ─────────────────────────────────────────────────────────────
    //  PANEL CENTRAL — árbol de asistencias
    // ─────────────────────────────────────────────────────────────

    private void abrirArbolConCurso(Curso curso) {
        // Si ya hay un controller de árbol vivo, solo cambiar el curso
        if (arbolController != null) {
            if (curso != null) arbolController.setCurso(curso);
            lblTitulo.setText(curso != null ? curso.getNombreCurso() : "Árbol de Asistencias");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ArbolAsistencias.fxml"));
            Parent root = loader.load();
            arbolController = loader.getController();

            contenedor.getChildren().setAll(root);
            lblTitulo.setText(curso != null ? curso.getNombreCurso() : "Árbol de Asistencias");

            if (curso != null) arbolController.setCurso(curso);

            root.setOpacity(0);
            FadeTransition ft = new FadeTransition(Duration.millis(200), root);
            ft.setToValue(1);
            ft.play();

        } catch (IOException e) {
            System.err.println("❌ DashboardCatedratico.abrirArbolConCurso: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  ACCIONES FXML
    // ─────────────────────────────────────────────────────────────

    @FXML
    public void irAsistencias() {
        // HERRAMIENTAS → abre el Historial (resumen histórico + calendario)
        arbolController = null;
        Curso c = (cursosCatedratico != null && !cursosCatedratico.isEmpty())
                ? cursosCatedratico.get(0) : null;
        abrirHistorial(c);
    }

    private app.controladores.HistorialAsistenciasController historialController;

    private void abrirHistorial(Curso curso) {
        historialController = null;
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/fxml/HistorialAsistencias.fxml"));
            javafx.scene.Parent root = loader.load();
            historialController = loader.getController();

            contenedor.getChildren().setAll(root);
            lblTitulo.setText("Historial de Asistencias");

            if (curso != null) historialController.setCurso(curso);

            root.setOpacity(0);
            javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(
                    javafx.util.Duration.millis(200), root);
            ft.setToValue(1);
            ft.play();
        } catch (java.io.IOException e) {
            System.err.println("❌ abrirHistorial: " + e.getMessage());
        }
    }

    @FXML
    public void irMiPerfil() {
        arbolController = null;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/CambiarPass.fxml"));
            Parent root = loader.load();
            contenedor.getChildren().setAll(root);
            lblTitulo.setText("Mi Perfil");
            root.setOpacity(0);
            FadeTransition ft = new FadeTransition(Duration.millis(200), root);
            ft.setToValue(1);
            ft.play();
        } catch (IOException e) {
            lblTitulo.setText("Mi Perfil");
        }
    }

    @FXML
    public void cerrarSesion() {
        if (arbolController != null) {
            try { arbolController.liberarRecursos(); } catch (Exception ignored) {}
        }
        UsuarioDAO.logout();
        Main.navegarA("/fxml/Login.fxml", "Login", 900, 600);
    }
}
