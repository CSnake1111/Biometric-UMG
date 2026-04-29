package app.modelos;

/** Modelo - Puerta de acceso */
public class Puerta {
    private int    idPuerta;
    private String nombre;
    private int    idInstalacion;
    private String nombreInstalacion;

    public Puerta() {}

    public int    getIdPuerta()                    { return idPuerta; }
    public void   setIdPuerta(int id)              { this.idPuerta = id; }
    public String getNombre()                      { return nombre; }
    public void   setNombre(String n)              { this.nombre = n; }
    public int    getIdInstalacion()               { return idInstalacion; }
    public void   setIdInstalacion(int id)         { this.idInstalacion = id; }
    public String getNombreInstalacion()           { return nombreInstalacion; }
    public void   setNombreInstalacion(String n)   { this.nombreInstalacion = n; }

    @Override public String toString()             { return nombre; }
}
