package app.servicios;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * VozService — síntesis de voz para alertas del sistema.
 * Usa javax.speech (FreeTTS) si disponible, o fallback a beep del sistema.
 * Frases: "Acceso permitido", "Acceso denegado", etc.
 */
public class VozService {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "VozService");
        t.setDaemon(true);
        return t;
    });

    private static boolean vozHabilitada = true;

    // ══════════════════════════════════════════════════════════
    //  Hablar mensaje (en hilo separado, no bloquea UI)
    // ══════════════════════════════════════════════════════════
    public static void hablar(String mensaje) {
        if (!vozHabilitada) return;
        executor.submit(() -> {
            try {
                // Intentar con el motor de voz del sistema operativo
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    // Windows: PowerShell speech
                    String script = String.format(
                        "Add-Type -AssemblyName System.Speech; " +
                        "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                        "$s.Rate = 1; $s.Volume = 100; $s.Speak('%s');",
                        mensaje.replace("'", "").replace("\"", "")
                    );
                    ProcessBuilder pb = new ProcessBuilder(
                        "powershell", "-Command", script
                    );
                    pb.redirectErrorStream(true);
                    Process p = pb.start();
                    p.waitFor();
                } else if (os.contains("mac")) {
                    // macOS: say command
                    new ProcessBuilder("say", "-v", "Paulina", mensaje).start().waitFor();
                } else {
                    // Linux: espeak o festival
                    try {
                        new ProcessBuilder("espeak", "-s", "130", "-v", "es", mensaje).start().waitFor();
                    } catch (Exception e2) {
                        new ProcessBuilder("festival", "--tts").start();
                    }
                }
            } catch (Exception e) {
                // Fallback: beep del sistema
                System.out.println("🔊 [VOZ] " + mensaje);
                java.awt.Toolkit.getDefaultToolkit().beep();
            }
        });
    }

    // ══════════════════════════════════════════════════════════
    //  Mensajes predefinidos
    // ══════════════════════════════════════════════════════════
    public static void accesoPermitido()   { hablar("Acceso permitido. Bienvenido."); }
    public static void accesoDenegado()    { hablar("Acceso denegado."); }
    public static void accesoRestringido() { hablar("Acceso restringido. Persona bloqueada."); }
    public static void intruso()           { hablar("Atención. Persona no reconocida."); }
    public static void asistenciaConfirmada() { hablar("Asistencia confirmada correctamente."); }
    public static void backupCompletado()  { hablar("Respaldo de datos completado."); }

    public static void setVozHabilitada(boolean habilitada) {
        vozHabilitada = habilitada;
    }
    public static boolean isVozHabilitada() { return vozHabilitada; }
}
