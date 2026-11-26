package com.newgen.los.dao;

import com.newgen.los.model.ReportTable;
import com.newgen.los.util.NamedSql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ReportDao {

    private final Map<String, NamedSql> queries = NamedSql.load("sql/reports.sql");

    public ReportTable run(Connection c, String name, String title, Map<String, Object> params) throws SQLException {
        NamedSql query = queries.get(name);
        if (query == null) {
            throw new IllegalArgumentException("Unknown report query " + name);
        }
        try (PreparedStatement ps = c.prepareStatement(query.sql())) {
            List<String> names = query.parameters();
            for (int i = 0; i < names.size(); i++) {
                Object value = params.get(names.get(i));
                if (value == null) {
                    ps.setNull(i + 1, Types.VARCHAR);
                } else {
                    ps.setObject(i + 1, value);
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    columns.add(meta.getColumnLabel(i));
                }
                ReportTable table = new ReportTable(title, columns);
                while (rs.next()) {
                    List<Object> row = new ArrayList<>();
                    for (int i = 1; i <= columns.size(); i++) {
                        row.add(rs.getObject(i));
                    }
                    table.addRow(row);
                }
                return table;
            }
        }
    }
}
