package app.modelos;

import java.time.LocalDateTime;

/**
 * Modelo - Persona
 * Representa estudiantes, catedráticos, mantenimiento, seguridad, etc.
 */
public class Persona {

    private int    idPersona;
    private String carne;
    private String nombre;
    private String apellido;
    private String telefono;
    private String correo;
    private String tipoPersona;
    private String carrera;
    private String seccion;
    private String foto;
    private String qrCodigo;
    private boolean estado;
    private LocalDateTime fechaRegistro;
    private int    idRol;
    private String nombreRol;

    // ══════════════ Constructores ══════════════

    public Persona() {
        this.estado        = true;
        this.fechaRegistro = LocalDateTime.now();
    }

    public Persona(String nombre, String apellido, String telefono,
                   String correo, String tipoPersona, String carrera,
                   String seccion, String foto, int idRol) {
        this();
        this.nombre      = nombre;
        this.apellido    = apellido;
        this.telefono    = telefono;
        this.correo      = correo;
        this.tipoPersona = tipoPersona;
        this.carrera     = carrera;
        this.seccion     = seccion;
        this.foto        = foto;
        this.idRol       = idRol;
    }

    // ══════════════ Métodos utilitarios ══════════════

    public String getNombreCompleto() {
        return nombre + " " + apellido;
    }

    @Override
    public String toString() {
        return "[" + idPersona + "] " + getNombreCompleto() + " - " + tipoPersona;
    }

    // ══════════════ Getters & Setters ══════════════

    public int getIdPersona()                        { return idPersona; }
    public void setIdPersona(int idPersona)          { this.idPersona = idPersona; }

    public String getCarne()                         { return carne; }
    public void setCarne(String carne)               { this.carne = carne; }

    public String getNombre()                        { return nombre; }
    public void setNombre(String nombre)             { this.nombre = nombre; }

    public String getApellido()                      { return apellido; }
    public void setApellido(String apellido)         { this.apellido = apellido; }

    public String getTelefono()                      { return telefono; }
    public void setTelefono(String telefono)         { this.telefono = telefono; }

    public String getCorreo()                        { return correo; }
    public void setCorreo(String correo)             { this.correo = correo; }

    public String getTipoPersona()                   { return tipoPersona; }
    public void setTipoPersona(String tipoPersona)   { this.tipoPersona = tipoPersona; }

    public String getCarrera()                       { return carrera; }
    public void setCarrera(String carrera)           { this.carrera = carrera; }

    public String getSeccion()                       { return seccion; }
    public void setSeccion(String seccion)           { this.seccion = seccion; }

    public String getFoto()                          { return foto; }
    public void setFoto(String foto)                 { this.foto = foto; }

    public String getQrCodigo()                      { return qrCodigo; }
    public void setQrCodigo(String qrCodigo)         { this.qrCodigo = qrCodigo; }

    public boolean isEstado()                        { return estado; }
    public void setEstado(boolean estado)            { this.estado = estado; }

    public LocalDateTime getFechaRegistro()          { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime f)    { this.fechaRegistro = f; }

    public int getIdRol()                            { return idRol; }
    public void setIdRol(int idRol)                  { this.idRol = idRol; }

    public String getNombreRol()                     { return nombreRol; }
    public void setNombreRol(String nombreRol)       { this.nombreRol = nombreRol; }
}