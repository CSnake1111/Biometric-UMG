package app.modelos;

import java.time.LocalDateTime;

/**
 * Modelo - Registro de ingreso biométrico
 */
public class RegistroIngreso {

    private int           idRegistro;
    private int           idPersona;
    private String        nombrePersona;
    private String        fotoPersona;
    private String        correoPersona;
    private Integer       idPuerta;
    private String        nombrePuerta;
    private Integer       idSalon;
    private String        nombreSalon;
    private LocalDateTime fechaHora;
    private String        tipoIngreso;   // PUERTA, SALON

    public RegistroIngreso() {}

    public int           getIdRegistro()               { return idRegistro; }
    public void          setIdRegistro(int id)         { this.idRegistro = id; }
    public int           getIdPersona()                { return idPersona; }
    public void          setIdPersona(int id)          { this.idPersona = id; }
    public String        getNombrePersona()            { return nombrePersona; }
    public void          setNombrePersona(String n)    { this.nombrePersona = n; }
    public String        getFotoPersona()              { return fotoPersona; }
    public void          setFotoPersona(String f)      { this.fotoPersona = f; }
    public String        getCorreoPersona()            { return correoPersona; }
    public void          setCorreoPersona(String c)    { this.correoPersona = c; }
    public Integer       getIdPuerta()                 { return idPuerta; }
    public void          setIdPuerta(Integer id)       { this.idPuerta = id; }
    public String        getNombrePuerta()             { return nombrePuerta; }
    public void          setNombrePuerta(String n)     { this.nombrePuerta = n; }
    public Integer       getIdSalon()                  { return idSalon; }
    public void          setIdSalon(Integer id)        { this.idSalon = id; }
    public String        getNombreSalon()              { return nombreSalon; }
    public void          setNombreSalon(String n)      { this.nombreSalon = n; }
    public LocalDateTime getFechaHora()                { return fechaHora; }
    public void          setFechaHora(LocalDateTime f) { this.fechaHora = f; }
    public String        getTipoIngreso()              { return tipoIngreso; }
    public void          setTipoIngreso(String t)      { this.tipoIngreso = t; }
}
