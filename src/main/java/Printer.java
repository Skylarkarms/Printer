import interfaces.StringUnaryOperator;
import interfaces.ToStringFunction;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.function.Function;

/**
 * A Java Print dependency for easy debugging, uses the default {@link System#out} {@link PrintStream}.
 * <p> Methods like {@link #setAutoFlush(boolean)} will alter the global state of {@link System#out}.
 * <p> Sub-components:
 * <ul>
 *     <li>
 *         {@link Chrono}
 *     </li>
 *     <li>
 *         {@link Export}
 *     </li>
 *     <li>
 *         {@link Editor}
 *     </li>
 * </ul>
 * */
public enum Printer {

    green("\u001B[32m"),
    purple("\u001B[35m"),
    white("\u001B[37m"),
    red("\u001B[31m"),
    yellow("\u001B[33m"),
    blue("\u001B[34m"),
    cyan("\u001B[36m");

    private static final String ANSI_RESET = "\u001B[0m";

    private final StringUnaryOperator colorWrap;
    interface Applier {
        String apply(StringUnaryOperator op, String s);
        Applier ident = Function::apply;
        Applier stack = (op, s) -> op.apply(s.concat(ToStringFunction.StackPrinter.PROV.toString(
                Thread.currentThread().getStackTrace(), Printer.range
        )));
    }

    private Applier printer;

    private static boolean printStack = false;

    /**
     * @return true if the stacktrace is set to be printed by the API.
     * */
    static boolean isStackPrinted() {return printStack;}

    /**
     * Allows the printer to display the call stack.
     * @param value true if the API should display stacks when printing.
     *              false if it shouldn't
     * @implNote
     * <p> To change the format in which the {@link StackTraceElement}s are displayed see
     * {@link ToStringFunction.StackPrinter.Params}
     * <p> To change the range of stacks to be display see {@link Printer#range}
     * */
    public static void printStack(boolean value) {
        if (printStack != value) {
            Printer[] cache = values();
            if (value) {
                for (int i = cache.length - 1; i >= 0; i--) {
                    Printer printer = cache[i];
                    if (printer != null) printer.printer = Applier.stack;
                }
            } else {
                for (int i = cache.length - 1; i >= 0; i--) {
                    Printer printer = cache[i];
                    printer.printer = Applier.ident;
                }
            }
            printStack = value;
        }
    }

    /**
     * When setting {@link #printStack} to true, this parameter will redefine the way in which the stack is displayed.
     * @see ToStringFunction.Arrays.ViewRange
     * @implNote Will be set to {@link ToStringFunction.Arrays.ViewRange#single(int)} with a value of 3 by default.
     * */
    public static ToStringFunction.Arrays.ViewRange range = ToStringFunction.Arrays.ViewRange.single(3);

    Printer(String color) {
        colorWrap = s -> color + s + ANSI_RESET;
        printer = isStackPrinted() ? Applier.stack : Applier.ident;

    }

    /**
     * Prints a message
     * @param message the message to be printed
     * */
    public void print(String message) {
        System.out.println(printer.apply(colorWrap, message));
    }

    private static final String d_dot = ": ";

    /**
     * Prints a message with a `TAG` of format:
     * <p> TAG: [Message begins here...]
     * @param TAG the tag to be prefixed.
     * @param message the message to be printed.
     * */
    public void print(String TAG, String message) {
        System.out.println(printer.apply(colorWrap, TAG + d_dot + message));
    }

    /**
     * Variation of {@link #print(String, String)} that will call {@link String#valueOf(Object)}
     * */
    public void print(String TAG, Object o) {
        System.out.println(printer.apply(colorWrap, TAG + d_dot + o));
    }

    public void print(Object o) {
        System.out.println(printer.apply(colorWrap, String.valueOf(o)));
    }

    public void print(long aLong) {
        System.out.println(printer.apply(colorWrap, "long = ".concat(Long.toString(aLong))));
    }

    public void print(String TAG, int i) {
        System.out.println(printer.apply(colorWrap, TAG + d_dot + "int = " + i));
    }

