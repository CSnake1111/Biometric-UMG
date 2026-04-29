package app.modelos;

import java.time.LocalDateTime;

/**
 * BiometricUMG 4.0 — Modelo Usuario (unificado)
 */
public class Usuario {

    private int           idUsuario;
    private String        carne;
    private String        nombre;
    private String        apellido;
    private String        telefono;
    private String        correo;
    private String        tipoPersona;
    private String        carrera;
    private String        seccion;
    private String        foto;
    private String        qrCodigo;
    private boolean       estado;
    private LocalDateTime fechaRegistro;
    private int           idRol;
    private String        nombreRol;
    private String        usuario;
    private int           intentosFallidos;
    private LocalDateTime bloqueadoHasta;
    private LocalDateTime ultimoLogin;
    private boolean       activo;
    private String        ipUltimoLogin;

    public Usuario() {
        this.estado        = true;
        this.activo        = true;
        this.fechaRegistro = LocalDateTime.now();
    }

    public String getNombreCompleto() {
        if (nombre == null && apellido == null) return usuario != null ? usuario : "—";
        return (nombre != null ? nombre : "") + " " + (apellido != null ? apellido : "");
    }

    public int getIdPersona()          { return idUsuario; }
    public void setIdPersona(int v)    { this.idUsuario = v; }

    @Override
    public String toString() {
        return "[" + idUsuario + "] " + getNombreCompleto()
                + (usuario != null ? " @" + usuario : "")
                + (nombreRol != null ? " [" + nombreRol + "]" : "");
    }

    public int           getIdUsuario()                    { return idUsuario; }
    public void          setIdUsuario(int v)               { this.idUsuario = v; }
    public String        getCarne()                        { return carne; }
    public void          setCarne(String v)                { this.carne = v; }
    public String        getNombre()                       { return nombre; }
    public void          setNombre(String v)               { this.nombre = v; }
    public String        getApellido()                     { return apellido; }
    public void          setApellido(String v)             { this.apellido = v; }
    public String        getTelefono()                     { return telefono; }
    public void          setTelefono(String v)             { this.telefono = v; }
    public String        getCorreo()                       { return correo; }
    public void          setCorreo(String v)               { this.correo = v; }
    public String        getTipoPersona()                  { return tipoPersona; }
    public void          setTipoPersona(String v)          { this.tipoPersona = v; }
    public String        getCarrera()                      { return carrera; }
    public void          setCarrera(String v)              { this.carrera = v; }
    public String        getSeccion()                      { return seccion; }
    public void          setSeccion(String v)              { this.seccion = v; }
    public String        getFoto()                         { return foto; }
    public void          setFoto(String v)                 { this.foto = v; }
    public String        getQrCodigo()                     { return qrCodigo; }
    public void          setQrCodigo(String v)             { this.qrCodigo = v; }
    public boolean       isEstado()                        { return estado; }
    public void          setEstado(boolean v)              { this.estado = v; }
    public LocalDateTime getFechaRegistro()                { return fechaRegistro; }
    public void          setFechaRegistro(LocalDateTime v) { this.fechaRegistro = v; }
    public int           getIdRol()                        { return idRol; }
    public void          setIdRol(int v)                   { this.idRol = v; }
    public String        getNombreRol()                    { return nombreRol; }
    public void          setNombreRol(String v)            { this.nombreRol = v; }
    public String        getUsuario()                      { return usuario; }
    public void          setUsuario(String v)              { this.usuario = v; }
    public int           getIntentosFallidos()             { return intentosFallidos; }
    public void          setIntentosFallidos(int v)        { this.intentosFallidos = v; }
    public LocalDateTime getBloqueadoHasta()               { return bloqueadoHasta; }
    public void          setBloqueadoHasta(LocalDateTime v){ this.bloqueadoHasta = v; }
    public LocalDateTime getUltimoLogin()                  { return ultimoLogin; }
    public void          setUltimoLogin(LocalDateTime v)   { this.ultimoLogin = v; }
    public boolean       isActivo()                        { return activo; }
    public void          setActivo(boolean v)              { this.activo = v; }
    public String        getIpUltimoLogin()                { return ipUltimoLogin; }
    public void          setIpUltimoLogin(String v)        { this.ipUltimoLogin = v; }
}
