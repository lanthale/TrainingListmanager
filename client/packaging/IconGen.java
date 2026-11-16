import javax.imageio.ImageIO;
import java.awt.*;
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
 * Erzeugt das App-Icon (MINDArchitects-„M“ auf blaugrauem Grund) in allen Größen.
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
        mark = ImageIO.read(new File(pack, "m-logo.png"));
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

    /** Weißes M-Zeichen aus dem MINDArchitects-Logo (packaging/m-logo.png). */
    static BufferedImage mark;

    /** Zeichnet das Icon auf einer 1024er-Fläche und skaliert auf size. */
    static BufferedImage render(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        double k = size / 1024.0;
        g.scale(k, k);

        boolean small = size <= 32;   // kleine Größen: weniger Rand, größeres Zeichen
        double inset = small ? 40 : 100;
        double body = 1024 - 2 * inset;
        double arc = body * 0.45;

        if (!small) {
            g.setColor(new Color(0, 0, 0, 60));
            g.fill(new RoundRectangle2D.Double(inset, inset + 14, body, body, arc, arc));
        }
        // Hintergrund: Blaugrau der Webseite → Nachtblau
        RoundRectangle2D bg = new RoundRectangle2D.Double(inset, inset, body, body, arc, arc);
        g.setPaint(new GradientPaint(0, (float) inset, new Color(0x383A4F), 0, (float) (inset + body), new Color(0x10121F)));
        g.fill(bg);

        // M-Zeichen zentriert
        double maxW = small ? body * 0.78 : body * 0.62;
        double w = maxW, h = w * mark.getHeight() / mark.getWidth();
        double x = 512 - w / 2, y = 512 - h / 2 - (small ? 0 : 30);
        g.drawImage(mark, (int) Math.round(x), (int) Math.round(y), (int) Math.round(w), (int) Math.round(h), null);

        // Bronze-Linie unter dem Zeichen (Akzentfarbe der Webseite)
        if (!small) {
            g.setColor(new Color(0xBB875D));
            double lw = w * 0.45;
            g.fill(new RoundRectangle2D.Double(512 - lw / 2, y + h + 50, lw, 22, 22, 22));
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
