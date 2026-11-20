package thurnos.brake_simulation.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Small CSV reader for the tools: header row, comma separated, double quotes for fields with commas. */
final class CsvTable {

    private final List<String> header;
    private final List<List<String>> rows = new ArrayList<>();

    private CsvTable(List<String> header) {
        this.header = header;
    }

    static CsvTable read(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        CsvTable t = null;
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) continue;
            List<String> f = split(line);
            if (t == null) t = new CsvTable(f);
            else t.rows.add(f);
        }
        if (t == null) throw new IOException("empty file: " + file);
        return t;
    }

    int size() {
        return rows.size();
    }

    boolean has(String column) {
        return header.contains(column);
    }

    /** Text of a cell, or null if the cell is empty or says NULL. */
    String text(int row, String column) {
        int i = header.indexOf(column);
        if (i < 0 || i >= rows.get(row).size()) return null;
        String v = rows.get(row).get(i).trim();
        return v.isEmpty() || v.equalsIgnoreCase("NULL") ? null : v;
    }

    Double number(int row, String column) {
        String v = text(row, column);
        return v == null ? null : Double.valueOf(v);
    }

    static List<String> split(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') quoted = !quoted;
            else if (c == ',' && !quoted) {
                out.add(cur.toString());
                cur.setLength(0);
            } else cur.append(c);
        }
        out.add(cur.toString());
        return out;
    }
}
