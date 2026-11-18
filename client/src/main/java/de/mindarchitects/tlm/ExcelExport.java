package de.mindarchitects.tlm;

import de.mindarchitects.tlm.api.Model.Registration;
import de.mindarchitects.tlm.api.Model.Training;
import de.mindarchitects.tlm.api.Model.TrainingDetail;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** Export eines Trainings als .xlsx: Blatt „Teilnehmer“ mit allen Registrierungen. */
public final class ExcelExport {
    private ExcelExport() {}

    private static final String DATE_FORMAT = "dd.mm.yyyy hh:mm";

    public static void write(TrainingDetail d, Path file) throws IOException {
        try (OutputStream out = Files.newOutputStream(file)) {
            Workbook wb = new Workbook(out, "TrainingListmanager", "1.0");
            writeRegistrations(wb.newWorksheet("Teilnehmer"), d);
            wb.finish();
        }
    }

    /** Vorschlag für den Dateinamen, z. B. "Verkaufstraining_2026-12-01.xlsx". */
    public static String suggestedFileName(Training t) {
        String name = Fmt.nz(t.title()).replaceAll("[\\\\/:*?\"<>|]+", "").replaceAll("\\s+", "_");
        if (name.isEmpty()) {
            name = "Training_" + t.id();
        }
        LocalDateTime start = Fmt.parse(t.startsAt());
        return name + (start == null ? "" : "_" + start.toLocalDate()) + ".xlsx";
    }

    private static void writeRegistrations(Worksheet ws, TrainingDetail d) {
        Training t = d.training();
        ws.value(0, 0, t.title());
        ws.style(0, 0).bold().fontSize(14).set();
        String when = Fmt.dateTime(t.startsAt());
        ws.value(1, 0, String.join("  ·  ", List.of(
                when.isEmpty() ? "ohne Datum" : when + " Uhr",
                Fmt.nz(t.location()).isEmpty() ? "ohne Ort" : t.location())));
        ws.value(2, 0, d.registrations().size() + " Teilnehmer");

        int header = 4;
        List<String> cols = List.of("Nr.", "Vorname", "Nachname", "Job-Position", "Händler", "Registriert am", "Einwilligung am");
        header(ws, header, cols);

        List<Registration> regs = d.registrations().stream()
                .sorted(Comparator.comparing((Registration r) -> Fmt.nz(r.lastName()).toLowerCase())
                        .thenComparing(r -> Fmt.nz(r.firstName()).toLowerCase()))
                .toList();
        int row = header + 1;
        for (Registration r : regs) {
            ws.value(row, 0, row - header);
            ws.value(row, 1, r.firstName());
            ws.value(row, 2, r.lastName());
            ws.value(row, 3, r.jobPosition());
            ws.value(row, 4, r.dealer());
            dateCell(ws, row, 5, r.createdAt());
            dateCell(ws, row, 6, r.consentAt());
            row++;
        }
        finishTable(ws, header, row - 1, cols.size(), new double[]{6, 18, 20, 24, 28, 18, 18});
    }

    private static void header(Worksheet ws, int row, List<String> cols) {
        for (int c = 0; c < cols.size(); c++) {
            ws.value(row, c, cols.get(c));
        }
        ws.range(row, 0, row, cols.size() - 1).style()
                .bold().fillColor("DDE4EE").borderStyle("thin").set();
    }

    private static void finishTable(Worksheet ws, int headerRow, int lastRow, int colCount, double[] widths) {
        for (int c = 0; c < widths.length; c++) {
            ws.width(c, widths[c]);
        }
        ws.freezePane(0, headerRow + 1);
        if (lastRow > headerRow) {
            ws.setAutoFilter(headerRow, 0, colCount - 1);
        }
    }

    private static void dateCell(Worksheet ws, int row, int col, String sqlDate) {
        LocalDateTime dt = Fmt.parse(sqlDate);
        if (dt != null) {
            ws.value(row, col, dt);
            ws.style(row, col).format(DATE_FORMAT).set();
        }
    }
}
