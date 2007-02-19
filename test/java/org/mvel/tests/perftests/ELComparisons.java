package org.mvel.tests.perftests;

import ognl.Ognl;
import org.mvel.MVEL;
import org.mvel.tests.main.res.Base;

import static java.lang.System.currentTimeMillis;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Performance Tests Comparing MVEL to OGNL with Same Expressions.
 */
public class ELComparisons implements Runnable {
    private Base baseClass = new Base();

    public static int RUN_MVEL = 1;
    public static int RUN_OGNL = 1 << 1;

    private static int COMPILED = 1 << 30;
    private static int INTERPRETED = 1 << 31;

    private static int ALL = RUN_MVEL + RUN_OGNL;

    private static final int TESTNUM = 50000;
    private static final int TESTITER = 5;

    private long ognlTotal = 0;
    private long mvelTotal = 0;

    private int testFlags = 0;

    private boolean silent = false;

    private static List<PerfTest> tests = new ArrayList<PerfTest>();

    static {

//        tests.add(new PerfTest("Simple String Pass-Through", "'Hello World'", ALL));
//        tests.add(new PerfTest("Shallow Property", "data", ALL));
//        tests.add(new PerfTest("Deep Property", "foo.bar.name", ALL));
//        tests.add(new PerfTest("Static Field Access (MVEL)", "Integer.MAX_VALUE", RUN_MVEL));
//        tests.add(new PerfTest("Static Field Access (OGNL)", "@java.lang.Integer@MAX_VALUE", RUN_OGNL));
//        tests.add(new PerfTest("Inline Array Creation (MVEL)", "{'foo', 'bar'}", RUN_MVEL));
//        tests.add(new PerfTest("Inline Array Creation (OGNL)", "new String[] {'foo', 'bar'}", RUN_OGNL));
        tests.add(new PerfTest("Collection Access + Method Call", "funMap['foo'].happy()", ALL));
//        tests.add(new PerfTest("Boolean compare", "data == 'cat'", ALL));
//        tests.add(new PerfTest("Object instantiation", "new String('Hello')", ALL));
//        tests.add(new PerfTest("Method access", "readBack('this is a string')", ALL));
//        tests.add(new PerfTest("Arithmetic", "10 + 1 - 1", ALL));
    }


    public ELComparisons() {


    }


    public void setTestFlags(int testFlags) {
        this.testFlags = testFlags;
    }

