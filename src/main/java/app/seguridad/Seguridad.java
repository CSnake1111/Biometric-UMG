package app.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * BiometricUMG 2.0 — Seguridad
 *
 * Formatos de hash soportados:
 *   1. SALTED  →  "base64salt:base64hash"          (nuevo, seguro)
 *   2. LEGACY  →  "LEGACY:HEXSTRING"               (migración desde SQL HASHBYTES UTF-16LE)
 *   3. LEGACY2 →  "LEGACY2:HEXSTRING"              (migración desde SHA-256 plain UTF-8)
 *
 * Al primer login exitoso con LEGACY/LEGACY2 el DAO re-hashea automáticamente al
 * formato SALTED, por lo que la base solo contiene contraseñas antiguas durante
 * la transición.
 */
public class Seguridad {

    private static final SecureRandom RNG = new SecureRandom();

    // ──────────────────────────────────────────────────────────────
    //  HASH PRINCIPAL (salted SHA-256, formato nuevo)
    // ──────────────────────────────────────────────────────────────

    /** Genera un hash salted seguro para almacenar. */
    public static String hashear(String plain) {
        byte[] salt = new byte[16];
        RNG.nextBytes(salt);
        return encode(salt) + ":" + encode(sha256(salt, plain));
    }

    /** Verifica contra el hash salted nuevo. */
    public static boolean verificar(String plain, String stored) {
        if (plain == null || stored == null || !stored.contains(":")) return false;
        // Rechazar si es un formato LEGACY (tiene prefijo)
        if (stored.startsWith("LEGACY")) return false;
        try {
            String[] parts = stored.split(":", 2);
            byte[] salt  = Base64.getDecoder().decode(parts[0]);
            byte[] hash  = Base64.getDecoder().decode(parts[1]);
            byte[] check = sha256(salt, plain);
            if (hash.length != check.length) return false;
            int diff = 0;
            for (int i = 0; i < hash.length; i++) diff |= (hash[i] ^ check[i]);
            return diff == 0;
        } catch (Exception e) { return false; }
    }

    // ──────────────────────────────────────────────────────────────
    //  LEGACY: SQL Server HASHBYTES('SHA2_256', CAST(p AS NVARCHAR)) → UTF-16LE
    // ──────────────────────────────────────────────────────────────

    /**
     * Verifica formato LEGACY: "LEGACY:53A39C..."
     * El hash fue generado por SQL Server con UTF-16LE (NVARCHAR).
     */
    public static boolean verificarLegacy(String plain, String stored) {
        if (plain == null || stored == null) return false;
        if (!stored.startsWith("LEGACY:")) return false;
        try {
            String hexHash = stored.substring(7); // quitar "LEGACY:"
            // SQL Server CONVERT(...,1) incluye prefijo "0x" — quitarlo si está
            if (hexHash.startsWith("0x") || hexHash.startsWith("0X"))
                hexHash = hexHash.substring(2);
            byte[] storedBytes = hexToBytes(hexHash);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            // SQL Server HASHBYTES con NVARCHAR usa UTF-16LE
            byte[] input   = plain.getBytes("UTF-16LE");
            byte[] computed = md.digest(input);
            if (computed.length != storedBytes.length) return false;
            int diff = 0;
            for (int i = 0; i < computed.length; i++) diff |= (computed[i] ^ storedBytes[i]);
            return diff == 0;
        } catch (Exception e) { return false; }
    }

    // ──────────────────────────────────────────────────────────────
    //  LEGACY2: SHA-256 plain UTF-8 (por si acaso)
    // ──────────────────────────────────────────────────────────────

    /**
     * Verifica formato LEGACY2: "LEGACY2:HEXSTRING"
     * Hash generado con SHA-256 + UTF-8 sin sal (formato intermedio).
     */
    public static boolean verificarLegacy2(String plain, String stored) {
        if (plain == null || stored == null) return false;
        if (!stored.startsWith("LEGACY2:")) return false;
        try {
            String hexHash = stored.substring(8);
            if (hexHash.startsWith("0x") || hexHash.startsWith("0X"))
                hexHash = hexHash.substring(2);
            byte[] storedBytes = hexToBytes(hexHash);
            MessageDigest md   = MessageDigest.getInstance("SHA-256");
            byte[] computed    = md.digest(plain.getBytes(StandardCharsets.UTF_8));
            if (computed.length != storedBytes.length) return false;
            int diff = 0;
            for (int i = 0; i < computed.length; i++) diff |= (computed[i] ^ storedBytes[i]);
            return diff == 0;
        } catch (Exception e) { return false; }
    }

    // ──────────────────────────────────────────────────────────────
    //  MÉTODO UNIFICADO — detecta formato automáticamente
    // ──────────────────────────────────────────────────────────────

    /**
     * Detecta el formato del hash almacenado y verifica con el método correcto.
     * Devuelve true si la contraseña coincide.
     */
    public static boolean verificarCualquierFormato(String plain, String stored) {
        if (plain == null || stored == null) return false;
        if (stored.startsWith("LEGACY2:"))  return verificarLegacy2(plain, stored);
        if (stored.startsWith("LEGACY:"))   return verificarLegacy(plain, stored);
        return verificar(plain, stored);
    }

    // ──────────────────────────────────────────────────────────────
    //  UTILIDADES
    // ──────────────────────────────────────────────────────────────

    public static String validarContrasena(String p) {
        if (p == null || p.length() < 8) return "Mínimo 8 caracteres.";
        if (!p.matches(".*[A-Z].*"))     return "Debe contener al menos una mayúscula.";
        if (!p.matches(".*[0-9].*"))     return "Debe contener al menos un número.";
        return null;
    }

    public static String sanitizar(String input) {
        if (input == null) return "";
        return input.replaceAll("[';\"\\\\<>]", "").trim();
    }

    public static String generarOTP() {
        byte[] b = new byte[32]; RNG.nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    public static String generarToken() {
        return String.format("%06d", RNG.nextInt(1_000_000));
    }

    // ──────────────────────────────────────────────────────────────
    //  PRIVADOS
    // ──────────────────────────────────────────────────────────────

    private static byte[] sha256(byte[] salt, String plain) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            md.update(plain.getBytes(StandardCharsets.UTF_8));
            return md.digest();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static String encode(byte[] b) { return Base64.getEncoder().encodeToString(b); }

    public static byte[] hexToBytes(String hex) {
        if (hex.startsWith("0x") || hex.startsWith("0X")) hex = hex.substring(2);
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2)
            out[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        return out;
    }
}