package app.servicios;

import app.modelos.Curso;
import app.modelos.Usuario;

import javax.mail.*;
import javax.mail.internet.*;
import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

public class EmailService {

    private static final String EMAIL_FROM;
    private static final String EMAIL_PASSWORD;
    private static final String SMTP_HOST;
    private static final String SMTP_PORT;

    static {
        // Leer credenciales desde variables de entorno
        String envUser = System.getenv("SMTP_USER");
        String envPass = System.getenv("SMTP_PASSWORD");

        // Fallback: leer desde db.properties si no hay variables de entorno
        if (envUser == null || envUser.isBlank()) {
            Properties props = new Properties();
            try (InputStream is = EmailService.class.getResourceAsStream("/db.properties")) {
                if (is != null) props.load(is);
            } catch (Exception ignored) {}
            envUser = props.getProperty("smtp.user", "");
            envPass = props.getProperty("smtp.password", "");
        }

        EMAIL_FROM     = envUser;
        EMAIL_PASSWORD = envPass;
        SMTP_HOST      = "smtp.gmail.com";
        SMTP_PORT      = "587";
    }

    private static Session crearSesion() {
        Properties props = new Properties();
        props.put("mail.smtp.auth",            "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host",             SMTP_HOST);
        props.put("mail.smtp.port",             SMTP_PORT);
        props.put("mail.smtp.ssl.trust",        SMTP_HOST);

        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(EMAIL_FROM, EMAIL_PASSWORD);
            }
        });
    }

    public static void enviarAvisoAsistencia(List<Usuario> estudiantes, Curso curso, String fecha) {
        if (estudiantes == null || estudiantes.isEmpty()) return;

        new Thread(() -> {
            int enviados = 0;
            for (Usuario est : estudiantes) {
                if (est.getCorreo() == null || est.getCorreo().isBlank()) continue;
                try {
                    String html = """
                        <html>
                        <body style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;">
                          <div style="background:#003366;padding:20px;text-align:center;">
                            <h1 style="color:white;margin:0;font-size:20px;">BiometricUMG 2.0</h1>
                            <p style="color:#adc8e8;margin:4px 0;font-size:13px;">
                              Universidad Mariano Gálvez · Sede La Florida
                            </p>
                          </div>
                          <div style="padding:28px;background:#f9f9f9;">
                            <h2 style="color:#003366;">📋 Aviso de Asistencia</h2>
                            <p>Estimado/a <strong>%s %s</strong>,</p>
                            <p>El catedrático ha abierto la lista de asistencia para el curso:</p>
                            <div style="background:white;border:1px solid #ddd;border-radius:8px;
                                        padding:16px;margin:18px 0;">
                              <p style="margin:0;font-size:15px;">
                                <strong>📚 Curso:</strong> %s — Sección %s
                              </p>
                              <p style="margin:8px 0 0;font-size:14px;color:#555;">
                                📅 Fecha: <strong>%s</strong>
                              </p>
                            </div>
                            <p>Si estás presente, el sistema biométrico registrará tu asistencia
                               automáticamente.</p>
                            <p style="color:#999;font-size:12px;margin-top:24px;">
                              Este correo fue generado automáticamente ·
                              BiometricUMG 2.0 · UMG La Florida 2026
                            </p>
                          </div>
                          <div style="background:#b40000;padding:10px;text-align:center;">
                            <p style="color:white;margin:0;font-size:11px;">
                              BiometricUMG 2.0 | Sistema de Control de Asistencia | 2026
                            </p>
                          </div>
                        </body>
                        </html>
                        """.formatted(
                            est.getNombre(), est.getApellido(),
                            curso.getNombreCurso(), curso.getSeccion(),
                            fecha
                    );

                    boolean ok = enviarCorreoGenerico(
                            est.getCorreo(),
                            "📋 Aviso de Asistencia — " + curso.getNombreCurso() + " (" + fecha + ")",
                            html
                    );
                    if (ok) enviados++;
                } catch (Exception e) {
                    System.err.println("❌ Aviso no enviado a " + est.getCorreo() + ": " + e.getMessage());
                }
            }
            System.out.println("✅ Avisos de asistencia enviados: " + enviados + "/" + estudiantes.size());
        }, "aviso-asistencia") {{ setDaemon(true); }}.start();
    }

    public static boolean enviarCarnet(String destinatario, String nombrePersona, String rutaPDF) {
        try {
            Session sesion = crearSesion();
            Message mensaje = new MimeMessage(sesion);

            mensaje.setFrom(new InternetAddress(EMAIL_FROM, "BiometricUMG Sistema"));
            mensaje.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinatario));
            mensaje.setSubject("🎓 Tu Carnet Universitario - UMG Sede La Florida");

            String cuerpoHTML = """
                <html>
                <body style="font-family: Arial, sans-serif; color: #333; max-width: 600px; margin: 0 auto;">
                  <div style="background: #003366; padding: 20px; text-align: center;">
                    <h1 style="color: white; margin: 0;">Universidad Mariano Gálvez</h1>
                    <p style="color: #adc8e8; margin: 5px 0;">Sede La Florida, Zona 19</p>
                  </div>
                  <div style="padding: 30px; background: #f9f9f9;">
                    <h2 style="color: #003366;">¡Bienvenido/a, %s!</h2>
                    <p>Tu carnet universitario ha sido generado exitosamente en el
                       <strong>Sistema BiometricUMG 2.0</strong>.</p>
                    <p style="color: #666; font-size: 12px; margin-top: 30px;">
                      Este correo fue generado automáticamente por BiometricUMG 2.0.
                    </p>
                  </div>
                  <div style="background: #b40000; padding: 10px; text-align: center;">
                    <p style="color: white; margin: 0; font-size: 12px;">
                      BiometricUMG 2.0 | Sistema de Control de Ingreso | 2026
                    </p>
                  </div>
                </body>
                </html>
                """.formatted(nombrePersona);

            Multipart multipart = new MimeMultipart();

            BodyPart parteCuerpo = new MimeBodyPart();
            parteCuerpo.setContent(cuerpoHTML, "text/html; charset=UTF-8");
            multipart.addBodyPart(parteCuerpo);

            if (rutaPDF != null && new File(rutaPDF).exists()) {
                MimeBodyPart parteAdjunto = new MimeBodyPart();
                parteAdjunto.attachFile(new File(rutaPDF));
                parteAdjunto.setFileName("Carnet_UMG_" + nombrePersona.replaceAll("\\s", "_") + ".pdf");
                multipart.addBodyPart(parteAdjunto);
            }

            mensaje.setContent(multipart);
            Transport.send(mensaje);
            System.out.println("✅ Carnet enviado a: " + destinatario);
            return true;

        } catch (Exception e) {
            System.err.println("❌ Error enviando carnet: " + e.getMessage());
            return false;
        }
    }

    public static boolean enviarReporteAsistencia(String destinatario, String nombreCatedratico,
                                                  String nombreCurso, String fecha, String rutaPDF) {
        try {
            Session sesion = crearSesion();
            Message mensaje = new MimeMessage(sesion);

            mensaje.setFrom(new InternetAddress(EMAIL_FROM, "BiometricUMG Sistema"));
            mensaje.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinatario));
            mensaje.setSubject("📋 Reporte de Asistencia - " + nombreCurso + " | " + fecha);

            String cuerpoHTML = """
                <html>
                <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                  <div style="background: #003366; padding: 20px; text-align: center;">
                    <h1 style="color: white; font-size: 20px; margin: 0;">REPORTE DE ASISTENCIA</h1>
                    <p style="color: #adc8e8;">Universidad Mariano Gálvez - Sede La Florida</p>
                  </div>
                  <div style="padding: 25px;">
                    <p>Estimado/a <strong>%s</strong>,</p>
                    <p>Se adjunta el reporte oficial de asistencia del curso <strong>%s</strong> del día <strong>%s</strong>.</p>
                    <p style="color:#666; font-size:12px;">Reporte generado automáticamente · BiometricUMG 2.0</p>
                  </div>
                  <div style="background:#b40000; padding:8px; text-align:center;">
                    <p style="color:white; margin:0; font-size:11px;">BiometricUMG 2.0 © 2026</p>
                  </div>
                </body></html>
                """.formatted(nombreCatedratico, nombreCurso, fecha);

            Multipart multipart = new MimeMultipart();
            BodyPart parteCuerpo = new MimeBodyPart();
            parteCuerpo.setContent(cuerpoHTML, "text/html; charset=UTF-8");
            multipart.addBodyPart(parteCuerpo);

            if (rutaPDF != null && new File(rutaPDF).exists()) {
                MimeBodyPart parteAdjunto = new MimeBodyPart();
                parteAdjunto.attachFile(new File(rutaPDF));
                parteAdjunto.setFileName("Asistencia_" + nombreCurso.replaceAll("\\s", "_") + "_" + fecha + ".pdf");
                multipart.addBodyPart(parteAdjunto);
            }

            mensaje.setContent(multipart);
            Transport.send(mensaje);
            System.out.println("✅ Reporte enviado a: " + destinatario);
            return true;

        } catch (Exception e) {
            System.err.println("❌ Error enviando reporte: " + e.getMessage());
            return false;
        }
    }

    public static boolean probarConexion() {
        try {
            Session sesion = crearSesion();
            Transport transport = sesion.getTransport("smtp");
            transport.connect(SMTP_HOST, EMAIL_FROM, EMAIL_PASSWORD);
            transport.close();
            System.out.println("✅ Conexión SMTP exitosa");
            return true;
        } catch (Exception e) {
            System.err.println("❌ Error SMTP: " + e.getMessage());
            return false;
        }
    }

    public static boolean enviarListaAsistencia(String destinatario, Curso curso, String rutaPDF) {
        String fecha = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        return enviarReporteAsistencia(
                destinatario,
                curso.getNombreCatedratico() != null ? curso.getNombreCatedratico() : "Professor",
                curso.getNombreCurso(),
                fecha,
                rutaPDF);
    }

    public static boolean enviarCorreoGenerico(String destinatario, String asunto, String cuerpoHTML) {
        try {
            Session sesion = crearSesion();
            Message mensaje = new MimeMessage(sesion);
            mensaje.setFrom(new InternetAddress(EMAIL_FROM, "BiometricUMG Sistema"));
            mensaje.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinatario));
            mensaje.setSubject(asunto);
            mensaje.setContent(cuerpoHTML, "text/html; charset=UTF-8");
            Transport.send(mensaje);
            System.out.println("✅ Correo enviado a: " + destinatario);
            return true;
        } catch (Exception e) {
            System.err.println("❌ Error enviando correo: " + e.getMessage());
            return false;
        }
    }

    public static boolean enviarSolicitudAutorizacion(
            String correoEncargado,
            String nombreSubencargado,
            String accion,
            String tokenAutorizacion) {
        try {
            String cuerpo = """
                <html><body style="font-family:Arial,sans-serif;color:#333;max-width:600px;margin:0 auto;">
                <div style="background:#003366;padding:20px;text-align:center;">
                  <h1 style="color:white;margin:0;">BiometricUMG 2.0</h1>
                  <p style="color:#adc8e8;">Solicitud de Autorización</p>
                </div>
                <div style="padding:30px;background:#f9f9f9;">
                  <h2 style="color:#b40000;">⚠️ Acción pendiente de autorización</h2>
                  <p>El SubEncargado <strong>%s</strong> desea realizar: <strong>%s</strong></p>
                  <p style="color:#999;font-size:11px;">Token de referencia: %s</p>
                </div>
                <div style="background:#b40000;padding:8px;text-align:center;">
                  <p style="color:white;margin:0;font-size:11px;">BiometricUMG 2.0 | UMG La Florida | 2026</p>
                </div>
                </body></html>
                """.formatted(nombreSubencargado, accion, tokenAutorizacion);

            return enviarCorreoGenerico(
                    correoEncargado,
                    "🔐 Autorización requerida — SubEncargado " + nombreSubencargado,
                    cuerpo
            );
        } catch (Exception e) {
            System.err.println("❌ Error enviando solicitud de autorización: " + e.getMessage());
            return false;
        }
    }
}