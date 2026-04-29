package app.dao;

import app.conexion.Conexion;
import app.modelos.AuditoriaAccion;
import app.modelos.IntentoLogin;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AuditoriaDAO {

    // ──────────────────────────────────────────────────────
    //  Core registration
    // ──────────────────────────────────────────────────────

    public static void registrarAccion(String descripcion, String modulo) {
        app.modelos.Usuario u = UsuarioDAO.getSesionActiva();
        registrarCompleto(
                u != null ? u.getIdUsuario()  : null,
                u != null ? u.getUsuario()    : "SISTEMA",
                u != null ? u.getNombreRol()  : "SISTEMA",
                modulo, descripcion, "EXITOSO"
        );
    }

    public static void registrarFallido(String descripcion, String modulo) {
        app.modelos.Usuario u = UsuarioDAO.getSesionActiva();
        registrarCompleto(
                u != null ? u.getIdUsuario()  : null,
                u != null ? u.getUsuario()    : "DESCONOCIDO",
                u != null ? u.getNombreRol()  : "DESCONOCIDO",
                modulo, descripcion, "FALLIDO"
        );
    }

    public static void registrar(String accion, String modulo, String descripcion) {
        app.modelos.Usuario u = UsuarioDAO.getSesionActiva();
        registrarCompleto(
                u != null ? u.getIdUsuario()  : null,
                u != null ? u.getUsuario()    : "SISTEMA",
                u != null ? u.getNombreRol()  : "SISTEMA",
                modulo, descripcion != null ? accion + ": " + descripcion : accion, "EXITOSO"
        );
    }

    public static void registrar(String tabla, String operacion, Integer idRegistro,
                                 String descripcion, String campo,
                                 String valorAnterior, String valorNuevo) {
        registrarAccion(descripcion + (campo != null ? " [" + campo + "]" : ""), tabla);
    }

    private static void registrarCompleto(Integer idUsuario, String nombreUsuario,
                                          String rol, String modulo,
                                          String descripcion, String resultado) {
        // FIX: columna correcta es fecha_hora (no fechaHora en snake_case)
        String sql = """
            INSERT INTO auditoria_acciones
                (id_usuario, nombre_usuario, rol, accion, modulo, descripcion, ip, resultado, fecha_hora)
            VALUES (?,?,?,?,?,?,?,?,NOW())
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (idUsuario != null) ps.setInt(1, idUsuario);
            else ps.setNull(1, Types.INTEGER);
            ps.setString(2, nombreUsuario);
            ps.setString(3, rol);
            ps.setString(4, descripcion); // FIX #9: columna 'accion' debe recibir la descripción/acción
            ps.setString(5, modulo);      // columna 'modulo' recibe el módulo
            ps.setString(6, descripcion);
            ps.setString(7, obtenerIP());
            ps.setString(8, resultado);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("⚠ AuditoriaDAO: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────
    //  Login attempts
    // ──────────────────────────────────────────────────────

    public static void registrarIntentoLogin(String usuario, boolean exitoso,
                                             String metodo, String motivoFallo) {
        try (Connection con = Conexion.nuevaConexion()) {
            if (con == null) return;

            // FIX: columna correcta es fecha_hora (no fechaHora)
            String sqlIns = """
                INSERT INTO intentos_login (usuario, ip, exitoso, metodo, fecha_hora)
                VALUES (?,?,?,?,NOW())
                """;
            try (PreparedStatement ps = con.prepareStatement(sqlIns)) {
                ps.setString (1, usuario);
                ps.setString (2, obtenerIP());
                ps.setBoolean(3, exitoso);
                ps.setString (4, metodo != null ? metodo : "PASSWORD");
                ps.executeUpdate();
            }

            if (!exitoso) {
                String sqlLock = """
                    UPDATE usuarios
                    SET intentos_fallidos = COALESCE(intentos_fallidos,0) + 1,
                        bloqueado_hasta = CASE
                            WHEN COALESCE(intentos_fallidos,0) + 1 >= 5
                            THEN NOW() + INTERVAL '15 minutes'
                            ELSE bloqueado_hasta END
                    WHERE usuario = ?
                    """;
                try (PreparedStatement ps = con.prepareStatement(sqlLock)) {
                    ps.setString(1, usuario);
                    ps.executeUpdate();
                }

                // FIX: concatenación con || en PostgreSQL requiere que ambos operandos sean text
                //      Se usa CAST explícito para evitar "character varying + unknown"
                String sqlAlert = """
                    INSERT INTO alertas_sistema (tipo, descripcion, ip)
                    SELECT 'BLOQUEO_CUENTA',
                           'Account locked: ' || CAST(? AS TEXT),
                           CAST(? AS TEXT)
                    WHERE EXISTS (
                        SELECT 1 FROM usuarios WHERE usuario=? AND intentos_fallidos>=5)
                    AND NOT EXISTS (
                        SELECT 1 FROM alertas_sistema
                        WHERE tipo='BLOQUEO_CUENTA'
                          AND descripcion LIKE '%' || CAST(? AS TEXT) || '%'
                          AND resuelta = FALSE
                          AND fecha_hora > NOW() - INTERVAL '15 minutes')
                    """;
                try (PreparedStatement ps = con.prepareStatement(sqlAlert)) {
                    ps.setString(1, usuario); ps.setString(2, obtenerIP());
                    ps.setString(3, usuario); ps.setString(4, usuario);
                    ps.executeUpdate();
                } catch (SQLException ignored) {}
            } else {
                String sqlReset = """
                    UPDATE usuarios SET intentos_fallidos=0, bloqueado_hasta=NULL,
                    ultimo_login=NOW(), ip_ultimo_login=?
                    WHERE usuario=?
                    """;
                try (PreparedStatement ps = con.prepareStatement(sqlReset)) {
                    ps.setString(1, obtenerIP());
                    ps.setString(2, usuario);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.err.println("⚠ registrarIntentoLogin: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────
    //  Query methods
    // ──────────────────────────────────────────────────────

    public static boolean estaBloqueado(String usuario) {
        String sql = "SELECT bloqueado_hasta FROM usuarios WHERE usuario=?";
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, usuario);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Timestamp ts = rs.getTimestamp("bloqueado_hasta");
                return ts != null && ts.toLocalDateTime().isAfter(LocalDateTime.now());
            }
        } catch (SQLException e) {
            System.err.println("⚠ estaBloqueado: " + e.getMessage());
        }
        return false;
    }

    public static LocalDateTime getBloqueadoHasta(String usuario) {
        String sql = "SELECT bloqueado_hasta FROM usuarios WHERE usuario=?";
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, usuario);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Timestamp ts = rs.getTimestamp("bloqueado_hasta");
                return ts != null ? ts.toLocalDateTime() : null;
            }
        } catch (SQLException e) {
            System.err.println("⚠ getBloqueadoHasta: " + e.getMessage());
        }
        return null;
    }

    public static boolean desbloquearUsuario(String usuario) {
        String sql = "UPDATE usuarios SET intentos_fallidos=0, bloqueado_hasta=NULL WHERE usuario=?";
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, usuario);
            boolean ok = ps.executeUpdate() > 0;
            if (ok) registrarAccion("Account unlocked: " + usuario, "SEGURIDAD");
            return ok;
        } catch (SQLException e) {
            System.err.println("⚠ desbloquearUsuario: " + e.getMessage());
        }
        return false;
    }

    public static List<AuditoriaAccion> listarAcciones(String filtroModulo,
                                                       String filtroResultado,
                                                       int limite) {
        List<AuditoriaAccion> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT id_auditoria, id_usuario, nombre_usuario, rol,
                   accion, modulo, descripcion, valor_anterior, valor_nuevo,
                   ip, resultado, fecha_hora
            FROM auditoria_acciones WHERE 1=1
            """);
        List<Object> params = new ArrayList<>();
        if (filtroModulo != null && !filtroModulo.isBlank() && !"Todos".equals(filtroModulo)) {
            sql.append(" AND modulo=?"); params.add(filtroModulo);
        }
        if (filtroResultado != null && !filtroResultado.isBlank() && !"Todos".equals(filtroResultado)) {
            sql.append(" AND resultado=?"); params.add(filtroResultado);
        }
        sql.append(" ORDER BY fecha_hora DESC LIMIT ?");
        params.add(limite);   // LIMIT siempre va al final

        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                AuditoriaAccion a = new AuditoriaAccion();
                a.setIdAuditoria  (rs.getLong  ("id_auditoria"));
                a.setIdUsuario    (rs.getInt   ("id_usuario"));
                a.setNombreUsuario(rs.getString("nombre_usuario"));
                a.setRol          (rs.getString("rol"));
                a.setAccion       (rs.getString("accion"));
                a.setModulo       (rs.getString("modulo"));
                a.setDescripcion  (rs.getString("descripcion"));
                a.setValorAnterior(rs.getString("valor_anterior"));
                a.setValorNuevo   (rs.getString("valor_nuevo"));
                a.setIp           (rs.getString("ip"));
                a.setResultado    (rs.getString("resultado"));
                Timestamp ts = rs.getTimestamp("fecha_hora");
                if (ts != null) a.setFechaHora(ts.toLocalDateTime());
                list.add(a);
            }
        } catch (SQLException e) {
            System.err.println("⚠ listarAcciones: " + e.getMessage());
        }
        return list;
    }

    public static List<IntentoLogin> listarIntentos(String filtroResultado, int limite) {
        List<IntentoLogin> list = new ArrayList<>();
        // FIX: exitoso es BOOLEAN — usar exitoso = TRUE / FALSE en lugar de exitoso=true/false (string)
        StringBuilder sql = new StringBuilder(
                "SELECT id_intento, usuario, ip, exitoso, metodo, fecha_hora FROM intentos_login WHERE 1=1");
        if ("EXITOSO".equals(filtroResultado)) { sql.append(" AND exitoso = TRUE"); }
        if ("FALLIDO".equals(filtroResultado)) { sql.append(" AND exitoso = FALSE"); }
        sql.append(" ORDER BY fecha_hora DESC LIMIT ?");

        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            ps.setInt(1, limite);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                IntentoLogin il = new IntentoLogin();
                il.setIdIntento(rs.getLong   ("id_intento"));
                il.setUsuario  (rs.getString ("usuario"));
                il.setIp       (rs.getString ("ip"));
                il.setExitoso  (rs.getBoolean("exitoso"));
                il.setMetodo   (rs.getString ("metodo"));
                Timestamp ts = rs.getTimestamp("fecha_hora");
                if (ts != null) il.setFechaHora(ts.toLocalDateTime());
                list.add(il);
            }
        } catch (SQLException e) {
            System.err.println("⚠ listarIntentos: " + e.getMessage());
        }
        return list;
    }

    public static int contarIntentosFallidos(String usuario, int minutosAtras) {
        String sql = """
            SELECT COUNT(*) FROM intentos_login
            WHERE usuario=? AND exitoso = FALSE
              AND fecha_hora > NOW() - (? * INTERVAL '1 minute')
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, usuario);
            ps.setInt   (2, minutosAtras);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("⚠ contarIntentosFallidos: " + e.getMessage());
        }
        return 0;
    }

    public static int contarAlertasActivas() {
        // FIX: resuelta es BOOLEAN — usar resuelta = FALSE
        String sql = "SELECT COUNT(*) FROM alertas_sistema WHERE resuelta = FALSE";
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("⚠ contarAlertasActivas: " + e.getMessage());
        }
        return 0;
    }

    public static boolean resolverAlerta(int idAlerta) {
        // FIX: columna correcta es id_alerta (no idAlerta)
        String sql = "UPDATE alertas_sistema SET resuelta = TRUE WHERE id_alerta=?";
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idAlerta);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("⚠ resolverAlerta: " + e.getMessage());
        }
        return false;
    }

    public static int[] resumenHoy() {
        int[] r = {0, 0, 0};
        String sql = """
            SELECT
                COUNT(*) AS total,
                SUM(CASE WHEN resultado='EXITOSO' THEN 1 ELSE 0 END) AS exitosas,
                SUM(CASE WHEN resultado='FALLIDO'  THEN 1 ELSE 0 END) AS fallidas
            FROM auditoria_acciones
            WHERE fecha_hora::date = CURRENT_DATE
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                r[0] = rs.getInt("total");
                r[1] = rs.getInt("exitosas");
                r[2] = rs.getInt("fallidas");
            }
        } catch (SQLException e) {
            System.err.println("⚠ resumenHoy: " + e.getMessage());
        }
        return r;
    }

    public static List<app.modelos.AlertaSistema> listarAlertas(boolean soloActivas) {
        List<app.modelos.AlertaSistema> list = new ArrayList<>();
        // FIX: resuelta es BOOLEAN — usar resuelta = FALSE
        String sql = "SELECT id_alerta, tipo, descripcion, id_usuario, ip, resuelta, fecha_hora "
                + "FROM alertas_sistema"
                + (soloActivas ? " WHERE resuelta = FALSE" : "")
                + " ORDER BY fecha_hora DESC LIMIT 200";
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                app.modelos.AlertaSistema al = new app.modelos.AlertaSistema();
                al.setIdAlerta   (rs.getInt   ("id_alerta"));
                al.setTipo       (rs.getString("tipo"));
                al.setDescripcion(rs.getString("descripcion"));
                int uid = rs.getInt("id_usuario");
                al.setIdUsuario  (rs.wasNull() ? null : uid);
                al.setIp         (rs.getString("ip"));
                al.setResuelta   (rs.getBoolean("resuelta"));
                Timestamp ts = rs.getTimestamp("fecha_hora");
                if (ts != null) al.setFechaHora(ts.toLocalDateTime());
                list.add(al);
            }
        } catch (SQLException e) {
            System.err.println("⚠ listarAlertas: " + e.getMessage());
        }
        return list;
    }

    private static String obtenerIP() {
        try { return java.net.InetAddress.getLocalHost().getHostAddress(); }
        catch (Exception e) { return "127.0.0.1"; }
    }
}