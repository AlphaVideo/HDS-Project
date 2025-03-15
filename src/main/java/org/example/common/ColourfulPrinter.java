package org.example.common;

public class ColourfulPrinter {
    private String _name;
    private boolean _isDebug;

    public ColourfulPrinter(int name, boolean isDebug) {
        _name = String.valueOf(name);
        _isDebug = isDebug;
    }

    public ColourfulPrinter(String name, boolean isDebug) {
        _name = name;
        _isDebug = isDebug;
    }

    public void error(String format, Object... args) {
        String msg = String.format(format, args);
        System.err.printf("\033[91m[%s]\033[0m %s%n", _name, msg);
    }

    public void warn(String format, Object... args) {
        String msg = String.format(format, args);
        System.out.printf("\033[93m[%s]\033[0m %s%n", _name, msg);
    }

    public void ok(String format, Object... args) {
        String msg = String.format(format, args);
        System.out.printf("\033[92m[%s]\033[0m %s%n", _name, msg);
    }

    public void debug(String format, Object... args) {
        String msg = String.format(format, args);
        if (_isDebug)
            System.out.printf("\033[96m[%s]\033[0m %s%n", _name, msg);
    }
}
