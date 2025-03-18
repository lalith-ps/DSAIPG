package com.phasmidsoftware.dsaipg.sort.par;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.*;
import java.util.concurrent.ForkJoinPool;

public class Main {

    public static void main(String[] args) {
        processArgs(args);

        // Experiment parameters
        int[] arraySizes = {2000000, 4000000, 8000000};
        double[] percentageValues = {0.05, 0.10, 0.15, 0.20, 0.25, 0.30, 0.35, 0.40, 0.45,
                0.50, 0.55, 0.60, 0.65, 0.70, 0.75, 0.80, 0.85, 0.90, 0.95};
        int[] threadCounts = {2, 4, 8, 16};
        int numberOfRuns = 5;

        Random random = new Random();
        ArrayList<String> resultLines = new ArrayList<>();
        resultLines.add("ArraySize,Cutoff,ThreadCount,AverageTime(ms)");

        for (int arraySize : arraySizes) {
            System.out.println("\nTesting Array Size: " + arraySize);

            int[] cutoffValues = new int[percentageValues.length];
            for (int i = 0; i < percentageValues.length; i++) {
                cutoffValues[i] = (int) (arraySize * percentageValues[i]);
            }

            int[] array = new int[arraySize];

            for (int threadCount : threadCounts) {
                ForkJoinPool pool = new ForkJoinPool(threadCount);
                ParSort.setPool(pool); // <-- Set pool once per thread count
                System.out.println("  Thread Count: " + threadCount);

                for (int cutoff : cutoffValues) {
                    ParSort.cutoff = cutoff;
                    long totalTime = 0;

                    for (int t = 0; t < numberOfRuns; t++) {
                        for (int i = 0; i < array.length; i++) {
                            array[i] = random.nextInt(10000000);
                        }

                        long startTime = System.currentTimeMillis();
                        ParSort.sort(array, 0, array.length); // No need to pass pool
                        long endTime = System.currentTimeMillis();
                        totalTime += (endTime - startTime);
                    }

                    long avgTime = totalTime / numberOfRuns;
                    System.out.println("    Cutoff: " + cutoff + ", Avg Time: " + avgTime + " ms");
                    resultLines.add(arraySize + "," + cutoff + "," + threadCount + "," + avgTime);
                }
                pool.shutdown();
            }
        }

        // Write results
        try {
            FileOutputStream fis = new FileOutputStream("./src/dynamic_result.csv");
            OutputStreamWriter isr = new OutputStreamWriter(fis);
            BufferedWriter bw = new BufferedWriter(isr);
            for (String line : resultLines) {
                bw.write(line);
                bw.newLine();
            }
            bw.flush();
            bw.close();
            System.out.println("\nAll results written to dynamic_result.csv");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Args processing (unchanged)
    private static void processArgs(String[] args) {
        String[] xs = args;
        while (xs.length > 0)
            if (xs[0].startsWith("-")) xs = processArg(xs);
    }

    private static String[] processArg(String[] xs) {
        String[] result = new String[0];
        System.arraycopy(xs, 2, result, 0, xs.length - 2);
        processCommand(xs[0], xs[1]);
        return result;
    }

    private static void processCommand(String x, String y) {
        if (x.equalsIgnoreCase("N")) setConfig(x, Integer.parseInt(y));
        else if (x.equalsIgnoreCase("P")) ForkJoinPool.getCommonPoolParallelism();
    }

    private static void setConfig(String x, int i) {
        configuration.put(x, i);
    }

    private static final Map<String, Integer> configuration = new HashMap<>();
}