package app.modelos;

import java.time.LocalDateTime;

public class AlertaSistema {
    private int idAlerta;
    private String tipo;
    private String descripcion;
    private Integer idUsuario;
    private String ip;
    private boolean resuelta;
    private LocalDateTime fechaHora;

    public AlertaSistema() {}

    public int getIdAlerta()                        { return idAlerta; }
    public void setIdAlerta(int idAlerta)           { this.idAlerta = idAlerta; }
    public String getTipo()                         { return tipo; }
    public void setTipo(String tipo)                { this.tipo = tipo; }
    public String getDescripcion()                  { return descripcion; }
    public void setDescripcion(String descripcion)  { this.descripcion = descripcion; }
    public Integer getIdUsuario()                   { return idUsuario; }
    public void setIdUsuario(Integer idUsuario)     { this.idUsuario = idUsuario; }
    public String getIp()                           { return ip; }
    public void setIp(String ip)                    { this.ip = ip; }
    public boolean isResuelta()                     { return resuelta; }
    public void setResuelta(boolean resuelta)       { this.resuelta = resuelta; }
    public LocalDateTime getFechaHora()             { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }

    public String getFechaHoraStr() {
        if (fechaHora == null) return "";
        return fechaHora.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
    }
}
