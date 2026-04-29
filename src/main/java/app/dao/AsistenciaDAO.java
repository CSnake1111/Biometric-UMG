package app.dao;

import app.conexion.Conexion;
import app.modelos.Asistencia;
import app.modelos.Usuario;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class AsistenciaDAO {

    /**
     * Confirma la asistencia del día para un curso.
     *
     * Flujo:
     *  1. Para inscritos con entrada en registro_ingresos (biométrico de salón):
     *     insertar/actualizar como PRESENTE o TARDANZA.
     *  2. Para inscritos que ya tienen PRESENTE/TARDANZA registrados manualmente
     *     (desde el panel manual o desde la cámara), no tocar nada.
     *  3. Para todos los demás inscritos sin registro válido hoy: marcar AUSENTE.
     */
    public static boolean confirmarAsistencia(int idCurso, int idSalon, int idCatedratico) {

        // ── PASO 1: promover desde registro_ingresos de salón (biométrico de cámara) ──
        // Inserta o actualiza SOLO si el estado actual es PENDIENTE, AUSENTE o NULL.
        // Nunca sobreescribe un PRESENTE/TARDANZA ya confirmado manualmente.
        String sqlPresentes = """
            INSERT INTO asistencias (id_curso, id_estudiante, fecha, estado, hora_ingreso)
            SELECT
                ? AS id_curso,
                ic.id_estudiante,
                CURRENT_DATE,
                CASE
                    WHEN ri.fecha_hora::time > (SELECT (hora_inicio || ':00')::time
                                                FROM cursos WHERE id_curso = ?)
                    THEN 'TARDANZA'
                    ELSE 'PRESENTE'
                END,
                TO_CHAR(ri.fecha_hora::time, 'HH24:MI:SS')
            FROM inscripciones_curso ic
            INNER JOIN registro_ingresos ri
                ON ri.id_usuario = ic.id_estudiante
               AND ri.id_salon   = ?
               AND ri.fecha_hora::date = CURRENT_DATE
               AND ri.tipo_ingreso = 'SALON'
            WHERE ic.id_curso = ? AND ic.activo = TRUE
            ON CONFLICT (id_curso, id_estudiante, fecha)
            DO UPDATE SET
                estado       = EXCLUDED.estado,
                hora_ingreso = EXCLUDED.hora_ingreso
            WHERE asistencias.estado IN ('AUSENTE', 'PENDIENTE')
               OR asistencias.estado IS NULL
            """;

        // ── PASO 2: marcar AUSENTE a inscritos sin NINGÚN registro válido hoy ──────
        // Solo afecta a quienes tienen estado PENDIENTE o NULL (no toca PRESENTE/TARDANZA/AUSENTE ya puesto).
        String sqlAusentes = """
            INSERT INTO asistencias (id_curso, id_estudiante, fecha, estado, hora_ingreso)
            SELECT
                ? AS id_curso,
                ic.id_estudiante,
                CURRENT_DATE,
                'AUSENTE',
                NULL
            FROM inscripciones_curso ic
            WHERE ic.id_curso = ? AND ic.activo = TRUE
            ON CONFLICT (id_curso, id_estudiante, fecha)
            DO UPDATE SET
                estado       = 'AUSENTE',
                hora_ingreso = NULL
            WHERE asistencias.estado IN ('PENDIENTE')
               OR asistencias.estado IS NULL
            """;

        try (Connection con = Conexion.nuevaConexion()) {
            con.setAutoCommit(false);

            // Paso 1: promover desde registro_ingresos de salón
            try (PreparedStatement ps1 = con.prepareStatement(sqlPresentes)) {
                ps1.setInt(1, idCurso);
                ps1.setInt(2, idCurso);
                ps1.setInt(3, idSalon);
                ps1.setInt(4, idCurso);
                ps1.executeUpdate();
            }

            // Paso 2: ausentes para quienes no tienen registro válido hoy
            try (PreparedStatement ps2 = con.prepareStatement(sqlAusentes)) {
                ps2.setInt(1, idCurso);
                ps2.setInt(2, idCurso);
                ps2.executeUpdate();
            }

            con.commit();
            System.out.println("✅ Asistencia confirmada — curso " + idCurso);
            return true;

        } catch (SQLException e) {
            System.err.println("❌ confirmarAsistencia: " + e.getMessage());
        }
        return false;
    }

    /**
     * FIX PRINCIPAL — Registra un estado de asistencia de forma manual desde el panel.
     *
     * A diferencia de registrarPresente(), este método:
     *  - NO recalcula PRESENTE/TARDANZA por hora: respeta el estado que el catedrático eligió.
     *  - SÍ sobreescribe cualquier estado anterior (AUSENTE, PENDIENTE, PRESENTE, TARDANZA).
     *    El catedrático tiene autoridad para corregir el registro en cualquier momento.
     *  - Hace INSERT si no existe, UPDATE si ya existe (upsert completo sin restricción de estado).
     */
    public static boolean registrarEstadoManual(int idCurso, int idEstudiante, String estado, String horaIngreso) {
        String sql = """
            INSERT INTO asistencias (id_curso, id_estudiante, fecha, estado, hora_ingreso)
            VALUES (?, ?, CURRENT_DATE, ?, ?)
            ON CONFLICT (id_curso, id_estudiante, fecha)
            DO UPDATE SET
                estado       = EXCLUDED.estado,
                hora_ingreso = EXCLUDED.hora_ingreso
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt   (1, idCurso);
            ps.setInt   (2, idEstudiante);
            ps.setString(3, estado);
            ps.setString(4, horaIngreso);
            int filas = ps.executeUpdate();
            System.out.println("✅ Asistencia manual [" + estado + "]: estudiante="
                    + idEstudiante + " curso=" + idCurso + " filas=" + filas);
            return filas > 0;
        } catch (SQLException e) {
            System.err.println("❌ registrarEstadoManual: " + e.getMessage());
        }
        return false;
    }

    /**
     * Registra o actualiza la asistencia de un estudiante como AUSENTE
     * de forma manual. Ahora puede sobreescribir cualquier estado previo
     * para que el catedrático pueda corregir errores.
     *
     * ANTES: solo actualizaba si estado IS NULL OR estado = 'AUSENTE' → bug.
     * AHORA: hace upsert completo (el catedrático manda).
     */
    public static boolean registrarAusenteManual(int idCurso, int idEstudiante) {
        return registrarEstadoManual(idCurso, idEstudiante, "AUSENTE", null);
    }

    /**
     * Registra asistencia PRESENTE o TARDANZA identificada por biométrico.
     * Calcula si es TARDANZA comparando la hora de ingreso con hora_inicio del curso.
     *
     * Este método es para el reconocimiento facial automático, NO para el panel manual.
     * Solo actualiza si el estado previo es AUSENTE, PENDIENTE o NULL
     * (para no pisar un PRESENTE/TARDANZA ya registrado).
     */
    public static boolean registrarPresente(int idCurso, int idEstudiante, String horaIngreso) {
        String sqlCurso = "SELECT hora_inicio FROM cursos WHERE id_curso = ?";
        String estado = "PRESENTE";
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sqlCurso)) {
            ps.setInt(1, idCurso);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                // Bug#3 fix: hora_inicio es VARCHAR "HH:mm", no TIME.
                String horaInicioStr = rs.getString("hora_inicio");
                Time horaInicio = null;
                if (horaInicioStr != null && !horaInicioStr.isBlank()) {
                    try {
                        horaInicio = Time.valueOf(
                                horaInicioStr.length() == 5 ? horaInicioStr + ":00" : horaInicioStr
                        );
                    } catch (Exception ignored) {}
                }
                if (horaIngreso != null && horaInicio != null) {
                    try {
                        Time horaReal = Time.valueOf(horaIngreso.length() == 5
                                ? horaIngreso + ":00" : horaIngreso);
                        if (horaReal.after(horaInicio)) estado = "TARDANZA";
                    } catch (Exception ignored) {}
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ registrarPresente (check hora): " + e.getMessage());
        }

        // Para el biométrico automático: no sobreescribir PRESENTE/TARDANZA ya confirmado.
        String sql = """
            INSERT INTO asistencias (id_curso, id_estudiante, fecha, estado, hora_ingreso)
            VALUES (?, ?, CURRENT_DATE, ?, ?)
            ON CONFLICT (id_curso, id_estudiante, fecha)
            DO UPDATE SET
                estado       = EXCLUDED.estado,
                hora_ingreso = EXCLUDED.hora_ingreso
            WHERE asistencias.estado IN ('AUSENTE', 'PENDIENTE')
               OR asistencias.estado IS NULL
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt   (1, idCurso);
            ps.setInt   (2, idEstudiante);
            ps.setString(3, estado);
            ps.setString(4, horaIngreso);
            ps.executeUpdate();
            System.out.println("✅ Biométrico registrado: estudiante=" + idEstudiante
                    + " curso=" + idCurso + " estado=" + estado);
            return true;
        } catch (SQLException e) {
            System.err.println("❌ registrarPresente: " + e.getMessage());
        }
        return false;
    }

    public static boolean yaIngresoHoy(int idUsuario, int idSalon) {
        String sql = """
            SELECT 1 FROM registro_ingresos
            WHERE id_usuario = ? AND id_salon = ?
              AND fecha_hora::date = CURRENT_DATE
              AND tipo_ingreso = 'SALON'
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            ps.setInt(2, idSalon);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            System.err.println("❌ yaIngresoHoy: " + e.getMessage());
        }
        return false;
    }

    public static List<Asistencia> obtenerAsistenciasCursoFecha(int idCurso, LocalDate fecha) {
        List<Asistencia> list = new ArrayList<>();
        String sql = """
            SELECT a.*, u.nombre, u.apellido, u.foto, u.correo, u.carne, u.carrera
            FROM asistencias a
            JOIN usuarios u ON a.id_estudiante = u.id_usuario
            WHERE a.id_curso = ? AND a.fecha = ?
            ORDER BY u.apellido, u.nombre
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt (1, idCurso);
            ps.setDate(2, Date.valueOf(fecha));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Asistencia a = new Asistencia();
                a.setIdAsistencia(rs.getInt   ("id_asistencia"));
                a.setIdCurso     (rs.getInt   ("id_curso"));
                a.setIdEstudiante(rs.getInt   ("id_estudiante"));
                a.setFecha       (rs.getDate  ("fecha").toLocalDate());
                a.setEstado      (rs.getString("estado"));
                a.setHoraIngreso (rs.getString("hora_ingreso"));
                Usuario u = new Usuario();
                u.setNombre  (rs.getString("nombre"));
                u.setApellido(rs.getString("apellido"));
                u.setFoto    (rs.getString("foto"));
                u.setCorreo  (rs.getString("correo"));
                u.setCarne   (rs.getString("carne"));
                u.setCarrera (rs.getString("carrera"));
                a.setUsuario(u);
                list.add(a);
            }
        } catch (SQLException e) {
            System.err.println("❌ obtenerAsistenciasCursoFecha: " + e.getMessage());
        }
        return list;
    }

    public static List<Asistencia> obtenerAsistenciasHoy(int idCurso) {
        return obtenerAsistenciasCursoFecha(idCurso, LocalDate.now());
    }

    public static int[] contarEstados(int idCurso, LocalDate fecha) {
        int[] r = {0, 0, 0};
        String sql = """
            SELECT estado, COUNT(*) AS cnt
            FROM asistencias WHERE id_curso = ? AND fecha = ?
            GROUP BY estado
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt (1, idCurso);
            ps.setDate(2, Date.valueOf(fecha));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int cnt = rs.getInt("cnt");
                switch (rs.getString("estado")) {
                    case "PRESENTE"  -> r[0] += cnt;
                    case "AUSENTE"   -> r[1] += cnt;
                    case "TARDANZA"  -> r[2] += cnt;
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ contarEstados: " + e.getMessage());
        }
        return r;
    }

    /**
     * Devuelve el id_curso del curso activo hoy cuyo salón coincide con idSalon.
     * Retorna -1 si no hay curso activo en ese salón.
     */
    public static int cursoActivoEnSalon(int idSalon) {
        String sql = """
            SELECT id_curso FROM cursos
            WHERE id_salon = ?
              AND activo = TRUE
              AND dia_semana = fn_dia_semana_es()
              AND CURRENT_TIME BETWEEN
                    (hora_inicio || ':00')::time
                AND (hora_fin   || ':00')::time
            LIMIT 1
            """;
        String sqlFallback = """
            SELECT id_curso FROM cursos
            WHERE id_salon = ? AND activo = TRUE
            ORDER BY id_curso LIMIT 1
            """;
        try (Connection con = Conexion.nuevaConexion()) {
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, idSalon);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getInt("id_curso");
            }
            try (PreparedStatement ps = con.prepareStatement(sqlFallback)) {
                ps.setInt(1, idSalon);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getInt("id_curso");
            }
        } catch (SQLException e) {
            System.err.println("❌ cursoActivoEnSalon: " + e.getMessage());
        }
        return -1;
    }

    /**
     * FIX — forzarEstado: ahora hace UPSERT en vez de solo UPDATE.
     * Antes: si el estudiante no tenía fila en asistencias para hoy,
     * el UPDATE devolvía 0 filas y el estado nunca se guardaba.
     * Ahora: inserta si no existe, siempre actualiza si existe.
     */
    public static boolean forzarEstado(int idCurso, int idEstudiante, String estado, String horaIngreso) {
        return registrarEstadoManual(idCurso, idEstudiante, estado, horaIngreso);
    }

    /**
     * Retorna todas las fechas distintas que tienen asistencias registradas
     * para el curso indicado, ordenadas de más antigua a más reciente.
     */
    public static List<LocalDate> obtenerFechasConAsistencia(int idCurso) {
        List<LocalDate> fechas = new ArrayList<>();
        String sql = """
            SELECT DISTINCT fecha FROM asistencias
            WHERE id_curso = ?
            ORDER BY fecha ASC
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idCurso);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) fechas.add(rs.getDate("fecha").toLocalDate());
        } catch (SQLException e) {
            System.err.println("❌ obtenerFechasConAsistencia: " + e.getMessage());
        }
        return fechas;
    }
}