package com.newgen.los.console;

import com.newgen.los.util.DateUtil;
import com.newgen.los.util.MoneyUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

public class ConsoleIO {

    private final BufferedReader in;
    private final PrintStream out;

    public ConsoleIO() {
        this.in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        this.out = System.out;
    }

    public PrintStream out() {
        return out;
    }

    public String readLine(String prompt) {
        out.print(prompt);
        out.flush();
        try {
            String line = in.readLine();
            if (line == null) {
                throw new EndOfInputException();
            }
            return line.trim();
        } catch (IOException e) {
            throw new EndOfInputException();
        }
    }

    public int choice(String prompt, int max) {
        while (true) {
            String line = readLine(prompt);
            if (line.matches("\\d+")) {
                int value = Integer.parseInt(line);
                if (value >= 0 && value <= max) {
                    return value;
                }
            }
            error("Enter a number between 0 and " + max);
        }
    }

    public String text(String label, boolean required, String current) {
        while (true) {
            String line = readLine(label + suffix(current) + ": ");
            if (line.isEmpty()) {
                if (current != null) {
                    return current;
                }
                if (!required) {
                    return null;
                }
                error(label + " is required");
                continue;
            }
            if (line.equals("-") && !required) {
                return null;
            }
            return line;
        }
    }

    public Integer integer(String label, boolean required, Integer current) {
        while (true) {
            String line = readLine(label + suffix(current == null ? null : current.toString()) + ": ");
            if (line.isEmpty()) {
                if (current != null || !required) {
                    return current;
                }
                error(label + " is required");
                continue;
            }
            if (line.matches("-?\\d+")) {
                return Integer.parseInt(line);
            }
            error(label + " must be a whole number");
        }
    }

    public BigDecimal amount(String label, boolean required, BigDecimal current) {
        while (true) {
            String line = readLine(label + suffix(current == null ? null : MoneyUtil.format(current)) + ": ");
            if (line.isEmpty()) {
                if (current != null || !required) {
                    return current;
                }
                error(label + " is required");
                continue;
            }
            try {
                return MoneyUtil.parse(line);
            } catch (IllegalArgumentException e) {
                error(e.getMessage());
            }
        }
    }

    public LocalDate date(String label, boolean required, LocalDate current) {
        while (true) {
            String line = readLine(label + " (dd-MM-yyyy)" + suffix(current == null ? null : DateUtil.format(current)) + ": ");
            if (line.isEmpty()) {
                if (current != null || !required) {
                    return current;
                }
                error(label + " is required");
                continue;
            }
            try {
                return DateUtil.parse(line);
            } catch (IllegalArgumentException e) {
                error(e.getMessage());
            }
        }
    }

    public boolean confirm(String question) {
        String line = readLine(question + " (y/n): ");
        return line.equalsIgnoreCase("y") || line.equalsIgnoreCase("yes");
    }

    public void pause() {
        readLine("\nPress Enter to continue...");
    }

    public void title(String text) {
        out.println();
        out.println(text);
        out.println("-".repeat(Math.max(20, text.length())));
    }

    public void line(String text) {
        out.println(text);
    }

    public void field(String label, Object value) {
        out.printf("  %-26s %s%n", label, value == null ? "" : value);
    }

    public void success(String text) {
        out.println("  [OK] " + text);
    }

    public void error(String text) {
        out.println("  [!] " + text);
    }

    private static String suffix(String current) {
        return current == null ? "" : " [" + current + "]";
    }

    public static class EndOfInputException extends RuntimeException {
    }
}
