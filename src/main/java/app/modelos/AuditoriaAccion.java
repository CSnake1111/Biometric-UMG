package app.modelos;

import java.time.LocalDateTime;

public class AuditoriaAccion {
    private long idAuditoria;
    private Integer idUsuario;
    private String nombreUsuario;
    private String rol;
    private String accion;
    private String modulo;
    private String descripcion;
    private String valorAnterior;
    private String valorNuevo;
    private String ip;
    private String resultado;
    private LocalDateTime fechaHora;

    public AuditoriaAccion() {}

    public long getIdAuditoria()                        { return idAuditoria; }
    public void setIdAuditoria(long idAuditoria)        { this.idAuditoria = idAuditoria; }
    public Integer getIdUsuario()                       { return idUsuario; }
    public void setIdUsuario(Integer idUsuario)         { this.idUsuario = idUsuario; }
    public String getNombreUsuario()                    { return nombreUsuario; }
    public void setNombreUsuario(String nombreUsuario)  { this.nombreUsuario = nombreUsuario; }
    public String getRol()                              { return rol; }
    public void setRol(String rol)                      { this.rol = rol; }
    public String getAccion()                           { return accion; }
    public void setAccion(String accion)                { this.accion = accion; }
    public String getModulo()                           { return modulo; }
    public void setModulo(String modulo)                { this.modulo = modulo; }
    public String getDescripcion()                      { return descripcion; }
    public void setDescripcion(String descripcion)      { this.descripcion = descripcion; }
    public String getValorAnterior()                    { return valorAnterior; }
    public void setValorAnterior(String valorAnterior)  { this.valorAnterior = valorAnterior; }
    public String getValorNuevo()                       { return valorNuevo; }
    public void setValorNuevo(String valorNuevo)        { this.valorNuevo = valorNuevo; }
    public String getIp()                               { return ip; }
    public void setIp(String ip)                        { this.ip = ip; }
    public String getResultado()                        { return resultado; }
    public void setResultado(String resultado)          { this.resultado = resultado; }
    public LocalDateTime getFechaHora()                 { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora)   { this.fechaHora = fechaHora; }

    public String getFechaHoraStr() {
        if (fechaHora == null) return "";
        return fechaHora.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
    }
}
