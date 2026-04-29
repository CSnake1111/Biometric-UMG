package app.modelos;

/** Modelo - Rol del sistema */
public class Rol {
    private int    idRol;
    private String nombreRol;

    public Rol() {}
    public Rol(int idRol, String nombreRol) {
        this.idRol     = idRol;
        this.nombreRol = nombreRol;
    }

    public int    getIdRol()               { return idRol; }
    public void   setIdRol(int idRol)      { this.idRol = idRol; }
    public String getNombreRol()           { return nombreRol; }
    public void   setNombreRol(String n)   { this.nombreRol = n; }

    @Override
    public String toString()               { return nombreRol; }
}
