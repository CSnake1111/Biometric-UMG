package app.servicios;

import app.conexion.Conexion;

import java.io.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * BackupService — Respaldo automático de datos.
 * Exporta tablas críticas a CSV + SQL cada N horas.
 * También puede enviar al correo del admin.
 */
public class BackupService {

    private static ScheduledExecutorService scheduler;
    private static final String CARPETA_BACKUP = "data/backup/";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    // ══════════════════════════════════════════════════════════
    //  Iniciar backup automático (cada X horas)
    // ══════════════════════════════════════════════════════════
    public static void iniciarBackupAutomatico(int cadaHoras) {
        if (scheduler != null && !scheduler.isShutdown()) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BackupService");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> {
            try {
                String ruta = ejecutarBackup();
                System.out.println("✅ Backup automático: " + ruta);
                VozService.backupCompletado();
            } catch (Exception e) {
                System.err.println("⚠ Error backup automático: " + e.getMessage());
            }
        }, cadaHoras, cadaHoras, TimeUnit.HOURS);
        System.out.println("🔄 Backup automático cada " + cadaHoras + " horas activado.");
    }

    public static void detener() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    // ══════════════════════════════════════════════════════════
    //  Ejecutar backup manual
    //  Genera: backup_YYYYMMDD_HHmmss.sql
    // ══════════════════════════════════════════════════════════
    public static String ejecutarBackup() {
        new File(CARPETA_BACKUP).mkdirs();
        String timestamp = LocalDateTime.now().format(FMT);
        String rutaSQL = CARPETA_BACKUP + "backup_" + timestamp + ".sql";

        try (PrintWriter writer = new PrintWriter(new FileWriter(rutaSQL))) {
            writer.println("-- ═══════════════════════════════════════════════");
            writer.println("-- BiometricUMG 2.0 — Backup generado: " + timestamp);
            writer.println("-- ═══════════════════════════════════════════════");
            writer.println("USE BiometricUMG;");
            writer.println();

            String[] tablas = {"Roles", "Instalaciones", "Puertas", "Salones",
                               "Usuarios", "Cursos", "Restricciones", "Asistencias",
                               "RegistroIngresos", "Usuarios"};

            try (Connection con = Conexion.nuevaConexion()) {
                for (String tabla : tablas) {
                    exportarTabla(con, tabla, writer);
                }
            }

            writer.println();
            writer.println("-- ✅ Backup completado: " + LocalDateTime.now().format(FMT));
            System.out.println("✅ Backup generado: " + rutaSQL);
            return rutaSQL;

        } catch (Exception e) {
            System.err.println("❌ Error generando backup: " + e.getMessage());
            return null;
        }
    }

    private static void exportarTabla(Connection con, String tabla, PrintWriter w) {
        try {
            DatabaseMetaData meta = con.getMetaData();
            ResultSet cols = meta.getColumns(null, null, tabla, null);
            java.util.List<String> columnas = new java.util.ArrayList<>();
            java.util.List<Integer> tipos   = new java.util.ArrayList<>();
            while (cols.next()) {
                columnas.add(cols.getString("COLUMN_NAME"));
                tipos.add(cols.getInt("DATA_TYPE"));
            }
            if (columnas.isEmpty()) return;

            w.println("-- Tabla: " + tabla);
            try (PreparedStatement ps = con.prepareStatement("SELECT * FROM " + tabla);
                 ResultSet rs = ps.executeQuery()) {
                int filas = 0;
                while (rs.next()) {
                    StringBuilder sb = new StringBuilder("INSERT INTO ")
                        .append(tabla).append(" (")
                        .append(String.join(", ", columnas))
                        .append(") VALUES (");
                    for (int i = 0; i < columnas.size(); i++) {
                        if (i > 0) sb.append(", ");
                        Object val = rs.getObject(i + 1);
                        if (val == null) {
                            sb.append("NULL");
                        } else if (val instanceof byte[]) {
                            sb.append("0x").append(bytesToHex((byte[]) val));
                        } else if (val instanceof String || val instanceof java.sql.Date
                                || val instanceof java.sql.Timestamp) {
                            sb.append("'").append(val.toString().replace("'", "''")).append("'");
                        } else {
                            sb.append(val);
                        }
                    }
                    sb.append(");");
                    w.println(sb);
                    filas++;
                }
                w.println("-- " + filas + " registros exportados de " + tabla);
                w.println();
            }
        } catch (SQLException e) {
            w.println("-- ⚠ Error exportando " + tabla + ": " + e.getMessage());
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02X", b));
        return sb.toString();
    }

    // ══════════════════════════════════════════════════════════
    //  Resumen de estadísticas del backup
    // ══════════════════════════════════════════════════════════
    public static String[] obtenerEstadisticasBackup() {
        // Último backup, cantidad de registros totales
        File carpeta = new File(CARPETA_BACKUP);
        if (!carpeta.exists()) return new String[]{"Sin backups", "0 registros"};

        File[] archivos = carpeta.listFiles((d, n) -> n.endsWith(".sql"));
        if (archivos == null || archivos.length == 0) return new String[]{"Sin backups", "0 archivos"};

        File ultimo = archivos[0];
        for (File f : archivos) if (f.lastModified() > ultimo.lastModified()) ultimo = f;

        return new String[]{
            "Último: " + ultimo.getName().replace("backup_", "").replace(".sql", ""),
            archivos.length + " archivos"
        };
    }
}