    public void print(String TAG, Integer i) {
        System.out.println(printer.apply(colorWrap, TAG + d_dot + "Integer = " + i));
    }

    public void print(Integer i) {
        System.out.println(printer.apply(colorWrap, "Integer = " + i));
    }

    private static final PrintStream nonFlushed = System.out, flushed = new PrintStream(System.out, true);

    /**
     * Sets the default {@link System#out} {@link PrintStream} to {@link PrintStream#PrintStream(OutputStream, boolean)}.
     * <p> Where: {@code autoflush} boolean = true.
     * @see PrintWriter#PrintWriter(java.io.Writer, boolean)
     * */
    public static boolean setAutoFlush(boolean autoFlush) {
        if ((flushed == System.out) == autoFlush) return false;
        System.setOut(autoFlush ? flushed : nonFlushed);
        return true;
    }

    /**
     * Wide {@link String} divisor.
     * */
    public static final String
            divisor =
            """

                     || >>>>>>>> || ** \s
                    ================\s
                     || >>>>>>>> || ** \s""".indent(1);

    private static final String space = "\s";
    private static String toPhrase(String... words) {
        return String.join(space, words);
    }

    public static String depthStack(int depth) {
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        return toPhrase(ofSize(
                depth,
                i -> elements[4 + i].toString() + ", \n "
        ));
    }

    private static String[] ofSize(
            int ofLength,
            ToStringFunction.FromInt xIntFunction
    ) {
        String[] result = new String[ofLength];
        for (int i = 0; i < ofLength; i++) {
            result[i] = xIntFunction.asString(i);
        }
        return result;
    }

    public static String thisStack() {
        return Thread.currentThread().getStackTrace()[3].toString();
    }

    /**
     * Component that helps is the measure of nanos via
     * atomic CAS-sing.
     * */
    public static final class Chrono {
        private static int id = 0;
        private volatile long begin;
        private final Object lock = new Object();
        private volatile long last;
        private final Format format;
        private final Printer color;
        private final int chronoId = id++;


        private static final VarHandle VAR_HANDLE;

