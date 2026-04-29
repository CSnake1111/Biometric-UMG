package app.servicios;

import app.conexion.Conexion;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * AnaliticaService — Inteligencia de asistencia y puntualidad.
 */
public class AnaliticaService {

    public static final double UMBRAL_RIESGO    = 0.75;
    public static final int    UMBRAL_TARDANZAS = 3;

    // ══════════════════════════════════════════════════════════
    //  Estudiantes en riesgo
    //  Retorna: {nombre, correo, curso, % asistencia, tipo_alerta}
    // ══════════════════════════════════════════════════════════
    public static List<String[]> obtenerEstudiantesEnRiesgo() {
        List<String[]> alertas = new ArrayList<>();
        // FIX: + → || para concatenar en PostgreSQL
        String sql = """
            SELECT
                p.nombre || ' ' || p.apellido AS nombre,
                p.correo,
                c.nombre_curso,
                c.seccion,
                COUNT(a.id_asistencia) AS totalClases,
                SUM(CASE WHEN a.estado IN ('PRESENTE','TARDANZA') THEN 1 ELSE 0 END) AS presentes,
                SUM(CASE WHEN a.estado = 'AUSENTE'  THEN 1 ELSE 0 END) AS ausentes
            FROM asistencias a
            JOIN usuarios p ON a.id_estudiante = p.id_usuario
            JOIN cursos c   ON a.id_curso = c.id_curso
            GROUP BY p.nombre, p.apellido, p.correo, c.nombre_curso, c.seccion
            HAVING COUNT(a.id_asistencia) >= 1
            ORDER BY (SUM(CASE WHEN a.estado IN ('PRESENTE','TARDANZA') THEN 1 ELSE 0 END)::float
                      / NULLIF(COUNT(a.id_asistencia), 0)) ASC
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int total    = rs.getInt("totalClases");
                int presentes = rs.getInt("presentes");
                int ausentes  = rs.getInt("ausentes");
                if (total == 0) continue;
                double pct = (double) presentes / total;
                String tipoAlerta;
                if      (pct < 0.60)          tipoAlerta = "CRÍTICO";
                else if (pct < UMBRAL_RIESGO) tipoAlerta = "EN RIESGO";
                else continue;

                alertas.add(new String[]{
                        rs.getString("nombre"),
                        rs.getString("correo"),
                        rs.getString("nombre_curso") + " - " + rs.getString("seccion"),
                        String.format("%.0f%%", pct * 100),
                        tipoAlerta,
                        String.valueOf(ausentes)
                });
            }
        } catch (SQLException e) {
            System.err.println("⚠ Error calculando riesgo: " + e.getMessage());
        }
        return alertas;
    }

    // ══════════════════════════════════════════════════════════
    //  Ranking de estudiantes más puntuales
    //  Retorna: {puesto, nombre, correo, foto, % asistencia, presentes}
    // ══════════════════════════════════════════════════════════
    public static List<String[]> rankingPuntuales(int top) {
        List<String[]> ranking = new ArrayList<>();
        // FIX: + → ||  /  CAST(...AS FLOAT) → ::float  /  LIMIT con parámetro
        String sql = """
            SELECT
                p.nombre || ' ' || p.apellido AS nombre,
                p.correo, p.foto,
                COUNT(a.id_asistencia) AS totalClases,
                SUM(CASE WHEN a.estado IN ('PRESENTE','TARDANZA') THEN 1 ELSE 0 END) AS presentes
            FROM asistencias a
            JOIN usuarios p ON a.id_estudiante = p.id_usuario
            GROUP BY p.nombre, p.apellido, p.correo, p.foto
            HAVING COUNT(a.id_asistencia) >= 1
            ORDER BY (SUM(CASE WHEN a.estado IN ('PRESENTE','TARDANZA') THEN 1 ELSE 0 END)::float
                      / NULLIF(COUNT(a.id_asistencia), 0)) DESC,
                     SUM(CASE WHEN a.estado IN ('PRESENTE','TARDANZA') THEN 1 ELSE 0 END) DESC
            LIMIT ?
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, top);
            ResultSet rs = ps.executeQuery();
            int puesto = 1;
            while (rs.next()) {
                int total    = rs.getInt("totalClases");
                int presentes = rs.getInt("presentes");
                double pct = total > 0 ? (double) presentes / total : 0;
                ranking.add(new String[]{
                        String.valueOf(puesto++),
                        rs.getString("nombre"),
                        rs.getString("correo"),
                        rs.getString("foto"),
                        String.format("%.0f%%", pct * 100),
                        String.valueOf(presentes)
                });
            }
        } catch (SQLException e) {
            System.err.println("⚠ Error ranking: " + e.getMessage());
        }
        return ranking;
    }

    // ══════════════════════════════════════════════════════════
    //  Estadísticas de asistencia por día (últimos N días)
    //  Retorna: {fecha, totalPresentes, totalAusentes}
    // ══════════════════════════════════════════════════════════
    public static List<String[]> asistenciaPorDia(int dias) {
        List<String[]> datos = new ArrayList<>();
        // FIX: CONVERT(VARCHAR, fecha, 23) es T-SQL → usar TO_CHAR en PostgreSQL
        //      CURRENT_DATE + (? * INTERVAL '1 day') → CURRENT_DATE - ?::int
        String sql = """
            SELECT
                TO_CHAR(fecha, 'YYYY-MM-DD') AS dia,
                SUM(CASE WHEN estado='PRESENTE' THEN 1 ELSE 0 END) AS presentes,
                SUM(CASE WHEN estado='AUSENTE'  THEN 1 ELSE 0 END) AS ausentes
            FROM asistencias
            WHERE fecha >= CURRENT_DATE - ?::int
            GROUP BY fecha
            ORDER BY fecha ASC
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, dias);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                datos.add(new String[]{
                        rs.getString("dia"),
                        rs.getString("presentes"),
                        rs.getString("ausentes")
                });
            }
        } catch (SQLException e) {
            System.err.println("⚠ Error stats diarias: " + e.getMessage());
        }
        return datos;
    }

    // ══════════════════════════════════════════════════════════
    //  % asistencia general hoy
    // ══════════════════════════════════════════════════════════
    public static double porcentajeAsistenciaHoy() {
        String sql = """
            SELECT
                SUM(CASE WHEN estado IN ('PRESENTE','TARDANZA') THEN 1.0 ELSE 0 END) /
                NULLIF(COUNT(*), 0) AS pct
            FROM asistencias
            WHERE fecha = CURRENT_DATE
              AND estado != 'PENDIENTE'
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getDouble("pct") * 100;
        } catch (SQLException e) {
            System.err.println("⚠ " + e.getMessage());
        }
        return 0;
    }

    // ══════════════════════════════════════════════════════════
    //  Predicción simple: probabilidad de ausencia mañana
    //  Retorna: {nombre, correo, pctRiesgoMañana}
    // ══════════════════════════════════════════════════════════
    public static List<String[]> prediccionAusenciaManana() {
        List<String[]> predicciones = new ArrayList<>();
        // FIX: + → ||  /  query reescrita correctamente para PostgreSQL
        String sqlSimple = """
            SELECT
                p.nombre || ' ' || p.apellido AS nombre,
                p.correo,
                COUNT(*) AS ultimas,
                SUM(CASE WHEN a.estado='AUSENTE' THEN 1 ELSE 0 END) AS ausRecientes
            FROM asistencias a
            JOIN usuarios p ON p.id_usuario = a.id_estudiante
            WHERE a.fecha >= CURRENT_DATE - 14
            GROUP BY p.nombre, p.apellido, p.correo
            HAVING COUNT(*) >= 1
               AND (SUM(CASE WHEN a.estado='AUSENTE' THEN 1 ELSE 0 END)::float
                    / NULLIF(COUNT(*), 0)) >= 0.4
            ORDER BY (SUM(CASE WHEN a.estado='AUSENTE' THEN 1 ELSE 0 END)::float
                      / NULLIF(COUNT(*), 0)) DESC
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sqlSimple);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int total = rs.getInt("ultimas");
                int aus   = rs.getInt("ausRecientes");
                double riesgo = total > 0 ? (double) aus / total * 100 : 0;
                predicciones.add(new String[]{
                        rs.getString("nombre"),
                        rs.getString("correo"),
                        String.format("%.0f%%", riesgo)
                });
            }
        } catch (SQLException e) {
            System.err.println("⚠ Error predicción: " + e.getMessage());
        }
        return predicciones;
    }

    // ══════════════════════════════════════════════════════════
    //  Distribución de puntualidad hoy
    //  [0]=Puntual (antes 7:15), [1]=Tarde (7:15-7:30), [2]=Muy tarde
    // ══════════════════════════════════════════════════════════
    public static int[] distribucionPuntualidad() {
        int[] dist = {0, 0, 0};
        String sql = """
            SELECT
                CASE
                    WHEN fecha_hora::time <= '07:15:00' THEN 0
                    WHEN fecha_hora::time <= '07:30:00' THEN 1
                    ELSE 2
                END AS categoria,
                COUNT(*) AS cantidad
            FROM registro_ingresos
            WHERE fecha_hora::date = CURRENT_DATE
            GROUP BY CASE
                WHEN fecha_hora::time <= '07:15:00' THEN 0
                WHEN fecha_hora::time <= '07:30:00' THEN 1
                ELSE 2
            END
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int cat = rs.getInt("categoria");
                if (cat >= 0 && cat <= 2) dist[cat] = rs.getInt("cantidad");
            }
        } catch (SQLException e) {
            System.err.println("⚠ " + e.getMessage());
        }
        return dist;
    }

    // ══════════════════════════════════════════════════════════
    //  Historial personal de un estudiante
    //  Retorna: {fecha, curso, estado, hora_ingreso}
    // ══════════════════════════════════════════════════════════
    public static List<String[]> historialEstudiante(int idUsuario) {
        List<String[]> historial = new ArrayList<>();
        // FIX: CONVERT(VARCHAR, fecha, 23) → TO_CHAR(fecha, 'YYYY-MM-DD')
        String sql = """
            SELECT
                TO_CHAR(a.fecha, 'YYYY-MM-DD') AS fecha,
                c.nombre_curso,
                a.estado,
                COALESCE(TO_CHAR(ri.fecha_hora, 'HH24:MI:SS'), 'Sin registro') AS hora_ingreso
            FROM asistencias a
            JOIN cursos c ON a.id_curso = c.id_curso
            LEFT JOIN registro_ingresos ri ON ri.id_usuario = a.id_estudiante
                AND ri.fecha_hora::date = a.fecha
            WHERE a.id_estudiante = ?
            ORDER BY a.fecha DESC, c.nombre_curso
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                historial.add(new String[]{
                        rs.getString("fecha"),
                        rs.getString("nombre_curso"),
                        rs.getString("estado"),
                        rs.getString("hora_ingreso")
                });
            }
        } catch (SQLException e) {
            System.err.println("⚠ " + e.getMessage());
        }
        return historial;
    }

    // ══════════════════════════════════════════════════════════
    //  Resumen por curso
    // ══════════════════════════════════════════════════════════
    public static List<String[]> resumenPorCurso() {
        List<String[]> datos = new ArrayList<>();
        String sql = """
            SELECT
                c.nombre_curso,
                c.seccion,
                COUNT(a.id_asistencia) AS total,
                SUM(CASE WHEN a.estado='PRESENTE' THEN 1 ELSE 0 END) AS presentes
            FROM cursos c
            LEFT JOIN asistencias a ON a.id_curso = c.id_curso
            GROUP BY c.id_curso, c.nombre_curso, c.seccion
            ORDER BY c.nombre_curso
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int total = rs.getInt("total");
                int pres  = rs.getInt("presentes");
                double pct = total > 0 ? (double) pres / total * 100 : 0;
                datos.add(new String[]{
                        rs.getString("nombre_curso"),
                        rs.getString("seccion"),
                        String.valueOf(total),
                        String.valueOf(pres),
                        String.format("%.1f%%", pct)
                });
            }
        } catch (SQLException e) {
            System.err.println("⚠ " + e.getMessage());
        }
        return datos;
    }
}