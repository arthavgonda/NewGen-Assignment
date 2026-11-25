package com.newgen.los.model;

import java.util.ArrayList;
import java.util.List;

public class ReportTable {

    private final String title;
    private final List<String> columns;
    private final List<List<Object>> rows = new ArrayList<>();

    public ReportTable(String title, List<String> columns) {
        this.title = title;
        this.columns = List.copyOf(columns);
    }

    public void addRow(List<Object> row) {
        rows.add(row);
    }

    public String getTitle() {
        return title;
    }

    public List<String> getColumns() {
        return columns;
    }

    public List<List<Object>> getRows() {
        return rows;
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }
}
