package app.modelos;

/** Modelo - Instalación física (edificio) */
public class Instalacion {
    private int    idInstalacion;
    private String nombre;

    public Instalacion() {}
    public Instalacion(int id, String nombre) { this.idInstalacion = id; this.nombre = nombre; }

    public int    getIdInstalacion()             { return idInstalacion; }
    public void   setIdInstalacion(int id)       { this.idInstalacion = id; }
    public String getNombre()                    { return nombre; }
    public void   setNombre(String n)            { this.nombre = n; }

    @Override public String toString()           { return nombre; }
}
