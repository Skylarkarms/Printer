import interfaces.ToStringFunction;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;

public class PrinterTest {
    static int[] ints = new int[]{1, 4, 6, 8, 12};
    public static void main(String[] args) {
        Printer.printStack(true);
        Printer.cyan.print(ToStringFunction.inspect(ints));
        long nanos = Duration.ofSeconds(3).toNanos();
        Printer.Chrono chrono = new Printer.Chrono(Printer.Chrono.Format.full, Printer.yellow);
        chrono.start();
        LockSupport.parkNanos(nanos);
        chrono.elapsed();
        LockSupport.parkNanos(nanos);
        ToStringFunction.StackPrinter.params.prefix = "\n >> BEGINS >> \n";
        long elapse = chrono.elapsedNanos();
        Printer.red.print(elapse);
    }
}
