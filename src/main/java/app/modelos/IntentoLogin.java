package app.modelos;

import java.time.LocalDateTime;

public class IntentoLogin {
    private long idIntento;
    private String usuario;
    private String ip;
    private boolean exitoso;
    private String metodo;
    private String motivoFallo;
    private LocalDateTime fechaHora;

    public IntentoLogin() {}

    public long getIdIntento()                      { return idIntento; }
    public void setIdIntento(long idIntento)        { this.idIntento = idIntento; }
    public String getUsuario()                      { return usuario; }
    public void setUsuario(String usuario)          { this.usuario = usuario; }
    public String getIp()                           { return ip; }
    public void setIp(String ip)                    { this.ip = ip; }
    public boolean isExitoso()                      { return exitoso; }
    public void setExitoso(boolean exitoso)         { this.exitoso = exitoso; }
    public String getMetodo()                       { return metodo; }
    public void setMetodo(String metodo)            { this.metodo = metodo; }
    public String getMotivoFallo()                  { return motivoFallo; }
    public void setMotivoFallo(String motivoFallo)  { this.motivoFallo = motivoFallo; }
    public LocalDateTime getFechaHora()             { return fechaHora; }
    public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }

    public String getFechaHoraStr() {
        if (fechaHora == null) return "";
        return fechaHora.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
    }
}