        static {
            try {
                VAR_HANDLE = MethodHandles.lookup().findVarHandle(Chrono.class, "last", long.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private boolean compareAndSet(long prev, long next) {
            return VAR_HANDLE.compareAndSet(this, prev, next);
        }

        private void print(
                String prefix, long toFormat
        ) {
            color.print(
                    prefix + " at (chrono = " + chronoId + ")..." + formatNanos(format, toFormat)
            );
        }

        /**
         * Prints the elapsed time since a {@link #start()} was called.
         * */
        public void elapsed() {
            System.out.println(color.printer.apply(color.colorWrap,
                    "Elapsed" + " at (chrono = " + chronoId + ")..." + formatNanos(format, System.nanoTime() - begin)
            ));
        }

        /**
         * @return the nano long values since a {@link #start()} was called.
         * */
        public long elapsedNanos() {
            long res = System.nanoTime() - begin;
            assert begin != 0 : "Must have called 'start()' or 'silentStart()' first.";
            return res;
        }

        /**
         * Prints the time passed since last time this method was called
         * OR since a {@link #start()} was first called.
         * The operation is performed atomically
         * */
        public void lap() {
            long prev = last, now = System.nanoTime();
            assert last != 0 : "Must have called .start()";
            long lap = now - prev;
            if (compareAndSet(prev, now)) {
                print(
                        "Lapsed", lap);
            }
        }

        /**
         * {@code `synchronized`} version of {@link #lap()}
         * */
        public void syncLap() {
            synchronized (lock) {
                long prev = last;
                long lap = System.nanoTime() - prev;
                last = lap;
                print(
                        "Sync lapsed", lap);
            }
        }

        private static final String nanos_format = "%d Nanos";
        private static final String full_format = "%d Secs. %d Millis. %d Nanos";

        /**
         * Defines the way in which the {@link Chrono} object will print the time passed.
         * */
        public enum Format {
            /**
             * Will display just the nanos passed.
             * */
            nanos(
                    duration -> String.format(nanos_format, duration.getNano())
            ),
            /**
             * Will display the nanos passed in the format: {@link #full_format}
             * */
            full(
                    duration -> String.format(full_format, duration.getSeconds(), duration.toMillisPart(), duration.toNanosPart())
            );
            final Function<Duration, String> format;

            Format(Function<Duration, String> format) {
                this.format = format;
            }
        }

        static String formatNanos(Format format, long nanoseconds) {
            Duration duration = Duration.ofNanos(nanoseconds); // create a duration object
            return format.format.apply(duration);
        }

        static final SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a");

        /**
         * Default implementation of {@link #Chrono(Format, Printer, boolean)}.
         * <p> Where:
         * <ul>
         *     <li>
         *         {@code start} = {@code false}
         *     </li>
         * </ul>
         * */
        public Chrono(Format format, Printer color) {
            this(format, color, false);
        }

        /**
         * Mina constructor for the {@link Chrono} class
         * @param start, set's the chronometer going when this constructor is called with true.
         * @param color the color of the print
         * @param format the {@link Format} by which the nanos will be displayed.
         * */
        public Chrono(Format format, Printer color, boolean start) {
            this.color = color;
            this.format = format;
            if (start) start();
        }

        /**
         * Default implementation of {@link #Chrono(Format, Printer)}
         * <p> Where:
         * <ul>
         *     <li>
         *         {@link Format} = {@link Format#full}
         *     </li>
         * </ul>
         * */
        public Chrono(Printer color) {
            this(Format.full, color);
        }

        /**
         * Default implementation of {@link #Chrono(Format, Printer, boolean)}
         * <p> Where:
         * <ul>
         *     <li>
         *         {@link Format} = {@link Format#full}
         *     </li>
         * </ul>
         * */
        Chrono(Printer color, boolean start) {
            this(Format.full, color, start);
        }

        /**
         * Will set the starting time ({@link #begin}) to the exact {@link System#nanoTime()} when this method is being called.
         * */
        public void start() {
            this.begin = System.nanoTime();
            this.last = begin;
            System.out.println(color.printer.apply(color.colorWrap,
                    "Chrono " + chronoId + ", begins at = " + sdf.format(new Date(System.currentTimeMillis()))
            ));
        }

        /**
         * Will trigger a {@link #start()} without printing
         * */
        public void silentStart() {
            this.begin = System.nanoTime();
            this.last = begin;
        }

        /**
         * Without printing, this method will set the starting time ({@link #begin}) to the exact {@link System#nanoTime()}.
         * */
        public String startText() {
            this.begin = System.nanoTime();
            this.last = begin;
            return color.printer.apply(color.colorWrap,
                    "Chrono " + chronoId + ", begins at = " + sdf.format(new Date(System.currentTimeMillis()))
            );
        }

        /**
         * @return a {@link Chrono} object that implements a {@link #Chrono(Printer, boolean)} constructor,
         * <p> Where:
         * <ul>
         *     <li>
         *         {@code start} = true
         *     </li>
         * </ul>
         * */
        public static Chrono begin(Printer color) {
            return new Chrono(color, true);
        }
    }

    /**
     * A <a href="package-summary.html">functional interface</a> for exporting data to a file.
     */
    @FunctionalInterface
    interface Exporter {
        /**
         * Saves the provided headers and data to a file in the specified directory with the given file name.
         *
         * @param DIRECTORY the directory where the file will be saved
         * @param fileName the name of the file without the extension
         * @param headers an array of header values to be written as the first line of the file
         * @param data a 2D array representing the data rows to be written to the file. Each sub-array should match the length of the headers array
         *
         * <p>Example usage:</p>
         * <pre>{@code
         * public class ExporterExample {
         *     public static void main(String[] args) {
         *         String directory = "C:\\exports";
         *         String fileName = "example";
         *         String[] headers = {"ID", "Name", "Age"};
         *         String[][] data = {
         *             {"1", "Alice", "30"},
         *             {"2", "Bob", "25"},
         *             {"3", "Charlie", "35"}
         *         };
         *
         *         Export.to_csv.save(directory, fileName, headers, data);
         *     }
         * }
         * }</pre>
         */
        void save(
                String DIRECTORY,
                String fileName,
                String[] headers, String[][] data
        );
    }

    /**
     * Standard set of {@link Exporter} file extensions implementations.
     * @see Exporter#save(String, String, String[], String[][])
     * */
    public enum Export implements Exporter {
        to_csv(
                ".csv"
        );

        final String type;

        Export(String type) {
            this.type = type;
        }

        @Override
        public void save(String DIRECTORY, String fileName, String[] headers, String[][] data) {

            BufferedWriter csvWriter = null;
            // Construct the file path
            String filePath = DIRECTORY + "\\" +fileName;

            try {
                csvWriter = new BufferedWriter(new FileWriter(filePath + type));

                // Write headers
                csvWriter.write(String.join(",", headers));
                csvWriter.newLine();

                // Write data
                for (String[] row : data) {
                    assert row.length == headers.length :
                            "Data row length (" + row.length + ") does not match headers length (" + headers.length + ")";
                    csvWriter.write(String.join(",", row));
                    csvWriter.newLine();
                }

                // Flushing can be controlled manually
                csvWriter.flush();
                System.out.println("CSV file created successfully at: " + filePath);

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                // Ensure the writer is closed
                if (csvWriter != null) {
                    try {
                        csvWriter.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * File Editor for the given formats:
     * <ul>
     *     <li>
     *         {@link #HTML},
     *     </li>
     *     <li>
     *         {@link #TXT},
     *     </li>
     *     <li>
     *         {@link #XML},
     *     </li>
     * </ul>
     * <p> With methods such as:
     * <ul>
     *     <li>{@link #editLine(String, String, Line...)}</li>
     * </ul>
     * */
    public enum Editor {
        HTML(".html"),
        TXT(".txt"),
        XML(".xml");

        private final String extension;

        Editor(String extension) {
            this.extension = extension;
        }

        public String getExtension() {
            return extension;
        }

        /**
         * The object to define the line that will be swapped from the original file.
         * @param number the line number to be changed
         * @param content the new content to be swapped in the specified line.
         * */
        public record Line(int number, String content) {}

        /**
         * Edits specific lines in a file and writes the modified content to a new file with "_copy" appended to the original file name.
         * <p>
         * This method reads the original file line by line, replaces the specified lines with new content, and writes the result to a new file.
         * The new file will have the same name as the original file with "_copy" appended before the file extension.
         * </p>
         *
         * @param path      the directory path where the file is located
         * @param fileName  the name of the file without the extension
         * @param lines     varargs parameter of {@link Line} records, each containing the line number and new content
         *
         * <p>
         * Example usage:
         * </p>
         * <pre>{@code
         * public static void main(String[] args) {
         *     Editor editor = Editor.HTML;
         *     editor.editLine("path/to/your", "file",
         *         new Line(10, "<newTag>New Content</newTag>"),
         *         new Line(20, "<anotherTag>Another Content</anotherTag>")
         *     );
         * }
         * }</pre>
         */
        public void editLine(String path, String fileName, Line... lines) {
            Path filePath = Paths.get(path, fileName + this.extension);
            Path newFilePath = Paths.get(path, fileName + "_copy" + this.extension);

            try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8);
                 BufferedWriter writer = Files.newBufferedWriter(newFilePath, StandardCharsets.UTF_8)) {

                String line;
                int lineNumber = 0;

                int curL = 0, prevLN = -1;
                Line line1;
                while ((line = reader.readLine()) != null) {
                    while (curL < lines.length && lineNumber == (line1 = lines[curL]).number) {
                        assert prevLN < line1.number : "Lines are not in ascending order";
                        prevLN = line1.number;
                        line = line1.content;
                        curL++;
                    }
                    writer.write(line);
                    writer.newLine();
                    lineNumber++;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
