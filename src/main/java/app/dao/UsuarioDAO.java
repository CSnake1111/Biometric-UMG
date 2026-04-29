package app.dao;

import app.conexion.Conexion;
import app.modelos.Usuario;
import app.seguridad.Seguridad;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UsuarioDAO {

    private static Usuario sesionActiva = null;

    // ══════════════════════════════════════════════════════════════
    //  SESIÓN
    // ══════════════════════════════════════════════════════════════

    public static void     logout()                       { sesionActiva = null; }
    public static Usuario  getSesionActiva()              { return sesionActiva; }
    public static boolean  haySesionActiva()              { return sesionActiva != null; }
    public static void     setSesionActiva(Usuario u)     { sesionActiva = u; }

    /**
     * Devuelve el correo del primer usuario con rol Administrador activo.
     * Se usa para enviar avisos cuando falla el reconocimiento biometrico.
     */
    public static String getCorreoAdmin() {
        String sql = "SELECT u.correo FROM usuarios u " +
                "JOIN roles r ON u.id_rol = r.id_rol " +
                "WHERE r.nombre_rol = 'Administrador' AND u.activo = TRUE " +
                "ORDER BY u.id_usuario LIMIT 1";
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getString("correo");
        } catch (SQLException e) {
            System.err.println("❌ getCorreoAdmin: " + e.getMessage());
        }
        return null;
    }

    // ══════════════════════════════════════════════════════════════
    //  LOGIN POR CONTRASEÑA
    // ══════════════════════════════════════════════════════════════

    public static Usuario login(String nombreUsuario, String password) {
        if (nombreUsuario == null || password == null) return null;

        Connection con = Conexion.nuevaConexion();
        if (con == null) {
            System.err.println("❌ UsuarioDAO.login: sin conexión");
            return null;
        }

        String sql = """
            SELECT u.*, r.nombre_rol
            FROM usuarios u
            JOIN roles r ON u.id_rol = r.id_rol
            WHERE u.usuario = ?
            """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, nombreUsuario.trim());
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                System.err.println("❌ login: usuario no encontrado → " + nombreUsuario);
                return null;
            }
            if (!rs.getBoolean("activo")) {
                System.err.println("❌ login: cuenta inactiva → " + nombreUsuario);
                return null;
            }

            Timestamp bloq = rs.getTimestamp("bloqueado_hasta");
            if (bloq != null && bloq.toLocalDateTime().isAfter(java.time.LocalDateTime.now())) {
                System.err.println("❌ login: cuenta bloqueada → " + nombreUsuario);
                return null;
            }

            String storedHash = rs.getString("password_hash");
            boolean ok = Seguridad.verificarCualquierFormato(password, storedHash);

            if (!ok) {
                System.err.println("❌ login: contraseña incorrecta → " + nombreUsuario);
                return null;
            }

            // Migración automática LEGACY → SALTED
            if (storedHash != null && storedHash.startsWith("LEGACY")) {
                String newHash = Seguridad.hashear(password);
                // FIX: columna correcta es password_hash (no passwordHash)
                try (PreparedStatement upd = con.prepareStatement(
                        "UPDATE usuarios SET password_hash=? WHERE usuario=?")) {
                    upd.setString(1, newHash);
                    upd.setString(2, nombreUsuario.trim());
                    upd.executeUpdate();
                    System.out.println("✅ Hash migrado a SALTED: " + nombreUsuario);
                } catch (SQLException ex) {
                    System.err.println("⚠ No se pudo migrar hash: " + ex.getMessage());
                }
            }

            Usuario u = mapear(rs);
            sesionActiva = u;
            System.out.println("✅ Login exitoso: " + nombreUsuario + " [" + u.getNombreRol() + "]");
            return u;

        } catch (SQLException e) {
            System.err.println("❌ UsuarioDAO.login SQL: " + e.getMessage());
            return null;
        } finally {
            Conexion.cerrarConexion(con);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  LOGIN FACIAL
    // ══════════════════════════════════════════════════════════════

    public static Usuario loginFacial(int idUsuario) {
        // FIX: activo es BOOLEAN en PostgreSQL — comparar con TRUE, no con 1
        String sql = """
            SELECT u.*, r.nombre_rol
            FROM usuarios u JOIN roles r ON u.id_rol = r.id_rol
            WHERE u.id_usuario = ? AND u.activo = TRUE
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Usuario u = mapear(rs);
                sesionActiva = u;
                return u;
            }
        } catch (SQLException e) {
            System.err.println("❌ UsuarioDAO.loginFacial: " + e.getMessage());
        }
        return null;
    }

    // ══════════════════════════════════════════════════════════════
    //  INSERTAR
    // ══════════════════════════════════════════════════════════════

    public static int insertar(Usuario u) {
        // FIX: activo es BOOLEAN — usar TRUE en lugar del entero 1
        // FIX #1: agregar estado=TRUE explícitamente — listarTodos() filtra WHERE estado=TRUE
        String sql = """
            INSERT INTO usuarios
                (carne, nombre, apellido, telefono, correo,
                 tipo_persona, carrera, seccion, foto, qr_codigo, id_rol,
                 usuario, password_hash, activo, estado)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, TRUE)
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1,  u.getCarne());
            ps.setString(2,  u.getNombre());
            ps.setString(3,  u.getApellido());
            ps.setString(4,  u.getTelefono());
            ps.setString(5,  u.getCorreo());
            ps.setString(6,  u.getTipoPersona());
            ps.setString(7,  u.getCarrera());
            ps.setString(8,  u.getSeccion());
            ps.setString(9,  u.getFoto());
            ps.setString(10, u.getQrCodigo());
            ps.setInt   (11, u.getIdRol());
            ps.setString(12, u.getUsuario() != null ? u.getUsuario().trim() : null);
            ps.setString(13, null);

            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        } catch (SQLException e) {
            System.err.println("❌ UsuarioDAO.insertar: " + e.getMessage());
        }
        return -1;
    }

    public static int insertarConPassword(Usuario u, String passwordPlano) {
        if (passwordPlano != null && !passwordPlano.isBlank()) {
            String err = Seguridad.validarContrasena(passwordPlano);
            if (err != null) {
                System.err.println("❌ insertarConPassword: " + err);
                return -1;
            }
        }

        // FIX #1: agregar estado=TRUE explícitamente — listarTodos() filtra WHERE estado=TRUE
        String sql = """
            INSERT INTO usuarios
                (carne, nombre, apellido, telefono, correo,
                 tipo_persona, carrera, seccion, foto, qr_codigo, id_rol,
                 usuario, password_hash, activo, estado)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, TRUE)
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1,  u.getCarne());
            ps.setString(2,  u.getNombre());
            ps.setString(3,  u.getApellido());
            ps.setString(4,  u.getTelefono());
            ps.setString(5,  u.getCorreo());
            ps.setString(6,  u.getTipoPersona());
            ps.setString(7,  u.getCarrera());
            ps.setString(8,  u.getSeccion());
            ps.setString(9,  u.getFoto());
            ps.setString(10, u.getQrCodigo());
            ps.setInt   (11, u.getIdRol());
            ps.setString(12, u.getUsuario() != null ? u.getUsuario().trim() : null);
            ps.setString(13, passwordPlano != null && !passwordPlano.isBlank()
                    ? Seguridad.hashear(passwordPlano) : null);

            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                int id = keys.getInt(1);
                System.out.println("✅ Usuario insertado ID=" + id + " → " + u.getNombreCompleto());
                return id;
            }
        } catch (SQLException e) {
            System.err.println("❌ UsuarioDAO.insertarConPassword: " + e.getMessage());
        }
        return -1;
    }

    // ══════════════════════════════════════════════════════════════
    //  ACTUALIZAR
    // ══════════════════════════════════════════════════════════════

    public static boolean actualizar(Usuario u) {
        // FIX: la columna en la BD es 'activo' (BOOLEAN), no 'estado'
        // FIX: se actualiza id_rol Y tipo_persona juntos para que queden sincronizados
        String sql = """
            UPDATE usuarios SET
                nombre=?, apellido=?, telefono=?, correo=?,
                tipo_persona=?, carrera=?, seccion=?, foto=?,
                id_rol=?, activo=?
            WHERE id_usuario=?
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString (1,  u.getNombre());
            ps.setString (2,  u.getApellido());
            ps.setString (3,  u.getTelefono());
            ps.setString (4,  u.getCorreo());
            ps.setString (5,  u.getTipoPersona());
            ps.setString (6,  u.getCarrera());
            ps.setString (7,  u.getSeccion());
            ps.setString (8,  u.getFoto());
            ps.setInt    (9,  u.getIdRol());
            ps.setBoolean(10, u.isActivo());
            ps.setInt    (11, u.getIdUsuario());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ UsuarioDAO.actualizar: " + e.getMessage());
        }
        return false;
    }

    public static boolean actualizarFoto(int idUsuario, String rutaFoto) {
        String sql = "UPDATE usuarios SET foto=? WHERE id_usuario=?";
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, rutaFoto);
            ps.setInt   (2, idUsuario);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ actualizarFoto: " + e.getMessage());
        }
        return false;
    }

    public static boolean actualizarQR(int idUsuario, String qrCodigo) {
        String sql = "UPDATE usuarios SET qr_codigo=? WHERE id_usuario=?";
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, qrCodigo);
            ps.setInt   (2, idUsuario);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ actualizarQR: " + e.getMessage());
        }
        return false;
    }

    // ══════════════════════════════════════════════════════════════
    //  CREDENCIALES
    // ══════════════════════════════════════════════════════════════

    public static boolean cambiarPassword(int idUsuario, String nueva) {
        String err = Seguridad.validarContrasena(nueva);
        if (err != null) { System.err.println("❌ cambiarPassword: " + err); return false; }
        String sql = "UPDATE usuarios SET password_hash=? WHERE id_usuario=?";
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, Seguridad.hashear(nueva));
            ps.setInt   (2, idUsuario);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ cambiarPassword: " + e.getMessage());
        }
        return false;
    }

    public static boolean habilitarUsuario(int idUsuario)   { return setActivo(idUsuario, true); }
    public static boolean deshabilitarUsuario(int idUsuario) { return setActivo(idUsuario, false); }

    private static boolean setActivo(int idUsuario, boolean activo) {
        String sql = "UPDATE usuarios SET activo=? WHERE id_usuario=?";
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setBoolean(1, activo);
            ps.setInt    (2, idUsuario);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ setActivo: " + e.getMessage());
        }
        return false;
    }

    public static boolean desactivar(int idUsuario) {
        // FIX #2: actualizar AMBAS columnas (activo y estado) para que
        // listarTodos() (filtra por estado) y loginFacial() (filtra por activo)
        // queden sincronizados correctamente.
        String sql = "UPDATE usuarios SET estado=FALSE, activo=FALSE WHERE id_usuario=?";
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ desactivar: " + e.getMessage());
        }
        return false;
    }

    public static boolean cambiarRol(int idUsuario, int idRol) {
        String sql = "UPDATE usuarios SET id_rol=? WHERE id_usuario=?";
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idRol);
            ps.setInt(2, idUsuario);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ cambiarRol: " + e.getMessage());
        }
        return false;
    }

    public static boolean desbloquear(String nombreUsuario) {
        String sql = "UPDATE usuarios SET intentos_fallidos=0, bloqueado_hasta=NULL WHERE usuario=?";
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, nombreUsuario);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ desbloquear: " + e.getMessage());
        }
        return false;
    }

    // ══════════════════════════════════════════════════════════════
    //  BÚSQUEDAS
    // ══════════════════════════════════════════════════════════════

    public static Usuario buscarPorId(int idUsuario) {
        return buscarUno("WHERE u.id_usuario=?", ps -> ps.setInt(1, idUsuario));
    }

    public static Usuario buscarPorIdPersona(int idUsuario) { return buscarPorId(idUsuario); }

    public static Usuario buscarPorCorreo(String correo) {
        return buscarUno("WHERE u.correo=?", ps -> ps.setString(1, correo));
    }

    public static boolean correoExiste(String correo) {
        String sql = "SELECT COUNT(*) FROM usuarios WHERE correo=?";
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, correo);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1) > 0;
        } catch (SQLException e) {
            System.err.println("❌ correoExiste: " + e.getMessage());
        }
        return false;
    }

    public static boolean usuarioExiste(String nombreUsuario) {
        String sql = "SELECT 1 FROM usuarios WHERE usuario=?";
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, nombreUsuario);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            System.err.println("❌ usuarioExiste: " + e.getMessage());
        }
        return false;
    }

    public static boolean estaBloqueado(String nombreUsuario) {
        String sql = "SELECT bloqueado_hasta FROM usuarios WHERE usuario=?";
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, nombreUsuario);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Timestamp ts = rs.getTimestamp("bloqueado_hasta");
                return ts != null && ts.toLocalDateTime().isAfter(java.time.LocalDateTime.now());
            }
        } catch (SQLException e) {
            System.err.println("❌ estaBloqueado: " + e.getMessage());
        }
        return false;
    }

    // ══════════════════════════════════════════════════════════════
    //  LISTADOS
    // ══════════════════════════════════════════════════════════════

    public static List<Usuario> listarTodos() {
        // FIX: estado es BOOLEAN — usar estado = TRUE, no estado=true (string)
        return listarConFiltro("WHERE u.estado = TRUE ORDER BY u.apellido, u.nombre");
    }

    public static List<Usuario> listarPorTipo(String tipoPersona) {
        List<Usuario> lista = new ArrayList<>();
        // FIX: estado es BOOLEAN — usar estado = TRUE
        String sql = SELECT_BASE + " WHERE u.tipo_persona=? AND u.estado = TRUE ORDER BY u.apellido, u.nombre";
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, tipoPersona);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) lista.add(mapear(rs));
        } catch (SQLException e) {
            System.err.println("❌ listarPorTipo: " + e.getMessage());
        }
        return lista;
    }

    public static List<Usuario> buscar(String termino) {
        List<Usuario> lista = new ArrayList<>();
        String like = "%" + termino + "%";
        // FIX: LIKE en PostgreSQL para varchar usa ILIKE (case-insensitive) o LIKE
        //      La concatenación debe hacerse en Java, no con || en el prepared statement
        String sql = SELECT_BASE + """
             WHERE u.estado = TRUE
               AND (u.nombre ILIKE ? OR u.apellido ILIKE ?
                    OR u.carne ILIKE ? OR u.correo ILIKE ?)
            ORDER BY u.apellido, u.nombre
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, like); ps.setString(2, like);
            ps.setString(3, like); ps.setString(4, like);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) lista.add(mapear(rs));
        } catch (SQLException e) {
            System.err.println("❌ buscar: " + e.getMessage());
        }
        return lista;
    }

    public static List<Usuario> listarEstudiantesPorCurso(int idCurso) {
        List<Usuario> lista = new ArrayList<>();
        // FIX: SELECT DISTINCT con ORDER BY en PostgreSQL requiere que las columnas
        // del ORDER BY estén incluidas en el SELECT. Se reemplaza DISTINCT por GROUP BY
        // que agrupa correctamente sin restricción en el ORDER BY.
        String sql = """
            SELECT u.id_usuario, u.usuario, u.nombre, u.apellido, u.correo,
                   u.carne, u.foto, u.tipo_persona, u.estado, u.id_rol,
                   u.activo, u.intentos_fallidos, u.bloqueado_hasta, u.ultimo_login,
                   r.nombre_rol
            FROM usuarios u
            LEFT JOIN roles r ON u.id_rol = r.id_rol
            INNER JOIN asistencias a ON a.id_estudiante = u.id_usuario
            WHERE a.id_curso=? AND u.estado = TRUE
            GROUP BY u.id_usuario, u.usuario, u.nombre, u.apellido, u.correo,
                     u.carne, u.foto, u.tipo_persona, u.estado, u.id_rol,
                     u.activo, u.intentos_fallidos, u.bloqueado_hasta, u.ultimo_login,
                     r.nombre_rol
            ORDER BY u.apellido, u.nombre
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idCurso);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) lista.add(mapear(rs));
        } catch (SQLException e) {
            System.err.println("❌ listarEstudiantesPorCurso: " + e.getMessage());
        }
        return lista;
    }

    public static List<Usuario> listarEstudiantesInscritos(int idCurso) {
        List<Usuario> lista = new ArrayList<>();
        // FIX: i.activo es BOOLEAN — usar i.activo = TRUE
        String sql = """
            SELECT u.*, r.nombre_rol
            FROM usuarios u
            JOIN inscripciones_curso i ON i.id_estudiante = u.id_usuario
            LEFT JOIN roles r ON u.id_rol = r.id_rol
            WHERE i.id_curso=? AND i.activo = TRUE AND u.estado = TRUE
            ORDER BY u.apellido, u.nombre
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idCurso);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) lista.add(mapear(rs));
        } catch (SQLException e) {
            System.err.println("❌ listarEstudiantesInscritos: " + e.getMessage());
        }
        return lista;
    }

    public static List<String[]> listarTodosConNombre() {
        List<String[]> list = new ArrayList<>();
        // FIX: u.activo=false → u.activo = FALSE  /  u.activo=true no aplica aquí pero se corrige también
        //      La concatenación de strings con || está bien en SQL, pero el tipo debe ser correcto
        String sql = """
            SELECT u.id_usuario, u.usuario, r.nombre_rol,
                   u.intentos_fallidos, u.bloqueado_hasta, u.ultimo_login,
                   CASE
                     WHEN u.activo = FALSE THEN 'Inactivo'
                     WHEN u.bloqueado_hasta IS NOT NULL
                          AND u.bloqueado_hasta > NOW() THEN 'Bloqueado'
                     ELSE 'Activo'
                   END AS estado,
                   u.activo::text AS activoRaw,
                   u.nombre || ' ' || u.apellido AS nombreCompleto
            FROM usuarios u
            JOIN roles r ON u.id_rol = r.id_rol
            ORDER BY u.usuario
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next())
                list.add(new String[]{
                        rs.getString("id_usuario"),
                        rs.getString("usuario"),
                        rs.getString("nombre_rol"),
                        rs.getString("intentos_fallidos"),
                        rs.getString("bloqueado_hasta"),
                        rs.getString("ultimo_login"),
                        rs.getString("estado"),
                        rs.getString("activoRaw"),
                        rs.getString("nombreCompleto")
                });
        } catch (SQLException e) {
            System.err.println("❌ listarTodosConNombre: " + e.getMessage());
        }
        return list;
    }

    // ══════════════════════════════════════════════════════════════
    //  INTERNOS
    // ══════════════════════════════════════════════════════════════

    private static final String SELECT_BASE = """
            SELECT u.*, r.nombre_rol
            FROM usuarios u
            LEFT JOIN roles r ON u.id_rol = r.id_rol
            """;

    /**
     * Resuelve el nombre del rol desde id_rol usando la tabla roles de la BD.
     * Fallback cuando nombre_rol no viene en el ResultSet (LEFT JOIN fallido o columna ausente).
     * Los IDs del INSERT inicial son: 1=Administrador, 2=Catedratico, 3=Estudiante,
     * 4=Seguridad, 5=Mantenimiento, 6=Administrativo.
     */
    public static String resolverNombreRol(int idRol) {
        // Primero intentar desde la BD para no depender de IDs hardcodeados
        String sql = "SELECT nombre_rol FROM roles WHERE id_rol = ?";
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idRol);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("nombre_rol");
        } catch (SQLException ignored) {}
        // Fallback estático si la BD no responde
        return switch (idRol) {
            case 1 -> "Administrador";
            case 2 -> "Catedratico";
            case 3 -> "Estudiante";
            case 4 -> "Seguridad";
            case 5 -> "Mantenimiento";
            case 6 -> "Administrativo";
            default -> "Desconocido";
        };
    }

    @FunctionalInterface
    interface Setter { void set(PreparedStatement ps) throws SQLException; }

    private static Usuario buscarUno(String where, Setter setter) {
        String sql = SELECT_BASE + where;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            setter.set(ps);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapear(rs);
        } catch (SQLException e) {
            System.err.println("❌ buscarUno: " + e.getMessage());
        }
        return null;
    }

    private static List<Usuario> listarConFiltro(String filtro) {
        List<Usuario> lista = new ArrayList<>();
        String sql = SELECT_BASE + filtro;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lista.add(mapear(rs));
        } catch (SQLException e) {
            System.err.println("❌ listarConFiltro: " + e.getMessage());
        }
        return lista;
    }

    public static Usuario mapear(ResultSet rs) throws SQLException {
        Usuario u = new Usuario();
        u.setIdUsuario   (rs.getInt    ("id_usuario"));
        u.setCarne       (rs.getString ("carne"));
        u.setNombre      (rs.getString ("nombre"));
        u.setApellido    (rs.getString ("apellido"));
        u.setTelefono    (rs.getString ("telefono"));
        u.setCorreo      (rs.getString ("correo"));
        u.setTipoPersona (rs.getString ("tipo_persona"));
        u.setCarrera     (rs.getString ("carrera"));
        u.setSeccion     (rs.getString ("seccion"));
        u.setFoto        (rs.getString ("foto"));
        u.setQrCodigo    (rs.getString ("qr_codigo"));
        u.setEstado      (rs.getBoolean("estado"));
        u.setIdRol       (rs.getInt    ("id_rol"));
        u.setActivo      (rs.getBoolean("activo"));

        try { u.setUsuario(rs.getString("usuario")); }           catch (SQLException ignored) {}
        try { u.setNombreRol(rs.getString("nombre_rol")); }      catch (SQLException ignored) {}
        // FIX: si nombre_rol no vino en el ResultSet (catch lo dejó null), resolverlo
        // desde id_rol usando el mapa estático de roles conocidos. Esto evita que
        // verificaciones de acceso con equals("Administrador") fallen silenciosamente.
        if (u.getNombreRol() == null && u.getIdRol() > 0) {
            u.setNombreRol(resolverNombreRol(u.getIdRol()));
        }
        try { u.setIntentosFallidos(rs.getInt("intentos_fallidos")); } catch (SQLException ignored) {}

        try {
            Timestamp ts = rs.getTimestamp("fecha_registro");
            if (ts != null) u.setFechaRegistro(ts.toLocalDateTime());
        } catch (SQLException ignored) {}
        try {
            Timestamp bl = rs.getTimestamp("bloqueado_hasta");
            if (bl != null) u.setBloqueadoHasta(bl.toLocalDateTime());
        } catch (SQLException ignored) {}
        try {
            Timestamp ul = rs.getTimestamp("ultimo_login");
            if (ul != null) u.setUltimoLogin(ul.toLocalDateTime());
        } catch (SQLException ignored) {}

        return u;
    }
}