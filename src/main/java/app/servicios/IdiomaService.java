package app.servicios;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.prefs.Preferences;

/**
 * IdiomaService — Gestiona el idioma activo de la aplicación (ES / EN).
 * Uso: IdiomaService.get("login.titulo")
 *      IdiomaService.setIdioma("en")
 */
public class IdiomaService {

    private static final Preferences PREFS = Preferences.userNodeForPackage(IdiomaService.class);
    private static final String PREF_IDIOMA = "idioma";
    private static final String PREF_TEMA   = "tema";

    private static Properties props = new Properties();
    private static String idiomaActual = "es";

    // Observable para que los controladores puedan reaccionar al cambio de idioma
    public static final ObjectProperty<String> idiomaProperty =
            new SimpleObjectProperty<>("es");

    // Observable para modo oscuro/claro
    public static final ObjectProperty<Boolean> temaOscuroProperty =
            new SimpleObjectProperty<>(true);

    static {
        // Cargar preferencias guardadas
        idiomaActual = PREFS.get(PREF_IDIOMA, "es");
        boolean oscuro = PREFS.getBoolean(PREF_TEMA, true);
        temaOscuroProperty.set(oscuro);
        cargarProps(idiomaActual);
    }

    /** Obtiene la traducción para la clave dada */
    public static String get(String clave) {
        return props.getProperty(clave, "[" + clave + "]");
    }

    /** Obtiene la traducción con parámetros (reemplaza {0}, {1}, ...) */
    public static String get(String clave, Object... params) {
        String val = get(clave);
        for (int i = 0; i < params.length; i++) {
            val = val.replace("{" + i + "}", String.valueOf(params[i]));
        }
        return val;
    }

    /** Cambia el idioma y notifica a los observadores */
    public static void setIdioma(String lang) {
        idiomaActual = lang;
        PREFS.put(PREF_IDIOMA, lang);
        cargarProps(lang);
        idiomaProperty.set(lang);
    }

    public static String getIdioma() { return idiomaActual; }

    /** Alterna entre modo oscuro y claro */
    public static void toggleTema() {
        boolean oscuro = !temaOscuroProperty.get();
        temaOscuroProperty.set(oscuro);
        PREFS.putBoolean(PREF_TEMA, oscuro);
    }

    public static boolean isTemaOscuro() { return temaOscuroProperty.get(); }

    private static void cargarProps(String lang) {
        props = new Properties();
        String archivo = "/i18n/messages_" + lang + ".properties";
        try (InputStream is = IdiomaService.class.getResourceAsStream(archivo)) {
            if (is != null) {
                props.load(new InputStreamReader(is, StandardCharsets.UTF_8));
            } else {
                System.err.println("⚠ Archivo de idioma no encontrado: " + archivo);
            }
        } catch (IOException e) {
            System.err.println("⚠ Error cargando idioma: " + e.getMessage());
        }
    }
}
