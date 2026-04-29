package app.servicios;

import app.modelos.Persona;
import app.modelos.Usuario;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PDFService {

    private static final DeviceRgb AZUL_UMG    = new DeviceRgb(0,   51,  102);
    private static final DeviceRgb AZUL_MEDIO  = new DeviceRgb(26,  79,  140);
    private static final DeviceRgb AZUL_CLARO  = new DeviceRgb(173, 216, 230);
    private static final DeviceRgb ROJO_UMG    = new DeviceRgb(180, 0,   0);
    private static final DeviceRgb GRIS_CLARO  = new DeviceRgb(245, 245, 245);
    private static final DeviceRgb GRIS_BORDE  = new DeviceRgb(200, 200, 200);
    private static final DeviceRgb VERDE       = new DeviceRgb(39,  174, 96);
    private static final DeviceRgb ROJO_AUS    = new DeviceRgb(231, 76,  60);
    private static final DeviceRgb BLANCO_SEMI = new DeviceRgb(235, 242, 255);

    private static final String CARPETA_PDF = "data/pdf/";

    // ══════════════════════════════════════════
    //  CARNET UNIVERSITARIO
    // ══════════════════════════════════════════
    public static String generarCarnet(Persona persona) {
        new File(CARPETA_PDF).mkdirs();
        String rutaPDF = CARPETA_PDF + "carnet_" + persona.getIdPersona() + ".pdf";

        try {
            PdfWriter   writer = new PdfWriter(rutaPDF);
            PdfDocument pdf    = new PdfDocument(writer);
            // Embed digital signature info in PDF metadata
            FirmaService.inicializar();
            String firmaCompleta = FirmaService.generarFirmaCompleta(
                persona.getIdPersona(), persona.getNombreCompleto(),
                persona.getCarne() != null ? persona.getCarne() : "", persona.getCorreo());
            if (firmaCompleta != null) {
                pdf.getDocumentInfo().setTitle("Carnet UMG - " + persona.getNombreCompleto());
                pdf.getDocumentInfo().setSubject("Universidad Mariano Galvez - Carnet Universitario");
                pdf.getDocumentInfo().setKeywords("RSA-SHA256:" + firmaCompleta);
                pdf.getDocumentInfo().setCreator("BiometricUMG 4.0");
                pdf.getDocumentInfo().setAuthor("Universidad Mariano Galvez de Guatemala");
            }
            PageSize tamano = new PageSize(242.65f, 153.07f);
            Document doc = new Document(pdf, tamano);
            doc.setMargins(0, 0, 0, 0);

            // ══ TABLA PRINCIPAL ══
            Table principal = new Table(UnitValue.createPercentArray(new float[]{38, 62}))
                    .setWidth(UnitValue.createPercentValue(100))
                    .setHeight(138f);

            // ─── COLUMNA IZQUIERDA ───
            Cell izq = new Cell()
                    .setBorder(Border.NO_BORDER)
                    .setBackgroundColor(AZUL_UMG)
                    .setPadding(6)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE);

            try {
                InputStream logoStream = PDFService.class
                        .getResourceAsStream("/images/logo_umg.png");
                if (logoStream != null) {
                    ImageData logoData = ImageDataFactory.create(logoStream.readAllBytes());
                    Image logo = new Image(logoData)
                            .setWidth(55).setHeight(55)
                            .setHorizontalAlignment(HorizontalAlignment.CENTER);
                    izq.add(logo);
                }
            } catch (Exception ignored) {}

            izq.add(new Paragraph("Universidad")
                    .setFontColor(ColorConstants.WHITE)
                    .setFontSize(5.5f).setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(3).setMarginBottom(0));
            izq.add(new Paragraph("Mariano Gálvez")
                    .setFontColor(ColorConstants.WHITE)
                    .setFontSize(6.5f).setBold().setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(0));
            izq.add(new Paragraph("de Guatemala")
                    .setFontColor(AZUL_CLARO)
                    .setFontSize(5f).setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(4));

            Table lineaDiv = new Table(1).setWidth(UnitValue.createPercentValue(85));
            lineaDiv.addCell(new Cell().setHeight(0.8f)
                    .setBackgroundColor(new DeviceRgb(100, 140, 200))
                    .setBorder(Border.NO_BORDER));
            izq.add(lineaDiv);

            izq.add(new Paragraph(
                    persona.getTipoPersona() != null
                            ? persona.getTipoPersona().toUpperCase() : "ESTUDIANTE")
                    .setFontColor(AZUL_CLARO).setFontSize(7f).setBold()
                    .setTextAlignment(TextAlignment.CENTER).setMarginTop(4));
            izq.add(new Paragraph("Sede La Florida · Zona 19")
                    .setFontColor(new DeviceRgb(180, 200, 220))
                    .setFontSize(4.5f).setTextAlignment(TextAlignment.CENTER));
            izq.add(new Paragraph("2026 — 2027")
                    .setFontColor(new DeviceRgb(140, 170, 200))
                    .setFontSize(4.5f).setTextAlignment(TextAlignment.CENTER));

            principal.addCell(izq);

            // ─── COLUMNA DERECHA ───
            Cell der = new Cell()
                    .setBorder(Border.NO_BORDER)
                    .setBackgroundColor(ColorConstants.WHITE)
                    .setPadding(0);

            // Sub-tabla: datos izquierda, foto derecha
            Table datosYFoto = new Table(UnitValue.createPercentArray(new float[]{52, 48}))
                    .setWidth(UnitValue.createPercentValue(100))
                    .setHeight(138f);

            // Datos + QR
            Cell datos = new Cell().setBorder(Border.NO_BORDER)
                    .setPadding(7).setPaddingRight(2)
                    .setVerticalAlignment(VerticalAlignment.TOP);

            // Nombre — reducir font si es muy largo
            String nombreCompleto = persona.getNombreCompleto();
            float fontNombre = nombreCompleto.length() > 20 ? 7.5f : 9f;
            datos.add(new Paragraph(nombreCompleto)
                    .setFontColor(AZUL_UMG).setFontSize(fontNombre).setBold()
                    .setMarginBottom(2).setMarginTop(4));

            if (persona.getCarne() != null && !persona.getCarne().isEmpty()) {
                // Carné en una línea, font pequeño
                String carneTexto = persona.getCarne();
                float fontCarne = carneTexto.length() > 15 ? 5.5f : 6.5f;
                datos.add(new Paragraph("Carné: " + carneTexto)
                        .setFontColor(new DeviceRgb(60, 60, 60)).setFontSize(fontCarne)
                        .setMarginBottom(1));
            }

            if (persona.getCarrera() != null && !persona.getCarrera().isEmpty()) {
                String carrera = persona.getCarrera();
                float fontCarrera = carrera.length() > 20 ? 5f : 6f;
                datos.add(new Paragraph(carrera)
                        .setFontColor(AZUL_MEDIO).setFontSize(fontCarrera).setItalic()
                        .setMarginBottom(1));
            }

            if (persona.getCorreo() != null) {
                String correo = persona.getCorreo();
                float fontCorreo = correo.length() > 25 ? 4.5f : 5.5f;
                datos.add(new Paragraph(correo)
                        .setFontColor(new DeviceRgb(120, 120, 120)).setFontSize(fontCorreo)
                        .setMarginBottom(4));
            }

            // QR — siempre intentar mostrarlo
            boolean qrAgregado = false;
            try {
                String rutaQR = "data/qr/qr_" + persona.getIdPersona() + ".png";
                if (!new File(rutaQR).exists()) {
                    // Intentar ruta alternativa
                    rutaQR = QRService.getRutaQR(persona.getIdPersona());
                }
                if (rutaQR != null && new File(rutaQR).exists()) {
                    ImageData qrData = ImageDataFactory.create(rutaQR);
                    Image qr = new Image(qrData)
                            .setWidth(42).setHeight(42)
                            .setHorizontalAlignment(HorizontalAlignment.LEFT)
                            .setBorder(new SolidBorder(GRIS_BORDE, 0.5f));
                    datos.add(qr);
                    datos.add(new Paragraph("Escanea para verificar")
                            .setFontColor(new DeviceRgb(150, 150, 150)).setFontSize(4f));
                    qrAgregado = true;
                }
            } catch (Exception e) {
                System.err.println("⚠ QR no disponible: " + e.getMessage());
            }

            if (!qrAgregado) {
                datos.add(new Paragraph("[ QR no disponible ]")
                        .setFontColor(GRIS_BORDE).setFontSize(5f));
            }

            datosYFoto.addCell(datos);

            // Foto
            Cell celdaFoto = new Cell().setBorder(Border.NO_BORDER)
                    .setPadding(6).setPaddingLeft(2)
                    .setVerticalAlignment(VerticalAlignment.TOP);

            try {
                String rutaFoto = persona.getFoto();
                ImageData fotoData = null;
                if (rutaFoto != null && !rutaFoto.isBlank()) {
                    if (rutaFoto.startsWith("http://") || rutaFoto.startsWith("https://")) {
                        // FIX #7: URL Supabase — iText puede cargar desde URL directamente
                        fotoData = ImageDataFactory.create(new java.net.URL(rutaFoto));
                    } else if (new File(rutaFoto).exists()) {
                        fotoData = ImageDataFactory.create(rutaFoto);
                    }
                }
                if (fotoData != null) {
                    Image foto = new Image(fotoData)
                            .setWidth(62).setHeight(76)
                            .setHorizontalAlignment(HorizontalAlignment.CENTER)
                            .setBorder(new SolidBorder(AZUL_UMG, 2.5f));
                    celdaFoto.add(foto);
                } else {
                    celdaFoto.add(new Paragraph("[ Sin foto ]")
                            .setFontColor(GRIS_BORDE).setFontSize(6f));
                }
            } catch (Exception ignored) {}

            datosYFoto.addCell(celdaFoto);
            der.add(datosYFoto);
            principal.addCell(der);
            doc.add(principal);

            // ─── BANDA INFERIOR ROJA ───
            Table banda = new Table(UnitValue.createPercentArray(new float[]{60, 40}))
                    .setWidth(UnitValue.createPercentValue(100));

            Cell cBandaIzq = new Cell()
                    .setBorder(Border.NO_BORDER)
                    .setBackgroundColor(ROJO_UMG)
                    .setPaddingTop(3).setPaddingBottom(3).setPaddingLeft(8);
            cBandaIzq.add(new Paragraph(
                    "Firma Digital: " + generarFirmaElectronica(persona))
                    .setFontColor(ColorConstants.WHITE).setFontSize(4.5f));
            banda.addCell(cBandaIzq);

            Cell cBandaDer = new Cell()
                    .setBorder(Border.NO_BORDER)
                    .setBackgroundColor(new DeviceRgb(140, 0, 0))
                    .setPaddingTop(3).setPaddingBottom(3).setPaddingRight(8);
            cBandaDer.add(new Paragraph("Válido: " + LocalDate.now().getYear())
                    .setFontColor(ColorConstants.WHITE).setFontSize(4.5f)
                    .setTextAlignment(TextAlignment.RIGHT));
            banda.addCell(cBandaDer);
            doc.add(banda);

            doc.close();
            System.out.println("✅ Carnet generado: " + rutaPDF);
            return rutaPDF;

        } catch (IOException e) {
            System.err.println("❌ Error generando carnet: " + e.getMessage());
            return null;
        }
    }

    // ══════════════════════════════════════════
    //  REPORTE DE ASISTENCIA
    // ══════════════════════════════════════════
    public static String generarReporteAsistencia(
            String nombreCurso, String nombreCatedratico,
            String seccion, LocalDate fecha, List<String[]> asistencias) {

        new File(CARPETA_PDF).mkdirs();
        String rutaPDF = CARPETA_PDF + "asistencia_"
                + nombreCurso.replaceAll("\\s", "_") + "_" + fecha + ".pdf";

        try {
            PdfDocument pdf = new PdfDocument(new PdfWriter(rutaPDF));
            Document doc = new Document(pdf, PageSize.A4);
            doc.setMargins(40, 40, 40, 40);

            agregarEncabezadoReporte(doc, "LISTA DE ASISTENCIA");

            Table infoCurso = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                    .setWidth(UnitValue.createPercentValue(100))
                    .setBackgroundColor(GRIS_CLARO)
                    .setBorder(new SolidBorder(GRIS_BORDE, 1))
                    .setMarginBottom(15);
            infoCurso.addCell(celdaInfo("Curso:", nombreCurso));
            infoCurso.addCell(celdaInfo("Fecha:",
                    fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
            infoCurso.addCell(celdaInfo("Catedrático:", nombreCatedratico));
            infoCurso.addCell(celdaInfo("Sección:", seccion));
            doc.add(infoCurso);

            Table tabla = new Table(UnitValue.createPercentArray(new float[]{8, 42, 35, 15}))
                    .setWidth(UnitValue.createPercentValue(100));
            for (String enc : new String[]{"#", "NOMBRE COMPLETO", "CORREO", "ESTADO"}) {
                tabla.addHeaderCell(new Cell()
                        .add(new Paragraph(enc).setFontColor(ColorConstants.WHITE)
                                .setFontSize(9f).setBold())
                        .setBackgroundColor(AZUL_UMG).setPadding(8)
                        .setTextAlignment(TextAlignment.CENTER));
            }

            int presentes = 0, ausentes = 0;
            for (int i = 0; i < asistencias.size(); i++) {
                String[] fila = asistencias.get(i);
                boolean esPresente = "PRESENTE".equalsIgnoreCase(fila[2]);
                DeviceRgb fondo = (i % 2 == 0)
                        ? new DeviceRgb(255, 255, 255) : GRIS_CLARO;
                tabla.addCell(celdaTabla(String.valueOf(i + 1), fondo, false));
                tabla.addCell(celdaTabla(fila[0], fondo, false));
                tabla.addCell(celdaTabla(fila[1], fondo, false));
                tabla.addCell(new Cell()
                        .add(new Paragraph(fila[2]).setFontSize(8f).setBold()
                                .setFontColor(ColorConstants.WHITE)
                                .setTextAlignment(TextAlignment.CENTER))
                        .setBackgroundColor(esPresente ? VERDE : ROJO_AUS).setPadding(6));
                if (esPresente) presentes++; else ausentes++;
            }
            doc.add(tabla);

            Table resumen = new Table(UnitValue.createPercentArray(new float[]{33, 33, 34}))
                    .setWidth(UnitValue.createPercentValue(100)).setMarginTop(15);
            resumen.addCell(celdaResumen("TOTAL",
                    String.valueOf(asistencias.size()), AZUL_UMG));
            resumen.addCell(celdaResumen("PRESENTES",
                    String.valueOf(presentes), VERDE));
            resumen.addCell(celdaResumen("AUSENTES",
                    String.valueOf(ausentes), ROJO_AUS));
            doc.add(resumen);

            doc.add(new Paragraph("\n\n"));
            Table firmas = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                    .setWidth(UnitValue.createPercentValue(100)).setMarginTop(30);
            firmas.addCell(celdaFirma(
                    "_________________________________\nFirma del Catedrático\n" + nombreCatedratico));
            firmas.addCell(celdaFirma(
                    "_________________________________\nSello y Firma\nCoordinación Académica"));
            doc.add(firmas);

            doc.add(new Paragraph(
                    "Generado por BiometricUMG 2.0 | " +
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")))
                    .setFontSize(7f).setFontColor(GRIS_BORDE)
                    .setTextAlignment(TextAlignment.CENTER).setMarginTop(20));

            doc.close();
            return rutaPDF;

        } catch (IOException e) {
            System.err.println("❌ Error generando reporte: " + e.getMessage());
            return null;
        }
    }

    // ══════════════════════════════════════════
    //  Encabezado estándar reportes
    // ══════════════════════════════════════════
    private static void agregarEncabezadoReporte(Document doc, String titulo)
            throws IOException {
        Table enc = new Table(UnitValue.createPercentArray(new float[]{20, 60, 20}))
                .setWidth(UnitValue.createPercentValue(100)).setMarginBottom(20);

        Cell cLogo = new Cell().setBorder(Border.NO_BORDER).setPadding(5);
        try {
            InputStream s = PDFService.class.getResourceAsStream("/images/logo_umg.png");
            if (s != null) {
                Image logo = new Image(ImageDataFactory.create(s.readAllBytes()))
                        .setWidth(60).setHeight(60);
                cLogo.add(logo);
            }
        } catch (Exception ignored) {}
        enc.addCell(cLogo);

        Cell cCentro = new Cell().setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.CENTER).setPadding(5);
        cCentro.add(new Paragraph("Universidad Mariano Gálvez de Guatemala")
                .setFontColor(AZUL_UMG).setFontSize(11f).setBold());
        cCentro.add(new Paragraph("Sede La Florida, Zona 19")
                .setFontColor(new DeviceRgb(100, 100, 100)).setFontSize(9f));
        cCentro.add(new Paragraph("Ingeniería en Sistemas")
                .setFontColor(new DeviceRgb(100, 100, 100)).setFontSize(9f).setItalic());
        cCentro.add(new Paragraph(titulo)
                .setFontColor(ROJO_UMG).setFontSize(14f).setBold().setMarginTop(8));
        enc.addCell(cCentro);

        Cell cLogoD = new Cell().setBorder(Border.NO_BORDER).setPadding(5);
        try {
            InputStream s = PDFService.class.getResourceAsStream("/images/logo_umg.png");
            if (s != null) {
                Image logo = new Image(ImageDataFactory.create(s.readAllBytes()))
                        .setWidth(60).setHeight(60)
                        .setHorizontalAlignment(HorizontalAlignment.RIGHT);
                cLogoD.add(logo);
            }
        } catch (Exception ignored) {}
        enc.addCell(cLogoD);
        doc.add(enc);

        Table linea = new Table(1).setWidth(UnitValue.createPercentValue(100));
        linea.addCell(new Cell().setHeight(3f)
                .setBackgroundColor(AZUL_UMG).setBorder(Border.NO_BORDER));
        doc.add(linea);
        doc.add(new Paragraph("\n"));
    }

    // ══════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════
    private static Cell celdaInfo(String etiqueta, String valor) {
        return new Cell()
                .add(new Paragraph()
                        .add(new Text(etiqueta + " ").setBold().setFontSize(9f))
                        .add(new Text(valor != null ? valor : "").setFontSize(9f)))
                .setBorder(Border.NO_BORDER).setPadding(6);
    }

    private static Cell celdaTabla(String texto, DeviceRgb fondo, boolean bold) {
        Paragraph p = new Paragraph(texto != null ? texto : "").setFontSize(8.5f);
        if (bold) p.setBold();
        return new Cell().add(p).setBackgroundColor(fondo).setPadding(7)
                .setBorderLeft(Border.NO_BORDER).setBorderRight(Border.NO_BORDER)
                .setBorderTop(new SolidBorder(GRIS_BORDE, 0.5f))
                .setBorderBottom(Border.NO_BORDER);
    }

    private static Cell celdaResumen(String titulo, String valor, DeviceRgb color) {
        Cell c = new Cell().setBackgroundColor(color)
                .setTextAlignment(TextAlignment.CENTER).setPadding(12)
                .setBorder(Border.NO_BORDER);
        c.add(new Paragraph(valor).setFontColor(ColorConstants.WHITE)
                .setFontSize(24f).setBold());
        c.add(new Paragraph(titulo).setFontColor(ColorConstants.WHITE).setFontSize(8f));
        return c;
    }

    private static Cell celdaFirma(String texto) {
        return new Cell().add(new Paragraph(texto).setFontSize(9f)
                        .setTextAlignment(TextAlignment.CENTER))
                .setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.CENTER)
                .setPadding(10);
    }

    private static String generarFirmaElectronica(Persona p) {
        FirmaService.inicializar();
        String firmaCorta = FirmaService.generarFirmaPersona(
                p.getIdPersona(), p.getNombreCompleto(),
                p.getCarne() != null ? p.getCarne() : "",
                p.getCorreo()
        );
        return "RSA-SHA256: " + (firmaCorta != null ? firmaCorta : "N/A");
    }

    // ══════════════════════════════════════════
    //  REPORTE DE INGRESOS (Puerta o Salón)
    // ══════════════════════════════════════════
    public static String generarPDFIngresos(
            String ubicacion, java.time.LocalDate fecha,
            List<String[]> ingresos, String tipo) {
        // ingresos: {nombre, correo, hora, tipoPersona}
        new File(CARPETA_PDF).mkdirs();
        String rutaPDF = CARPETA_PDF + "ingresos_"
                + ubicacion.replaceAll("[^a-zA-Z0-9]", "_") + "_" + fecha + ".pdf";

        try {
            PdfDocument pdf = new PdfDocument(new PdfWriter(rutaPDF));
            Document doc = new Document(pdf, PageSize.A4);
            doc.setMargins(40, 40, 40, 40);

            agregarEncabezadoReporte(doc, "REPORTE DE INGRESOS — " + tipo.toUpperCase());

            // Info
            Table infoCurso = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                    .setWidth(UnitValue.createPercentValue(100))
                    .setBackgroundColor(GRIS_CLARO)
                    .setBorder(new SolidBorder(GRIS_BORDE, 1))
                    .setMarginBottom(15);
            infoCurso.addCell(celdaInfo("Ubicación:", ubicacion));
            infoCurso.addCell(celdaInfo("Fecha:", fecha.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
            infoCurso.addCell(celdaInfo("Total ingresos:", String.valueOf(ingresos.size())));
            infoCurso.addCell(celdaInfo("Generado:", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))));
            doc.add(infoCurso);

            // Tabla
            Table tabla = new Table(UnitValue.createPercentArray(new float[]{8, 38, 34, 10, 10}))
                    .setWidth(UnitValue.createPercentValue(100));
            for (String enc : new String[]{"#", "NOMBRE", "CORREO", "HORA", "TIPO"}) {
                tabla.addHeaderCell(new Cell()
                        .add(new Paragraph(enc).setFontColor(ColorConstants.WHITE)
                                .setFontSize(9f).setBold())
                        .setBackgroundColor(AZUL_UMG).setPadding(8)
                        .setTextAlignment(TextAlignment.CENTER));
            }

            for (int i = 0; i < ingresos.size(); i++) {
                String[] fila = ingresos.get(i);
                DeviceRgb fondo = (i % 2 == 0) ? new DeviceRgb(255, 255, 255) : GRIS_CLARO;
                tabla.addCell(celdaTabla(String.valueOf(i + 1), fondo, false));
                tabla.addCell(celdaTabla(fila[0], fondo, false));
                tabla.addCell(celdaTabla(fila.length > 1 ? fila[1] : "", fondo, false));
                tabla.addCell(celdaTabla(fila.length > 2 ? fila[2] : "", fondo, false));
                tabla.addCell(celdaTabla(fila.length > 3 ? fila[3] : "", fondo, false));
            }
            doc.add(tabla);

            // Resumen
            Table resumen = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                    .setWidth(UnitValue.createPercentValue(100)).setMarginTop(15);
            resumen.addCell(celdaResumen("TOTAL REGISTROS", String.valueOf(ingresos.size()), AZUL_UMG));
            resumen.addCell(celdaResumen("FECHA", fecha.toString(), AZUL_MEDIO));
            doc.add(resumen);

            doc.add(new Paragraph(
                    "Generado por BiometricUMG 2.0 | " +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")))
                    .setFontSize(7f).setFontColor(GRIS_BORDE)
                    .setTextAlignment(TextAlignment.CENTER).setMarginTop(20));

            doc.close();
            return rutaPDF;
        } catch (java.io.IOException e) {
            System.err.println("❌ Error generando PDF ingresos: " + e.getMessage());
            return null;
        }
    }

    // ══════════════════════════════════════════
    //  REPORTE DE INTRUSOS (caras no reconocidas)
    // ══════════════════════════════════════════
    public static String generarPDFIntrusos(List<String[]> intrusos) {
        // intrusos: {fechaHora, ubicacion, rutaFoto}
        new File(CARPETA_PDF).mkdirs();
        String rutaPDF = CARPETA_PDF + "intrusos_" + java.time.LocalDate.now() + ".pdf";
        try {
            PdfDocument pdf = new PdfDocument(new PdfWriter(rutaPDF));
            Document doc = new Document(pdf, PageSize.A4);
            doc.setMargins(40, 40, 40, 40);
            agregarEncabezadoReporte(doc, "⚠ REPORTE DE INTRUSOS / CARAS NO RECONOCIDAS");

            if (intrusos.isEmpty()) {
                doc.add(new Paragraph("No hay registros de intrusos.")
                        .setFontSize(11f).setFontColor(GRIS_BORDE));
            } else {
                for (int i = 0; i < intrusos.size(); i++) {
                    String[] r = intrusos.get(i);
                    Table row = new Table(UnitValue.createPercentArray(new float[]{25, 45, 30}))
                            .setWidth(UnitValue.createPercentValue(100)).setMarginBottom(8);
                    // Foto
                    Cell cFoto = new Cell().setBorder(Border.NO_BORDER).setPadding(4);
                    try {
                        if (r[2] != null && new File(r[2]).exists()) {
                            Image img = new Image(ImageDataFactory.create(r[2]))
                                    .setWidth(60).setHeight(60);
                            cFoto.add(img);
                        } else {
                            cFoto.add(new Paragraph("[Sin foto]").setFontSize(8f).setFontColor(GRIS_BORDE));
                        }
                    } catch (Exception ignored) {}
                    row.addCell(cFoto);
                    Cell cInfo = new Cell().setBorder(Border.NO_BORDER).setPadding(8);
                    cInfo.add(new Paragraph("Intento #" + (i+1)).setBold().setFontSize(10f).setFontColor(ROJO_UMG));
                    cInfo.add(new Paragraph("Fecha/Hora: " + (r[0] != null ? r[0] : "N/A")).setFontSize(9f));
                    cInfo.add(new Paragraph("Ubicación: " + (r[1] != null ? r[1] : "N/A")).setFontSize(9f));
                    row.addCell(cInfo);
                    Cell cEstado = new Cell().setBackgroundColor(ROJO_UMG).setBorder(Border.NO_BORDER)
                            .setVerticalAlignment(VerticalAlignment.MIDDLE).setPadding(8);
                    cEstado.add(new Paragraph("ACCESO\nDENEGADO").setBold().setFontSize(11f)
                            .setFontColor(ColorConstants.WHITE).setTextAlignment(TextAlignment.CENTER));
                    row.addCell(cEstado);
                    doc.add(row);
                }
            }
            doc.add(new Paragraph(
                    "BiometricUMG 2.0 — Sistema de Seguridad | " +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")))
                    .setFontSize(7f).setFontColor(GRIS_BORDE)
                    .setTextAlignment(TextAlignment.CENTER).setMarginTop(20));
            doc.close();
            return rutaPDF;
        } catch (java.io.IOException e) {
            System.err.println("❌ Error generando PDF intrusos: " + e.getMessage());
            return null;
        }
    }

    public static String getCarpetaPDF() { return CARPETA_PDF; }

    // ══════════════════════════════════════════
    //  SOBRECARGA: acepta Usuario (tabla unificada)
    // ══════════════════════════════════════════
    public static String generarCarnet(Usuario u) {
        // Convertir Usuario → Persona para reutilizar la lógica existente
        Persona p = new Persona();
        p.setIdPersona  (u.getIdUsuario());
        p.setNombre     (u.getNombre());
        p.setApellido   (u.getApellido());
        p.setTelefono   (u.getTelefono());
        p.setCorreo     (u.getCorreo());
        p.setTipoPersona(u.getTipoPersona());
        p.setCarrera    (u.getCarrera());
        p.setSeccion    (u.getSeccion());
        p.setCarne      (u.getCarne());
        p.setFoto       (u.getFoto());
        p.setQrCodigo   (u.getQrCodigo());
        p.setEstado     (u.isEstado());
        p.setFechaRegistro(u.getFechaRegistro());
        p.setIdRol      (u.getIdRol());
        return generarCarnet(p);
    }

    private static String generarFirmaElectronica(Usuario u) {
        FirmaService.inicializar();
        String firmaCorta = FirmaService.generarFirmaPersona(
                u.getIdUsuario(), u.getNombreCompleto(),
                u.getCarne() != null ? u.getCarne() : "",
                u.getCorreo()
        );
        return "RSA-SHA256: " + (firmaCorta != null ? firmaCorta : "N/A");
    }

    // ═══════════════════════════════════════════════════════════════
    //  New method: generates attendance PDF from Asistencia objects
    //  Supports PRESENTE / TARDANZA / AUSENTE with color coding
    // ═══════════════════════════════════════════════════════════════
    public static String generarListaAsistencia(
            app.modelos.Curso curso,
            java.util.List<app.modelos.Asistencia> lista,
            java.time.LocalDate fecha) {

        // Build String[] rows compatible with existing generarReporteAsistencia
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        for (app.modelos.Asistencia a : lista) {
            String nombre = a.getPersona() != null
                ? a.getPersona().getNombre() + " " + a.getPersona().getApellido() : "—";
            String correo = a.getPersona() != null ? a.getPersona().getCorreo() : "—";
            String estado = a.getEstado() != null ? a.getEstado() : "PENDIENTE";
            rows.add(new String[]{nombre, correo, estado});
        }

        return generarReporteAsistencia(
            curso.getNombreCurso(),
            curso.getNombreCatedratico() != null ? curso.getNombreCatedratico() : "—",
            curso.getSeccion(),
            fecha,
            rows
        );
    }
}
