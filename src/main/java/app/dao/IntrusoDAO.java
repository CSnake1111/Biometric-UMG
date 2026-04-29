package app.dao;

import app.conexion.Conexion;

import java.io.File;
import java.nio.file.Files;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * IntrusoDAO — Registra y consulta intentos de acceso de personas no reconocidas.
 */
public class IntrusoDAO {

    private static final String CARPETA_INTRUSOS = "data/intrusos/";

    // ══════════════════════════════════════════════════════════
    //  Registrar intento de intrusión + guardar foto capturada
    // ══════════════════════════════════════════════════════════
    public static int registrar(String ubicacion, String rutaFoto) {
        new File(CARPETA_INTRUSOS).mkdirs();
        try (Connection con = Conexion.nuevaConexion()) {

            // Copiar foto a carpeta intrusos con timestamp
            String rutaLocal = null;
            if (rutaFoto != null && new File(rutaFoto).exists()) {
                String nombre = "intruder_" + System.currentTimeMillis() + ".jpg";
                rutaLocal = CARPETA_INTRUSOS + nombre;
                Files.copy(new File(rutaFoto).toPath(), new File(rutaLocal).toPath());
            }

            String sql = "INSERT INTO intentos_intrusos (ubicacion, foto_ruta) VALUES (?, ?)";
            try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, ubicacion);
                ps.setString(2, rutaLocal);
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                if (keys.next()) return keys.getInt(1);
            }
        } catch (Exception e) {
            System.err.println("⚠ Error registrando intruder: " + e.getMessage());
        }
        return -1;
    }

    // ══════════════════════════════════════════════════════════
    //  Listar intentos recientes
    //  Retorna: {fechaHora, ubicacion, fotoRuta}
    // ══════════════════════════════════════════════════════════
    public static List<String[]> listarRecientes(int limite) {
        List<String[]> lista = new ArrayList<>();
        String sql = """
            SELECT TO_CHAR(fecha_hora, 'DD/MM/YYYY HH24:MI:SS') AS fecha_hora,
                ubicacion, foto_ruta
            FROM intentos_intrusos
            ORDER BY fecha_hora DESC
            LIMIT ?
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limite);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                lista.add(new String[]{
                    rs.getString("fecha_hora"),
                    rs.getString("ubicacion"),
                    rs.getString("foto_ruta")
                });
            }
        } catch (Exception e) {
            System.err.println("⚠ Error listando intrusos: " + e.getMessage());
        }
        return lista;
    }

    // ══════════════════════════════════════════════════════════
    //  Contar intentos de hoy
    // ══════════════════════════════════════════════════════════
    public static int contarHoy() {
        String sql = """
            SELECT COUNT(*) FROM intentos_intrusos
            WHERE fecha_hora::date = CURRENT_DATE
            """;
        try (Connection con = Conexion.nuevaConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) {
            System.err.println("⚠ " + e.getMessage());
        }
        return 0;
    }

    public static String getCarpetaIntrusos() { return CARPETA_INTRUSOS; }
}
