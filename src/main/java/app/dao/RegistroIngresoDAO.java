package app.dao;

import app.conexion.Conexion;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class RegistroIngresoDAO {

    public static boolean registrarPorPuerta(int idUsuario, int idPuerta) {
        if (RestriccionDAO.tieneRestriccionActiva(idUsuario)) {
            System.out.println("⚠ Usuario con restricción activa. Acceso denegado.");
            return false;
        }
        String sql = """
            INSERT INTO registro_ingresos (id_usuario, id_puerta, tipo_ingreso)
            VALUES (?, ?, 'PUERTA')
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            ps.setInt(2, idPuerta);
            ps.executeUpdate();
            registrarLogSimple(idUsuario, "Puerta " + idPuerta);
            return true;
        } catch (SQLException e) {
            System.err.println("❌ registrarPorPuerta: " + e.getMessage());
        }
        return false;
    }

    public static boolean registrarPorSalon(int idUsuario, int idSalon) {
        String sql = """
            INSERT INTO registro_ingresos (id_usuario, id_salon, tipo_ingreso)
            VALUES (?, ?, 'SALON')
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            ps.setInt(2, idSalon);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("❌ registrarPorSalon: " + e.getMessage());
        }
        return false;
    }

    /**
     * FIX: la columna en la tabla 'ingresos' es 'id_usuario' (snake_case),
     * no 'idUsuario' (camelCase). PostgreSQL es sensible a mayúsculas
     * cuando el nombre no va entre comillas.
     *
     * Si la tabla 'ingresos' no existe en tu BD (es una tabla legada opcional),
     * el error se suprime silenciosamente porque el registro real ya quedó
     * en 'registro_ingresos' en el paso anterior.
     */
    private static void registrarLogSimple(int idUsuario, String puerta) {
        String sql = """
            INSERT INTO ingresos (id_usuario, fecha, hora, puerta)
            VALUES (?, CURRENT_DATE, CURRENT_TIME, ?)
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt   (1, idUsuario);
            ps.setString(2, puerta);
            ps.executeUpdate();
        } catch (SQLException e) {
            // Solo loguear si NO es error de tabla/columna inexistente
            // (código 42703 = columna no existe, 42P01 = tabla no existe)
            String estado = e.getSQLState();
            if (!"42703".equals(estado) && !"42P01".equals(estado)) {
                System.err.println("⚠ logSimple: " + e.getMessage());
            } else {
                System.out.println("ℹ logSimple omitido — tabla 'ingresos' no disponible (no crítico).");
            }
        }
    }

    public static ResultSet obtenerIngresosPorPuertaFecha(int idPuerta, LocalDate fecha, boolean ascendente) {
        String orden = ascendente ? "ASC" : "DESC";
        String sql = """
            SELECT ri.*, u.nombre || ' ' || u.apellido AS nombrePersona,
                   u.foto, u.correo, pu.nombre AS nombrePuerta,
                   i.nombre AS nombreInstalacion
            FROM registro_ingresos ri
            JOIN usuarios u ON ri.id_usuario = u.id_usuario
            JOIN puertas pu ON ri.id_puerta = pu.id_puerta
            JOIN instalaciones i ON pu.id_instalacion = i.id_instalacion
            WHERE ri.id_puerta = ?
              AND ri.fecha_hora::date = ?
              AND ri.tipo_ingreso = 'PUERTA'
            ORDER BY ri.fecha_hora """ + orden;
        try {
            Connection con = Conexion.nuevaConexion();
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setInt (1, idPuerta);
            ps.setDate(2, Date.valueOf(fecha));
            return ps.executeQuery();
        } catch (SQLException e) {
            System.err.println("❌ obtenerIngresosPorPuertaFecha: " + e.getMessage());
        }
        return null;
    }

    public static List<LocalDate> obtenerFechasConIngreso(int idPuerta) {
        List<LocalDate> fechas = new ArrayList<>();
        String sql = """
            SELECT DISTINCT fecha_hora::date AS fecha
            FROM registro_ingresos
            WHERE id_puerta = ? AND tipo_ingreso = 'PUERTA'
            ORDER BY fecha DESC
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idPuerta);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) fechas.add(rs.getDate("fecha").toLocalDate());
        } catch (SQLException e) {
            System.err.println("❌ obtenerFechasConIngreso: " + e.getMessage());
        }
        return fechas;
    }

    public static ResultSet obtenerIngresosPorSalon(int idSalon) {
        String sql = """
            SELECT ri.*, u.nombre || ' ' || u.apellido AS nombrePersona,
                   u.foto, u.correo, s.nombre AS nombreSalon
            FROM registro_ingresos ri
            JOIN usuarios u ON ri.id_usuario = u.id_usuario
            JOIN salones s ON ri.id_salon = s.id_salon
            WHERE ri.id_salon = ? AND ri.tipo_ingreso = 'SALON'
            ORDER BY ri.fecha_hora DESC
            """;
        try {
            Connection con = Conexion.nuevaConexion();
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setInt(1, idSalon);
            return ps.executeQuery();
        } catch (SQLException e) {
            System.err.println("❌ obtenerIngresosPorSalon: " + e.getMessage());
        }
        return null;
    }
}