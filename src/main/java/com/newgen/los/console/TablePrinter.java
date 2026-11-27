package com.newgen.los.console;

import com.newgen.los.model.ReportTable;
import com.newgen.los.util.Display;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public final class TablePrinter {

    private static final int MAX_WIDTH = 42;

    private TablePrinter() {
    }

    public static void print(PrintStream out, ReportTable table) {
        List<String> headers = new ArrayList<>();
        for (String column : table.getColumns()) {
            headers.add(Display.heading(column));
        }
        List<List<String>> rows = new ArrayList<>();
        List<Boolean> numeric = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            numeric.add(Boolean.FALSE);
        }
        for (List<Object> row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (int i = 0; i < row.size(); i++) {
                Object value = row.get(i);
                if (value instanceof Number) {
                    numeric.set(i, Boolean.TRUE);
                }
                cells.add(Display.cell(value));
            }
            rows.add(cells);
        }
        out.println();
        out.println(table.getTitle());
        print(out, headers, rows, numeric);
    }

    public static void print(PrintStream out, List<String> headers, List<List<String>> rows) {
        List<Boolean> numeric = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            numeric.add(Boolean.FALSE);
        }
        print(out, headers, rows, numeric);
    }

    private static void print(PrintStream out, List<String> headers, List<List<String>> rows, List<Boolean> numeric) {
        int[] widths = new int[headers.size()];
        for (int i = 0; i < headers.size(); i++) {
            widths[i] = headers.get(i).length();
        }
        for (List<String> row : rows) {
            for (int i = 0; i < row.size() && i < widths.length; i++) {
                widths[i] = Math.min(MAX_WIDTH, Math.max(widths[i], row.get(i).length()));
            }
        }
        StringBuilder rule = new StringBuilder("+");
        for (int w : widths) {
            rule.append("-".repeat(w + 2)).append('+');
        }
        out.println(rule);
        out.println(format(headers, widths, null));
        out.println(rule);
        if (rows.isEmpty()) {
            out.println("  (no records)");
        }
        for (List<String> row : rows) {
            out.println(format(row, widths, numeric));
        }
        out.println(rule);
        out.println("  " + rows.size() + " row(s)");
    }

    private static String format(List<String> cells, int[] widths, List<Boolean> numeric) {
        StringBuilder line = new StringBuilder("|");
        for (int i = 0; i < widths.length; i++) {
            String value = i < cells.size() ? cells.get(i) : "";
            if (value.length() > widths[i]) {
                value = value.substring(0, widths[i] - 2) + "..";
            }
            boolean right = numeric != null && numeric.get(i);
            String pad = " ".repeat(widths[i] - value.length());
            line.append(' ').append(right ? pad + value : value + pad).append(" |");
        }
        return line.toString();
    }
}
