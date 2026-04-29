package app.servicios;

import java.io.*;
import java.security.*;
import java.security.spec.*;
import java.util.Base64;

/**
 * Firma Electrónica RSA-SHA256
 * Genera par de claves, firma datos y verifica firmas
 */
public class FirmaService {

    private static final String CARPETA_CLAVES = "data/claves/";
    private static final String RUTA_PRIVADA   = CARPETA_CLAVES + "privada.key";
    private static final String RUTA_PUBLICA   = CARPETA_CLAVES + "publica.key";

    private static PrivateKey clavePrivada;
    private static PublicKey  clavePublica;

    // ══════════════════════════════════════════
    //  Inicializar — cargar o generar claves
    // ══════════════════════════════════════════
    public static void inicializar() {
        new File(CARPETA_CLAVES).mkdirs();
        try {
            if (new File(RUTA_PRIVADA).exists() && new File(RUTA_PUBLICA).exists()) {
                cargarClaves();
                System.out.println("✅ Claves RSA cargadas correctamente");
            } else {
                generarClaves();
                System.out.println("✅ Claves RSA generadas correctamente");
            }
        } catch (Exception e) {
            System.err.println("❌ Error inicializando firma: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════
    //  Generar par de claves RSA-2048
    // ══════════════════════════════════════════
    private static void generarClaves() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048, new SecureRandom());
        KeyPair par = gen.generateKeyPair();
        clavePrivada = par.getPrivate();
        clavePublica = par.getPublic();

        // Guardar en disco
        try (FileOutputStream fos = new FileOutputStream(RUTA_PRIVADA)) {
            fos.write(par.getPrivate().getEncoded());
        }
        try (FileOutputStream fos = new FileOutputStream(RUTA_PUBLICA)) {
            fos.write(par.getPublic().getEncoded());
        }
    }

    // ══════════════════════════════════════════
    //  Cargar claves desde disco
    // ══════════════════════════════════════════
    private static void cargarClaves() throws Exception {
        KeyFactory kf = KeyFactory.getInstance("RSA");

        byte[] bytesPrivada = new FileInputStream(RUTA_PRIVADA).readAllBytes();
        clavePrivada = kf.generatePrivate(new PKCS8EncodedKeySpec(bytesPrivada));

        byte[] bytesPublica = new FileInputStream(RUTA_PUBLICA).readAllBytes();
        clavePublica = kf.generatePublic(new X509EncodedKeySpec(bytesPublica));
    }

    // ══════════════════════════════════════════
    //  Firmar datos con clave privada RSA-SHA256
    // ══════════════════════════════════════════
    public static String firmar(String datos) {
        if (clavePrivada == null) inicializar();
        try {
            Signature firma = Signature.getInstance("SHA256withRSA");
            firma.initSign(clavePrivada);
            firma.update(datos.getBytes("UTF-8"));
            byte[] bytes = firma.sign();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (Exception e) {
            System.err.println("❌ Error firmando: " + e.getMessage());
            return null;
        }
    }

    // ══════════════════════════════════════════
    //  Verificar firma
    // ══════════════════════════════════════════
    public static boolean verificar(String datos, String firmaBase64) {
        if (clavePublica == null) inicializar();
        try {
            Signature verificador = Signature.getInstance("SHA256withRSA");
            verificador.initVerify(clavePublica);
            verificador.update(datos.getBytes("UTF-8"));
            byte[] bytes = Base64.getUrlDecoder().decode(firmaBase64);
            return verificador.verify(bytes);
        } catch (Exception e) {
            System.err.println("❌ Error verificando: " + e.getMessage());
            return false;
        }
    }

    // ══════════════════════════════════════════
    //  Obtener clave pública en Base64 (para mostrar en carné)
    // ══════════════════════════════════════════
    public static String getClavePublicaBase64() {
        if (clavePublica == null) inicializar();
        return Base64.getEncoder().encodeToString(clavePublica.getEncoded());
    }

    // ══════════════════════════════════════════
    //  Generar firma completa para una persona
    //  Retorna: firma corta para mostrar en carné
    // ══════════════════════════════════════════
    public static String generarFirmaPersona(int idPersona, String nombre,
                                             String carne, String correo) {
        if (clavePrivada == null) inicializar();
        String datos = idPersona + "|" + nombre + "|" + carne + "|" + correo;
        String firmaCompleta = firmar(datos);
        if (firmaCompleta == null) return "ERROR";
        // Retornar solo los primeros 20 chars para mostrar en carné
        return firmaCompleta.substring(0, Math.min(20, firmaCompleta.length()));
    }

    // ══════════════════════════════════════════
    //  Generar firma completa (para QR/web)
    // ══════════════════════════════════════════
    public static String generarFirmaCompleta(int idPersona, String nombre,
                                              String carne, String correo) {
        if (clavePrivada == null) inicializar();
        String datos = idPersona + "|" + nombre + "|" + carne + "|" + correo;
        return firmar(datos);
    }
}