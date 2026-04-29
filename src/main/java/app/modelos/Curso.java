package app.modelos;

public class Curso {
    private int    idCurso;
    private String nombreCurso;
    private String seccion;
    private String diaSemana;
    private String horaInicio;
    private String horaFin;
    private int    idCatedratico;
    private String nombreCatedratico;
    private int    idSalon;
    private String nombreSalon;
    private boolean activo = true;

    public Curso() {}

    public int     getIdCurso()                         { return idCurso; }
    public void    setIdCurso(int v)                    { this.idCurso = v; }
    public String  getNombreCurso()                     { return nombreCurso; }
    public void    setNombreCurso(String v)             { this.nombreCurso = v; }
    public String  getSeccion()                         { return seccion; }
    public void    setSeccion(String v)                 { this.seccion = v; }
    public String  getDiaSemana()                       { return diaSemana; }
    public void    setDiaSemana(String v)               { this.diaSemana = v; }
    public String  getHoraInicio()                      { return horaInicio; }
    public void    setHoraInicio(String v)              { this.horaInicio = v; }
    public String  getHoraFin()                         { return horaFin; }
    public void    setHoraFin(String v)                 { this.horaFin = v; }
    public int     getIdCatedratico()                   { return idCatedratico; }
    public void    setIdCatedratico(int v)              { this.idCatedratico = v; }
    public String  getNombreCatedratico()               { return nombreCatedratico; }
    public void    setNombreCatedratico(String v)       { this.nombreCatedratico = v; }
    public int     getIdSalon()                         { return idSalon; }
    public void    setIdSalon(int v)                    { this.idSalon = v; }
    public String  getNombreSalon()                     { return nombreSalon; }
    public void    setNombreSalon(String v)             { this.nombreSalon = v; }
    public boolean isActivo()                           { return activo; }
    public void    setActivo(boolean v)                 { this.activo = v; }

    public String getHorario() {
        if (diaSemana == null) return "";
        return diaSemana + " " + (horaInicio != null ? horaInicio : "")
             + (horaFin != null ? " - " + horaFin : "");
    }

    @Override public String toString() {
        return nombreCurso + " [" + seccion + "]"
             + (diaSemana != null ? " — " + diaSemana : "");
    }
}
