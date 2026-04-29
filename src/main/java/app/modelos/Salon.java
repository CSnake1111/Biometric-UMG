package app.modelos;

/** Modelo - Salón de clases */
public class Salon {
    private int    idSalon;
    private String nombre;
    private String nivel;
    private int    idInstalacion;
    private String nombreInstalacion;

    public Salon() {}

    public int    getIdSalon()                     { return idSalon; }
    public void   setIdSalon(int id)               { this.idSalon = id; }
    public String getNombre()                      { return nombre; }
    public void   setNombre(String n)              { this.nombre = n; }
    public String getNivel()                       { return nivel; }
    public void   setNivel(String n)               { this.nivel = n; }
    public int    getIdInstalacion()               { return idInstalacion; }
    public void   setIdInstalacion(int id)         { this.idInstalacion = id; }
    public String getNombreInstalacion()           { return nombreInstalacion; }
    public void   setNombreInstalacion(String n)   { this.nombreInstalacion = n; }

    @Override public String toString()             { return nombre + " - " + nivel; }
}
