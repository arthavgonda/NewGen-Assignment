package com.newgen.los.dao;

import com.newgen.los.exception.DataAccessException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;

public class Database {

    private static final int MYSQL_DUPLICATE_KEY = 1062;

    private final String url;
    private final String user;
    private final String password;

    public Database(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public <T> T withConnection(SqlWork<T> work) {
        try (Connection connection = getConnection()) {
            return work.run(connection);
        } catch (SQLException e) {
            throw translate(e);
        }
    }

    // any exception, SQL or business, rolls back the whole unit
    public <T> T inTransaction(SqlWork<T> work) {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                rollbackQuietly(connection);
                throw e;
            }
        } catch (SQLException e) {
            throw translate(e);
        }
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // connection is closing anyway
        }
    }

    static DataAccessException translate(SQLException e) {
        if (e instanceof SQLIntegrityConstraintViolationException && e.getErrorCode() == MYSQL_DUPLICATE_KEY) {
            return new DuplicateKeyException(e);
        }
        return new DataAccessException("Database error: " + e.getMessage(), e);
    }

    public void initSchema() {
        String script;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("sql/schema.sql")) {
            if (in == null) {
                throw new IllegalStateException("sql/schema.sql not found on classpath");
            }
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read schema", e);
        }
        withConnection(connection -> {
            try (Statement statement = connection.createStatement()) {
                for (String sql : script.split(";\\s*(\\r?\\n|$)")) {
                    if (!sql.isBlank()) {
                        statement.execute(sql);
                    }
                }
            }
            return null;
        });
    }

    public boolean isReachable() {
        try (Connection connection = getConnection()) {
            return connection.isValid(3);
        } catch (SQLException e) {
            return false;
        }
    }
}
