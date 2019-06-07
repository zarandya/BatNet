package android.util;

import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class Log {
    private static final boolean writeLogToFile = false;
    private static final FileOutputStream log = initLog();

    private static FileOutputStream initLog() {
        if (!writeLogToFile) {
            return null;
        }
        try {
            return new FileOutputStream("log/log.txt");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static int d(String tag, String msg) {
        System.out.println("DEBUG: " + tag + ": " + msg);
        if (writeLogToFile) {
            try {
                assert log != null;
                log.write(("DEBUG: " + tag + ": " + msg + "\n").getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.flush();
        return 0;
    }

    public static int i(String tag, String msg) {
        System.out.println("INFO: " + tag + ": " + msg);
        if (writeLogToFile) {
            try {
                assert log != null;
                log.write(("INFO: " + tag + ": " + msg + "\n").getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.flush();
        return 0;
    }

    public static int w(String tag, String msg) {
        System.out.println("WARN: " + tag + ": " + msg);
        if (writeLogToFile) {
            try {
                assert log != null;
                log.write(("WARN: " + tag + ": " + msg + "\n").getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.flush();
        return 0;
    }

    public static int e(String tag, String msg) {
        System.out.println("ERROR: " + tag + ": " + msg);
        if (writeLogToFile) {
            try {
                assert log != null;
                log.write(("ERROR: " + tag + ": " + msg + "\n").getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.flush();
        return 0;
    }

}
