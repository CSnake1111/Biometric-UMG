package app.conexion;

import java.io.InputStream;
import java.sql.*;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * BiometricUMG 4.0 — Connection Pool (PostgreSQL / Supabase)
 */
public class Conexion {

    private static final Properties CFG     = new Properties();
    private static       String      URL;
    private static       String      USER;
    private static       String      PASS;
    private static final int         POOL_MAX = 10;

    private static final BlockingQueue<Connection> pool =
            new ArrayBlockingQueue<>(POOL_MAX);

    static {
        try (InputStream is = Conexion.class
                .getResourceAsStream("/db.properties")) {
            if (is != null) {
                CFG.load(is);
            } else {
                CFG.setProperty("db.url",      "jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:6543/postgres");
                CFG.setProperty("db.user",     "postgres.eqtnobbszmtngdmrucjg");
                CFG.setProperty("db.password", "CHANGE_ME");
            }
            if (CFG.containsKey("db.url")) {
                URL = CFG.getProperty("db.url");
            } else if (CFG.containsKey("db.host")) {
                URL = "jdbc:postgresql://"
                    + CFG.getProperty("db.host", "localhost") + ":"
                    + CFG.getProperty("db.port", "5432") + "/"
                    + CFG.getProperty("db.name", "postgres")
                    + "?sslmode=" + CFG.getProperty("db.ssl", "require");
            } else {
                URL = "jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:6543/postgres?sslmode=require";
            }
            USER = CFG.getProperty("db.user");
            PASS = CFG.getProperty("db.password");
            Class.forName("org.postgresql.Driver");

            int minPool = Integer.parseInt(CFG.getProperty("db.pool.min","2"));
            for (int i = 0; i < minPool; i++) {
                try { pool.offer(DriverManager.getConnection(URL, USER, PASS)); }
                catch (Exception ignored) {}
            }
        } catch (Exception e) {
            System.err.println("❌ Conexion init error: " + e.getMessage());
        }
    }

    /** Borrow from pool or create new. */
    public static Connection nuevaConexion() {
        Connection con = pool.poll();
        if (con != null) {
            try {
                if (!con.isClosed() && con.isValid(2)) return con;
            } catch (SQLException ignored) {}
        }
        try { return DriverManager.getConnection(URL, USER, PASS); }
        catch (SQLException e) {
            System.err.println("❌ nuevaConexion: " + e.getMessage());
            return null;
        }
    }

    /** Return connection to pool or close if full. */
    public static void cerrarConexion(Connection con) {
        if (con == null) return;
        try {
            if (!con.isClosed()) {
                if (!pool.offer(con)) con.close();
            }
        } catch (SQLException e) {
            System.err.println("⚠ cerrarConexion: " + e.getMessage());
        }
    }

    /** Legacy singleton — kept for compatibility. */
    public static Connection getConexion() { return nuevaConexion(); }

    public static boolean probarConexion() {
        try (Connection con = nuevaConexion()) {
            return con != null && con.isValid(3);
        } catch (Exception e) { return false; }
    }

    public static void cerrarConexionPool() {
        for (Connection con : pool) {
            try { if (!con.isClosed()) con.close(); } catch (Exception ignored) {}
        }
        pool.clear();
    }

    public static String getDbName() {
        return CFG.getProperty("db.name", "BiometricUMG");
    }
}
