package app.dao;

import app.conexion.Conexion;
import app.modelos.Curso;
import app.modelos.Salon;
import app.modelos.Usuario;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * BiometricUMG 4.0 — CursoDAO
 * JOIN con Usuarios en lugar de Personas.
 */
public class CursoDAO {

    private static final String SELECT_BASE = """
        SELECT c.id_curso, c.nombre_curso, c.seccion,
               c.dia_semana, c.hora_inicio::text AS hora_inicio,
               c.hora_fin::text AS hora_fin,
               c.id_catedratico, c.id_salon, c.activo,
               u.nombre || ' ' || u.apellido AS nombre_catedratico,
               s.nombre AS nombre_salon
        FROM cursos c
        LEFT JOIN usuarios u ON c.id_catedratico = u.id_usuario
        LEFT JOIN salones s ON c.id_salon = s.id_salon
        """;

    public static List<Curso> listarTodos() {
        return query(SELECT_BASE + " WHERE c.activo=true ORDER BY c.nombre_curso", ps -> {});
    }

    public static List<Curso> listarPorCatedratico(int idCatedratico) {
        return query(SELECT_BASE + " WHERE c.id_catedratico=? AND c.activo=true ORDER BY c.nombre_curso",
                ps -> ps.setInt(1, idCatedratico));
    }

    public static Curso buscarPorId(int idCurso) {
        List<Curso> list = query(SELECT_BASE + " WHERE c.id_curso=?",
                ps -> ps.setInt(1, idCurso));
        return list.isEmpty() ? null : list.get(0);
    }

    public static int insertar(Curso c) {
        String sql = """
            INSERT INTO cursos (nombre_curso,seccion,dia_semana,hora_inicio,hora_fin,id_catedratico,id_salon)
            VALUES (?,?,?,?,?,?,?)
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, c.getNombreCurso());
            ps.setString(2, c.getSeccion());
            ps.setString(3, c.getDiaSemana());
            ps.setString(4, c.getHoraInicio());
            ps.setString(5, c.getHoraFin());
            ps.setInt   (6, c.getIdCatedratico());
            ps.setInt   (7, c.getIdSalon());
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            return rs.next() ? rs.getInt(1) : -1;
        } catch (SQLException e) {
            System.err.println("❌ CursoDAO.insertar: " + e.getMessage());
        }
        return -1;
    }

    public static boolean actualizar(Curso c) {
        String sql = """
            UPDATE cursos SET nombre_curso=?,seccion=?,dia_semana=?,
            hora_inicio=?,hora_fin=?,id_catedratico=?,id_salon=?
            WHERE id_curso=?
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, c.getNombreCurso());
            ps.setString(2, c.getSeccion());
            ps.setString(3, c.getDiaSemana());
            ps.setString(4, c.getHoraInicio());
            ps.setString(5, c.getHoraFin());
            ps.setInt   (6, c.getIdCatedratico());
            ps.setInt   (7, c.getIdSalon());
            ps.setInt   (8, c.getIdCurso());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ CursoDAO.actualizar: " + e.getMessage());
        }
        return false;
    }

    public static boolean eliminar(int idCurso) {
        String sql = "UPDATE cursos SET activo=false WHERE id_curso=?";
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idCurso);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ CursoDAO.eliminar: " + e.getMessage());
        }
        return false;
    }

    public static boolean inscribirEstudiante(int idCurso, int idEstudiante) {
        String sqlCheck     = "SELECT id_inscripcion, activo FROM inscripciones_curso WHERE id_curso=? AND id_estudiante=?";
        String sqlInsert    = "INSERT INTO inscripciones_curso (id_curso, id_estudiante, activo) VALUES (?,?,true)";
        String sqlReactivar = "UPDATE inscripciones_curso SET activo=true WHERE id_curso=? AND id_estudiante=?";
        try (Connection con = Conexion.nuevaConexion()) {
            try (PreparedStatement ps = con.prepareStatement(sqlCheck)) {
                ps.setInt(1, idCurso); ps.setInt(2, idEstudiante);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    if (!rs.getBoolean("activo")) {
                        try (PreparedStatement psUp = con.prepareStatement(sqlReactivar)) {
                            psUp.setInt(1, idCurso); psUp.setInt(2, idEstudiante);
                            psUp.executeUpdate();
                        }
                    }
                    return true;
                }
            }
            try (PreparedStatement ps = con.prepareStatement(sqlInsert)) {
                ps.setInt(1, idCurso); ps.setInt(2, idEstudiante);
                ps.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            System.err.println("❌ inscribirEstudiante: " + e.getMessage());
        }
        return false;
    }

    public static boolean desinscribirEstudiante(int idCurso, int idEstudiante) {
        String sql = "UPDATE inscripciones_curso SET activo=false WHERE id_curso=? AND id_estudiante=?";
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1,idCurso); ps.setInt(2,idEstudiante);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ desinscribirEstudiante: " + e.getMessage());
        }
        return false;
    }

    /** Antes devolvía List<Persona> — ahora devuelve List<Usuario> */
    public static List<Usuario> listarEstudiantesInscritos(int idCurso) {
        return UsuarioDAO.listarEstudiantesInscritos(idCurso);
    }

    public static List<Salon> listarSalones() {
        List<Salon> list = new ArrayList<>();
        String sql = "SELECT * FROM salones ORDER BY nivel, nombre";
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Salon s = new Salon();
                s.setIdSalon(rs.getInt("id_salon"));
                s.setNombre (rs.getString("nombre"));
                s.setNivel  (rs.getString("nivel"));
                list.add(s);
            }
        } catch (SQLException e) {
            System.err.println("❌ listarSalones: " + e.getMessage());
        }
        return list;
    }

    @FunctionalInterface
    interface Setter { void set(PreparedStatement ps) throws SQLException; }

    private static List<Curso> query(String sql, Setter setter) {
        List<Curso> list = new ArrayList<>();
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            setter.set(ps);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Curso c = new Curso();
                c.setIdCurso          (rs.getInt   ("id_curso"));
                c.setNombreCurso      (rs.getString("nombre_curso"));
                c.setSeccion          (rs.getString("seccion"));
                c.setDiaSemana        (rs.getString("dia_semana"));
                c.setHoraInicio       (rs.getString("hora_inicio"));
                c.setHoraFin          (rs.getString("hora_fin"));
                c.setIdCatedratico    (rs.getInt   ("id_catedratico"));
                c.setNombreCatedratico(rs.getString("nombre_catedratico"));
                c.setIdSalon          (rs.getInt   ("id_salon"));
                c.setNombreSalon      (rs.getString("nombre_salon"));
                c.setActivo           (rs.getBoolean("activo"));
                list.add(c);
            }
        } catch (SQLException e) {
            System.err.println("❌ CursoDAO.query: " + e.getMessage());
        }
        return list;
    }
}