    public static void main(String[] args) throws Exception {


        ELComparisons omc = new ELComparisons();
        boolean multithreaded = false;
        boolean compiled = true;
        boolean interpreted = true;
        boolean continuous = false;
        boolean silent = false;
        long totaltime = 0;

        int threadMax = 1;

        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("-continuous")) continuous = true;
                else if (args[i].equals("-threaded")) {
                    if ((i + 1) == args.length) {
                        throw new RuntimeException("expected parameter for -threaded (number of threads)");
                    }
                    multithreaded = true;
                    threadMax = Integer.parseInt(args[++i]);

                }
                else if (args[i].equals("-nocompiled")) compiled = false;
                else if (args[i].equals("-nointerpret")) interpreted = false;
                else if (args[i].equals("-silent")) silent = true;
            }

        }

        int flags = (compiled ? COMPILED : 0) + (interpreted ? INTERPRETED : 0);

        long ognlTotals = 0;
        long mvelTotals = 0;

        if (multithreaded) {
            for (int threadNumber = 1; threadNumber < 100; threadNumber += 5) {
                totaltime = System.currentTimeMillis();

                System.out.println("Running concurrency stress test: " + threadNumber);

                ELComparisons ognlTests = new ELComparisons();
                ognlTests.setTestFlags(flags + RUN_OGNL);
                ognlTests.setSilent(silent);

                Thread[] threads = new Thread[threadNumber];
                for (int i = 0; i < threads.length; i++) {
                    threads[i] = new Thread(ognlTests);
                }

                for (Thread thread : threads) {
                    thread.run();
                }

                for (Thread thread : threads) {
                    thread.join();
                }

                ognlTotals = ognlTests.getOgnlTotal();

                ELComparisons mvelTests = new ELComparisons();
                mvelTests.setTestFlags(flags + RUN_MVEL);
                mvelTests.setSilent(silent);

                for (int i = 0; i < threads.length; i++) {
                    threads[i] = new Thread(mvelTests);
                }

                for (Thread thread : threads) {
                    thread.run();
                }

                for (Thread thread : threads) {
                    thread.join();
                }

                mvelTotals = mvelTests.getMvelTotal();


                totaltime = System.currentTimeMillis() - totaltime;

                System.out.println("\nPerformance Comparison Done. OUTPUT TOTALS:");
                System.out.println("Total Number of Threads: " + threadMax);
                System.out.println("OGNL Total Runtime (ms): " + ognlTotals);
                System.out.println("MVEL Total Runtime (ms): " + mvelTotals + " :: " + new BigDecimal(mvelTotals).divide(new BigDecimal(ognlTotals), 4, RoundingMode.HALF_UP));

            }

        }

    }

    public void run() {
        try {
            for (PerfTest test : tests) {
                runTest(test, TESTNUM);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void runTest(PerfTest test, int count) throws Exception {
        int exFlags = test.getRunFlags();
        String expression = test.getExpression();
        String name = test.getName();

        if (!silent) {
            System.out.println("Test Name            : " + test.getName());
            System.out.println("Expression           : " + test.getExpression());
            System.out.println("Iterations           : " + count);
        }

        if ((testFlags & INTERPRETED) != 0) {

            if (!silent) System.out.println("Interpreted Results  :");

            long time;
            long mem;
            long total = 0;
            long[] res = new long[TESTITER];


            if ((testFlags & RUN_OGNL) != 0 && ((total & RUN_OGNL)) != 0) {
                try {
                    // unbenched warm-up
                    for (int i = 0; i < count; i++) {
                        Ognl.getValue(expression, baseClass);
                    }

                    System.gc();

                    time = currentTimeMillis();
                    mem = Runtime.getRuntime().freeMemory();

                    for (int reps = 0; reps < TESTITER; reps++) {
                        for (int i = 0; i < count; i++) {
                            Ognl.getValue(expression, baseClass);
                        }

                        if (reps == 0) res[0] = total += currentTimeMillis() - time;
                        else res[reps] = (total * -1) + (total += currentTimeMillis() - time - total);


                    }

                    if (!silent)
                        System.out.println("(OGNL)               : " + new BigDecimal(((currentTimeMillis() - time))).divide(new BigDecimal(TESTITER), 2, RoundingMode.HALF_UP)
                                + "ms avg.  (mem delta: " + ((Runtime.getRuntime().freeMemory() - mem) / 1024) + "kb) " + resultsToString(res));
                }
                catch (Exception e) {
                    if (!silent)
                        System.out.println("(OGNL)               : <<COULD NOT EXECUTE>>");
                }

            }

            synchronized (this) {
                ognlTotal += total;
            }

            total = 0;

            if ((testFlags & RUN_MVEL) != 0 && ((exFlags & RUN_MVEL) != 0)) {
                try {
                    for (int i = 0; i < count; i++) {
                        MVEL.eval(expression, baseClass);
                    }

                    System.gc();

                    time = currentTimeMillis();
                    mem = Runtime.getRuntime().freeMemory();
                    for (int reps = 0; reps < TESTITER; reps++) {
                        for (int i = 0; i < count; i++) {
                            MVEL.eval(expression, baseClass);
                        }

                        if (reps == 0) res[0] = total += currentTimeMillis() - time;
                        else res[reps] = (total * -1) + (total += currentTimeMillis() - time - total);
                    }

                    if (!silent)
                        System.out.println("(MVEL)               : " + new BigDecimal(((currentTimeMillis() - time))).divide(new BigDecimal(TESTITER), 2, RoundingMode.HALF_UP)
                                + "ms avg.  (mem delta: " + ((Runtime.getRuntime().freeMemory() - mem) / 1024) + "kb) " + resultsToString(res));

                }
                catch (Exception e) {
                    if (!silent)
                        System.out.println("(MVEL)               : <<COULD NOT EXECUTE>>");
                }
            }

            synchronized (this) {
                mvelTotal += total;
            }

        }

        if ((testFlags & COMPILED) != 0) {
            runTestCompiled(name, test.getOgnlCompiled(), test.getMvelCompiled(), count, exFlags);
        }

        if (!silent)
            System.out.println("------------------------------------------------");
    }

    public void runTestCompiled(String name, Object compiledOgnl, Object compiledMvel, int count, int totest) throws Exception {

        long time;
        long mem;
        long total = 0;
        long[] res = new long[TESTITER];


        if (!silent)
            System.out.println("Compiled Results     :");


        if ((testFlags & RUN_OGNL) != 0 && ((totest & RUN_OGNL) != 0)) {
            try {
                //    compiled = Ognl.parseExpression(expression);
                for (int i = 0; i < count; i++) {
                    Ognl.getValue(compiledOgnl, baseClass);
                }

                System.gc();

                time = currentTimeMillis();
                mem = Runtime.getRuntime().freeMemory();

                for (int reps = 0; reps < TESTITER; reps++) {
                    for (int i = 0; i < count; i++) {
                        Ognl.getValue(compiledOgnl, baseClass);
                    }

                    if (reps == 0) res[0] = total += currentTimeMillis() - time;
                    else res[reps] = (total * -1) + (total += currentTimeMillis() - time - total);
                }

                if (!silent)
                    System.out.println("(OGNL Compiled)      : " + new BigDecimal(currentTimeMillis() - time).divide(new BigDecimal(TESTITER), 2, RoundingMode.HALF_UP)
                            + "ms avg.  (mem delta: " + ((Runtime.getRuntime().freeMemory() - mem) / 1024) + "kb) " + resultsToString(res));
            }
            catch (Exception e) {

                if (!silent)
                    System.out.println("(OGNL)               : <<COULD NOT EXECUTE>>");
            }
        }

        synchronized (this) {
            ognlTotal += total;
        }

        total = 0;

        if ((testFlags & RUN_MVEL) != 0 && ((totest & RUN_MVEL)) != 0) {

            try {
                //    compiled = MVEL.compileExpression(expression);
                for (int i = 0; i < count; i++) {
                    MVEL.executeExpression(compiledMvel, baseClass);
                }

                System.gc();

                time = currentTimeMillis();
                mem = Runtime.getRuntime().freeMemory();

                for (int reps = 0; reps < TESTITER; reps++) {
                    for (int i = 0; i < count; i++) {
                        MVEL.executeExpression(compiledMvel, baseClass);
                    }

                    if (reps == 0) res[0] = total += currentTimeMillis() - time;
                    else res[reps] = (total * -1) + (total += currentTimeMillis() - time - total);
                }

                if (!silent)
                    System.out.println("(MVEL Compiled)      : " + new BigDecimal(currentTimeMillis() - time).divide(new BigDecimal(TESTITER), 2, RoundingMode.HALF_UP)
                            + "ms avg.  (mem delta: " + ((Runtime.getRuntime().freeMemory() - mem) / 1024) + "kb) " + resultsToString(res));
            }
            catch (Exception e) {

                if (!silent)
                    System.out.println("(MVEL)               : <<COULD NOT EXECUTE>>");
            }


            synchronized (this) {
                mvelTotal += total;
            }
        }
    }

    private static String resultsToString(long[] res) {
        StringBuffer sbuf = new StringBuffer("[");
        for (int i = 0; i < res.length; i++) {
            sbuf.append(res[i]);

            if ((i + 1) < res.length) sbuf.append(",");
        }
        sbuf.append("]");

        return sbuf.toString();
    }


    public long getOgnlTotal() {
        return ognlTotal;
    }

    public void setOgnlTotal(long ognlTotal) {
        this.ognlTotal = ognlTotal;
    }

    public long getMvelTotal() {
        return mvelTotal;
    }

    public void setMvelTotal(long mvelTotal) {
        this.mvelTotal = mvelTotal;
    }


    public boolean isSilent() {
        return silent;
    }

    public void setSilent(boolean silent) {
        this.silent = silent;
    }
}
