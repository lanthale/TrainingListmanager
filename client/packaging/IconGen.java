import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Erzeugt das App-Icon (Teilnehmerliste mit Häkchen auf blauem Grund) in allen Größen.
 *
 *   java client/packaging/IconGen.java client
 *
 * Ausgabe:
 *   packaging/icon.iconset/*.png    → mit "iconutil -c icns" zu TrainingListmanager.icns (macOS)
 *   packaging/TrainingListmanager.ico (Windows, PNG-Einträge 16–256)
 *   packaging/TrainingListmanager.png (Linux, 512)
 *   src/main/resources/icons/app-*.png (Fenstersymbol)
 */
public class IconGen {

    public static void main(String[] args) throws IOException {
        File base = new File(args.length > 0 ? args[0] : ".");
        File pack = new File(base, "packaging");
        File iconset = new File(pack, "icon.iconset");
        File res = new File(base, "src/main/resources/icons");
        iconset.mkdirs();
        res.mkdirs();

        // macOS-Iconset
        int[] mac = {16, 32, 128, 256, 512};
        for (int s : mac) {
            ImageIO.write(render(s), "png", new File(iconset, "icon_" + s + "x" + s + ".png"));
            ImageIO.write(render(s * 2), "png", new File(iconset, "icon_" + s + "x" + s + "@2x.png"));
        }
        // Windows-ICO
        List<byte[]> entries = new ArrayList<>();
        int[] win = {16, 24, 32, 48, 64, 128, 256};
        for (int s : win) {
            entries.add(png(render(s)));
        }
        writeIco(new File(pack, "TrainingListmanager.ico"), win, entries);
        // Linux
        ImageIO.write(render(512), "png", new File(pack, "TrainingListmanager.png"));
        // Fenstersymbole
        for (int s : new int[]{16, 32, 64, 128, 256}) {
            ImageIO.write(render(s), "png", new File(res, "app-" + s + ".png"));
        }
        System.out.println("Icons erzeugt in " + pack.getAbsolutePath());
    }

    /** Zeichnet das Icon auf einer 1024er-Fläche und skaliert auf size. */
    static BufferedImage render(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        double k = size / 1024.0;
        g.scale(k, k);

        boolean small = size <= 32;   // kleine Größen: weniger Rand, kräftigere Formen
        double inset = small ? 40 : 100;
        double body = 1024 - 2 * inset;

        // Schatten (nur große Größen)
        if (!small) {
            g.setColor(new Color(0, 0, 0, 50));
            g.fill(new RoundRectangle2D.Double(inset, inset + 14, body, body, body * 0.45, body * 0.45));
        }
        // Hintergrund: Verlauf Blau
        RoundRectangle2D bg = new RoundRectangle2D.Double(inset, inset, body, body, body * 0.45, body * 0.45);
        g.setPaint(new GradientPaint(0, (float) inset, new Color(0x3B82F6), 0, (float) (inset + body), new Color(0x1E3A8A)));
        g.fill(bg);

        // Klemmbrett
        double cx = 512, cardW = small ? 520 : 430, cardH = small ? 620 : 540;
        double cardX = cx - cardW / 2, cardY = small ? 250 : 270;
        g.setColor(new Color(0, 0, 0, 40));
        g.fill(new RoundRectangle2D.Double(cardX, cardY + 12, cardW, cardH, 56, 56));
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Double(cardX, cardY, cardW, cardH, 56, 56));
        // Klammer oben
        double clipW = cardW * 0.42;
        g.setColor(new Color(0xCBD5E1));
        g.fill(new RoundRectangle2D.Double(cx - clipW / 2, cardY - 40, clipW, 90, 36, 36));

        // Drei Zeilen: zwei abgehakt (grün), eine offen
        int rows = small ? 2 : 3;
        double rowGap = (cardH - 140) / rows;
        for (int i = 0; i < rows; i++) {
            double y = cardY + 120 + i * rowGap;
            double r = small ? 64 : 46;
            double circleX = cardX + (small ? 60 : 55);
            boolean done = i < rows - 1;
            if (done) {
                g.setColor(new Color(0x22C55E));
                g.fill(new Ellipse2D.Double(circleX, y - r, 2 * r, 2 * r));
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(small ? 22 : 16, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D check = new Path2D.Double();
                check.moveTo(circleX + r * 0.52, y + r * 0.02);
                check.lineTo(circleX + r * 0.88, y + r * 0.38);
                check.lineTo(circleX + r * 1.50, y - r * 0.36);
                g.draw(check);
            } else {
                g.setColor(new Color(0x94A3B8));
                g.setStroke(new BasicStroke(small ? 18 : 12));
                g.draw(new Ellipse2D.Double(circleX + 6, y - r + 6, 2 * r - 12, 2 * r - 12));
            }
            // Textzeile
            double lineX = circleX + 2 * r + (small ? 40 : 34);
            double lineW = cardX + cardW - (small ? 50 : 50) - lineX;
            g.setColor(done ? new Color(0x334155) : new Color(0xCBD5E1));
            double lh = small ? 56 : 34;
            g.fill(new RoundRectangle2D.Double(lineX, y - lh / 2, lineW * (i == 1 ? 0.75 : 1.0), lh, lh, lh));
        }
        g.dispose();
        return img;
    }

    static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /** ICO mit PNG-komprimierten Einträgen (ab Windows Vista unterstützt). */
    static void writeIco(File file, int[] sizes, List<byte[]> pngs) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
            ByteBuffer header = ByteBuffer.allocate(6 + 16 * sizes.length).order(ByteOrder.LITTLE_ENDIAN);
            header.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
            int offset = 6 + 16 * sizes.length;
            for (int i = 0; i < sizes.length; i++) {
                int s = sizes[i];
                header.put((byte) (s >= 256 ? 0 : s)).put((byte) (s >= 256 ? 0 : s));
                header.put((byte) 0).put((byte) 0);
                header.putShort((short) 1).putShort((short) 32);
                header.putInt(pngs.get(i).length).putInt(offset);
                offset += pngs.get(i).length;
            }
            out.write(header.array());
            for (byte[] p : pngs) {
                out.write(p);
            }
        }
    }
}
