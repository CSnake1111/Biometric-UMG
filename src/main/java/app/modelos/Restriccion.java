package app.modelos;

import java.time.LocalDate;

/**
 * Modelo - Restricción de acceso a instalaciones
 */
public class Restriccion {

    private int       idRestriccion;
    private int       idPersona;
    private String    nombrePersona;
    private String    motivo;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;

    public Restriccion() {}

    public boolean isActiva() {
        LocalDate hoy = LocalDate.now();
        return fechaInicio != null && fechaFin != null
            && !hoy.isBefore(fechaInicio) && !hoy.isAfter(fechaFin);
    }

    public int       getIdRestriccion()             { return idRestriccion; }
    public void      setIdRestriccion(int id)       { this.idRestriccion = id; }
    public int       getIdPersona()                 { return idPersona; }
    public void      setIdPersona(int id)           { this.idPersona = id; }
    public String    getNombrePersona()             { return nombrePersona; }
    public void      setNombrePersona(String n)     { this.nombrePersona = n; }
    public String    getMotivo()                    { return motivo; }
    public void      setMotivo(String m)            { this.motivo = m; }
    public LocalDate getFechaInicio()               { return fechaInicio; }
    public void      setFechaInicio(LocalDate f)    { this.fechaInicio = f; }
    public LocalDate getFechaFin()                  { return fechaFin; }
    public void      setFechaFin(LocalDate f)       { this.fechaFin = f; }
}
