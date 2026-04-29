package app.modelos;

import java.time.LocalDate;

public class Asistencia {
    private int       idAsistencia;
    private int       idCurso;
    private int       idEstudiante;
    private LocalDate fecha;
    private String    estado;       // PRESENTE, AUSENTE, TARDANZA, PENDIENTE
    private String    horaIngreso;
    private Usuario   usuario;      // joined — antes era Persona

    public Asistencia() {}

    public int      getIdAsistencia()              { return idAsistencia; }
    public void     setIdAsistencia(int v)         { this.idAsistencia = v; }
    public int      getIdCurso()                   { return idCurso; }
    public void     setIdCurso(int v)              { this.idCurso = v; }
    public int      getIdEstudiante()              { return idEstudiante; }
    public void     setIdEstudiante(int v)         { this.idEstudiante = v; }
    public LocalDate getFecha()                    { return fecha; }
    public void     setFecha(LocalDate v)          { this.fecha = v; }
    public String   getEstado()                    { return estado; }
    public void     setEstado(String v)            { this.estado = v; }
    public String   getHoraIngreso()               { return horaIngreso; }
    public void     setHoraIngreso(String v)       { this.horaIngreso = v; }

    public Usuario  getUsuario()                   { return usuario; }
    public void     setUsuario(Usuario v)          { this.usuario = v; }

    /** Compatibilidad con código antiguo que llamaba getPersona() */
    public Usuario  getPersona()                   { return usuario; }
    public void     setPersona(Usuario v)          { this.usuario = v; }

    public boolean isPresente()  { return "PRESENTE".equals(estado); }
    public boolean isAusente()   { return "AUSENTE".equals(estado); }
    public boolean isTardanza()  { return "TARDANZA".equals(estado); }
}
