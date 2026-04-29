// ═══════════════════════════════════════════════════════════
//  RestriccionDAO.java  — BiometricUMG 4.0
// ═══════════════════════════════════════════════════════════
package app.dao;

import app.conexion.Conexion;

import java.sql.*;
import java.time.LocalDate;

public class RestriccionDAO {

    public static boolean tieneRestriccionActiva(int idUsuario) {
        String sql = """
            SELECT COUNT(*) FROM restricciones
            WHERE id_usuario=?
              AND fecha_inicio <= CURRENT_DATE
              AND fecha_fin >= CURRENT_DATE
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1) > 0;
        } catch (SQLException e) {
            System.err.println("❌ tieneRestriccionActiva: " + e.getMessage());
        }
        return false;
    }

    public static boolean agregar(int idUsuario, String motivo, LocalDate inicio, LocalDate fin) {
        String sql = """
            INSERT INTO restricciones (id_usuario, motivo, fecha_inicio, fecha_fin)
            VALUES (?, ?, ?, ?)
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt   (1, idUsuario);
            ps.setString(2, motivo);
            ps.setDate  (3, Date.valueOf(inicio));
            ps.setDate  (4, Date.valueOf(fin));
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ agregar restricción: " + e.getMessage());
        }
        return false;
    }

    public static ResultSet listarTodas() {
        String sql = """
            SELECT r.*, u.nombre || ' ' || u.apellido AS nombrePersona
            FROM restricciones r
            JOIN usuarios u ON r.id_usuario = u.id_usuario
            ORDER BY r.fecha_fin DESC
            """;
        try {
            Connection con = Conexion.nuevaConexion();
            return con.prepareStatement(sql).executeQuery();
        } catch (SQLException e) {
            System.err.println("❌ listarTodas: " + e.getMessage());
        }
        return null;
    }

    public static boolean eliminar(int idRestriccion) {
        String sql = "DELETE FROM restricciones WHERE id_restriccion=?";
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idRestriccion);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ eliminar restricción: " + e.getMessage());
        }
        return false;
    }
}
