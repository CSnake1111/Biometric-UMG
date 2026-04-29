package app.controladores;

/**
 * Contrato para controladores que mantienen recursos abiertos
 * (cámara, scheduler, etc.) y deben liberarlos antes de navegar.
 * Main.navegarA() detecta esta interfaz automáticamente.
 */
public interface Liberable {
    void liberarRecursos();
}