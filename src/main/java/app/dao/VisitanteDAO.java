// ═══════════════════════════════════════════════════════════
//  VisitanteDAO.java  — BiometricUMG 4.0
// ═══════════════════════════════════════════════════════════
package app.dao;

import app.conexion.Conexion;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class VisitanteDAO {

    /**
     * Registra un visitante temporal.
     * Inserta en Usuarios con tipoPersona='Visitante'
     * y crea su acceso temporal en VisitantesTemporales.
     * Retorna idUsuario generado, o -1 si falló.
     */
    public static int registrarVisitante(String nombre, String apellido,
                                          String telefono, String correo,
                                          String motivo, int horasAcceso) {
        String sqlUsuario = """
            INSERT INTO Usuarios (nombre, apellido, telefono, correo,
                                  tipoPersona, estado, activo, idRol, fechaRegistro)
            VALUES (?, ?, ?, ?, 'Visitante', true, true, 3, NOW())
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sqlUsuario, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nombre);
            ps.setString(2, apellido);
            ps.setString(3, telefono != null ? telefono : "");
            ps.setString(4, correo   != null ? correo   : "visitante@temporal.com");
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                int idUsuario = keys.getInt(1);
                crearAccesoTemporal(con, idUsuario, motivo, horasAcceso);
                return idUsuario;
            }
        } catch (SQLException e) {
            System.err.println("❌ registrarVisitante: " + e.getMessage());
        }
        return -1;
    }

    private static void crearAccesoTemporal(Connection con, int idUsuario,
                                             String motivo, int horasAcceso) throws SQLException {
        String sql = """
            INSERT INTO visitantes_temporales (idUsuario, motivo, fechaInicio, fechaExpira)
            VALUES (?, ?, NOW(), NOW() + (? * INTERVAL '1 hour'))
            """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt   (1, idUsuario);
            ps.setString(2, motivo);
            ps.setInt   (3, horasAcceso);
            ps.executeUpdate();
        }
    }

    public static boolean tieneAccesoValido(int idUsuario) {
        String sql = """
            SELECT COUNT(*) FROM visitantes_temporales
            WHERE id_usuario=? AND activo=true AND fecha_expira > NOW()
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1) > 0;
        } catch (SQLException e) {
            System.err.println("⚠ tieneAccesoValido: " + e.getMessage());
        }
        return false;
    }

    public static boolean revocarAcceso(int idUsuario) {
        String sql = "UPDATE visitantes_temporales SET activo=false WHERE id_usuario=?";
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("⚠ revocarAcceso: " + e.getMessage());
        }
        return false;
    }

    public static List<String[]> listarVisitantesActivos() {
        List<String[]> lista = new ArrayList<>();
        String sql = """
            SELECT u.id_usuario,
                   u.nombre || ' ' || u.apellido AS nombre,
                   u.telefono, u.correo,
                   vt.motivo,
                   TO_CHAR(vt.fecha_expira, 'DD/MM/YYYY HH24:MI') AS expira,
                   EXTRACT(EPOCH FROM (vt.fecha_expira - NOW()))::int / 60 AS minutosRestantes
            FROM visitantes_temporales vt
            JOIN usuarios u ON u.id_usuario = vt.id_usuario
            WHERE vt.activo=true AND vt.fecha_expira > NOW()
            ORDER BY vt.fecha_expira ASC
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int mins = rs.getInt("minutos_restantes");
                String tiempo = mins >= 60 ? (mins/60)+"h "+(mins%60)+"min" : mins+" min";
                lista.add(new String[]{
                    rs.getString("id_usuario"), rs.getString("nombre"),
                    rs.getString("telefono"),  rs.getString("correo"),
                    rs.getString("motivo"),    rs.getString("expira"), tiempo
                });
            }
        } catch (SQLException e) {
            System.err.println("⚠ listarVisitantesActivos: " + e.getMessage());
        }
        return lista;
    }

    public static int limpiarExpirados() {
        String sql = "UPDATE visitantes_temporales SET activo=false WHERE activo=true AND fecha_expira <= NOW()";
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            return ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("⚠ limpiarExpirados: " + e.getMessage());
        }
        return 0;
    }
}
