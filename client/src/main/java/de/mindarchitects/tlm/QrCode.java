package de.mindarchitects.tlm;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Erzeugt QR-Codes lokal als PNG (ZXing), wahlweise mit Beschriftung zum Ausdrucken. */
public final class QrCode {
    private QrCode() {}

    /** Nur der QR-Code, quadratisch. */
    public static byte[] png(String content, int size) {
        return toPng(image(content, size));
    }

    /**
     * QR-Code mit Überschrift (Trainingstitel), Unterzeile (Datum/Ort) und Hinweis zum Scannen –
     * zum Ausdrucken oder Zeigen per Beamer.
     */
    public static byte[] poster(String content, String title, String subtitle) {
        int width = 1200;
        int qrSize = 1000;
        Font titleFont = new Font(Font.SANS_SERIF, Font.BOLD, 52);
        Font subFont = new Font(Font.SANS_SERIF, Font.PLAIN, 36);
        Font hintFont = new Font(Font.SANS_SERIF, Font.PLAIN, 32);

        // Höhe vorab berechnen (Titel kann umbrechen)
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D pg = probe.createGraphics();
        List<String> titleLines = wrap(title, pg.getFontMetrics(titleFont), width - 120);
        pg.dispose();

        int top = 70;
        int titleHeight = titleLines.size() * 64;
        int subHeight = subtitle == null || subtitle.isBlank() ? 0 : 56;
        int qrTop = top + titleHeight + subHeight + 20;
        int height = qrTop + qrSize + 110;

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        g.setColor(new Color(0x10121F));
        g.setFont(titleFont);
        int y = top + 48;
        for (String line : titleLines) {
            centered(g, line, width, y);
            y += 64;
        }
        if (subHeight > 0) {
            g.setColor(new Color(0x383A4F));
            g.setFont(subFont);
            centered(g, subtitle, width, y + 4);
        }
        g.drawImage(image(content, qrSize), (width - qrSize) / 2, qrTop, null);

        g.setColor(new Color(0x383A4F));
        g.setFont(hintFont);
        centered(g, "Mit dem Handy scannen und registrieren", width, qrTop + qrSize + 55);
        g.dispose();
        return toPng(img);
    }

    private static BufferedImage image(String content, int size) {
        try {
            BitMatrix m = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 2,
                    EncodeHintType.CHARACTER_SET, "UTF-8"));
            BufferedImage img = new BufferedImage(m.getWidth(), m.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < m.getHeight(); y++) {
                for (int x = 0; x < m.getWidth(); x++) {
                    img.setRGB(x, y, m.get(x, y) ? 0x000000 : 0xFFFFFF);
                }
            }
            return img;
        } catch (WriterException e) {
            throw new IllegalStateException("QR-Code konnte nicht erzeugt werden", e);
        }
    }

    private static byte[] toPng(BufferedImage img) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("PNG konnte nicht erzeugt werden", e);
        }
    }

    private static void centered(Graphics2D g, String text, int width, int baseline) {
        int w = g.getFontMetrics().stringWidth(text);
        g.drawString(text, (width - w) / 2, baseline);
    }

    private static List<String> wrap(String text, FontMetrics fm, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : Fmt.nz(text).split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(candidate) > maxWidth && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines.isEmpty() ? List.of("") : lines.subList(0, Math.min(lines.size(), 3));
    }
}
