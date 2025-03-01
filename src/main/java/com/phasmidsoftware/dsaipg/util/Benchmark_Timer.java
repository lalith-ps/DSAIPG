/*
 * Copyright (c) 2018-2024. Robin Hillyard
 */

package com.phasmidsoftware.dsaipg.util;
import com.phasmidsoftware.dsaipg.adt.pq.PriorityQueue;
import com.phasmidsoftware.dsaipg.adt.pq.FourAryHeap;
import com.phasmidsoftware.dsaipg.adt.pq.FibonacciHeap;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import com.phasmidsoftware.dsaipg.adt.pq.PQException;
import static com.phasmidsoftware.dsaipg.util.Utilities.formatWhole;

/**
 * This class implements a simple Benchmark utility for measuring the running time of algorithms.
 * It is part of the repository for the INFO6205 class, taught by Prof. Robin Hillyard.
 *
 * <p>
 * In general, the benchmark class handles three phases of a "run:"
 * <ol>
 *     <li>The pre-function which prepares the input to the study function (field fPre) (may be null);</li>
 *     <li>The study function itself (field fRun) -- assumed to be a mutating function since it does not return a result;</li>
 *     <li>The post-function which cleans up and/or checks the results of the study function (field fPost) (may be null).</li>
 * </ol>
 * </p>
 *
 * @param <T> The generic type T is that of the input to the function f which you will pass into the constructor.
 */
public class Benchmark_Timer<T> implements Benchmark<T> {

    /**
     * Calculate the appropriate number of warmup runs.
     *
     * @param m the number of runs.
     * @return at least one and at most the lower of four or m/15.
     */
    static int getWarmupRuns(int m) {
        return Integer.max(1, Integer.min(3, m / 15));
    }

    /**
     * Run function f m times and return the average time in milliseconds.
     *
     * @param supplier a Supplier of a T
     * @param m        the number of times the function f will be called.
     * @return the average number of milliseconds taken for each run of function f.
     */
    public double runFromSupplier(Supplier<T> supplier, int m) {
        final Function<T, T> function = t -> {
            fRun.accept(t);
            return t;
        };

        new Timer().repeat(getWarmupRuns(m), true, supplier, function, fPre, null);

        return new Timer().repeat(m, false, supplier, function, fPre, fPost);
    }

    /**
     * Constructor for a Benchmark_Timer with the option of specifying all three functions.
     */
    public Benchmark_Timer(String description, UnaryOperator<T> fPre, Consumer<T> fRun, Consumer<T> fPost) {
        this.description = description;
        this.fPre = fPre;
        this.fRun = fRun;
        this.fPost = fPost;
    }

    /**
     * Constructor for a Benchmark_Timer with the option of specifying all three functions.
     *
     * @param description the description of the benchmark.
     * @param fPre        a function of T => T.
     *                    Function fPre is run before each invocation of fRun (but with the clock stopped).
     *                    The result of fPre (if any) is passed to fRun.
     * @param fRun        a Consumer function (i.e. a function of T => Void).
     *                    Function fRun is the function whose timing you want to measure. For example, you might create a function which sorts an array.
     */
    public Benchmark_Timer(String description, UnaryOperator<T> fPre, Consumer<T> fRun) {
        this(description, fPre, fRun, null);
    }

    /**
     * Constructor for a Benchmark_Timer with only fRun and fPost Consumer parameters.
     */
    public Benchmark_Timer(String description, Consumer<T> fRun, Consumer<T> fPost) {
        this(description, null, fRun, fPost);
    }

    /**
     * Constructor for a Benchmark_Timer where only the (timed) run function is specified.
     */
    public Benchmark_Timer(String description, Consumer<T> f) {
        this(description, null, f, null);
    }
    private final String description;
    private final UnaryOperator<T> fPre;
    private final Consumer<T> fRun;
    private final Consumer<T> fPost;
    private static final Random random = new Random();
    private static final int M = 4095; // Max heap size
    private static final int INSERTIONS = 64000;
    private static final int REMOVALS = 16000;
    private static final Map<String, Integer> highestSpilledMap = new HashMap<>();


    public static void main(String[] args) {
        System.out.println("\n--- Heap Benchmarking ---");
        List<Supplier<Object>> heapSuppliers = Arrays.asList(
                () -> new PriorityQueue<Integer>(M, true, Comparator.naturalOrder(), false),
                () -> new PriorityQueue<Integer>(M, true, Comparator.naturalOrder(), true),
                () -> new FourAryHeap<Integer>(M, Comparator.naturalOrder(), false, false),
                () -> new FourAryHeap<Integer>(M, Comparator.naturalOrder(), true, true),
                FibonacciHeap::new
        );

        List<String> heapNames = Arrays.asList(
                "BinaryHeap", "BinaryHeapFloyd", "4AryHeap", "4AryHeapFloyd", "FibonacciHeap"
        );

        List<Double> insertionTimes = new ArrayList<>();
        List<Double> removalTimes = new ArrayList<>();

        for (int i = 0; i < heapSuppliers.size(); i++) {
            String heapName = heapNames.get(i);
            Supplier<Object> heapSupplier = heapSuppliers.get(i);

            System.out.println("\nHeap: " + heapName);

            Benchmark_Timer<Object> insertionBenchmark = new Benchmark_Timer<>(
                    heapName + " Insertions",
                    heap -> benchmarkHeap(heapName, heapSupplier, INSERTIONS, 0)
            );
            double insertionTime = insertionBenchmark.runFromSupplier(heapSupplier, 10);
            insertionTimes.add(insertionTime);

            Benchmark_Timer<Object> removalBenchmark = new Benchmark_Timer<>(
                    heapName + " Removals",
                    heap -> benchmarkHeap(heapName, heapSupplier, 0, REMOVALS)
            );
            double removalTime = removalBenchmark.runFromSupplier(heapSupplier, 10);
            removalTimes.add(removalTime);

            System.out.printf("Time taken for Insertion: %.6f ms%n", insertionTime);
            System.out.printf("Time taken for Removal: %.6f ms%n", removalTime);
        }
        exportdataToCSV(heapNames, insertionTimes, removalTimes);
    }
    static void benchmarkHeap(String description, Supplier<Object> heapSupplier, int inserts, int removes) {
        Object heapInstance = heapSupplier.get();
        Integer highestSpilled = null;

        if (heapInstance instanceof PriorityQueue) {
            PriorityQueue<Integer> pq = (PriorityQueue<Integer>) heapInstance;
            List<Integer> spilledElements = new ArrayList<>();

            for (int i = 0; i < INSERTIONS; i++) {
                if (pq.size() >= 4095) {
                    try {
                        int spilledElement = pq.take();
                        spilledElements.add(spilledElement);
                        System.out.println("BinaryHeap - Spilled element added: " + spilledElement);
                    } catch (PQException e) {
                        System.err.println("Error in PriorityQueue during insert: " + e.getMessage());
                    }
                }
                pq.give(random.nextInt());
            }

            for (int i = 0; i < REMOVALS; i++) {
                if (!pq.isEmpty()) {
                    try {
                        pq.take();
                    } catch (PQException e) {
                        System.err.println("Error in PriorityQueue during removal: " + e.getMessage());
                    }
                }
            }

            if (!spilledElements.isEmpty()) {
                highestSpilled = Collections.max(spilledElements);
                System.out.println(description + " - Highest priority spilled element: " + highestSpilled);
            }

        } else if (heapInstance instanceof FourAryHeap) {
            FourAryHeap<Integer> faHeap = (FourAryHeap<Integer>) heapInstance;

            for (int i = 0; i < INSERTIONS; i++) faHeap.insert(random.nextInt());
            for (int i = 0; i < REMOVALS; i++) if (faHeap.size() > 0) faHeap.removeTop();

            highestSpilled = faHeap.getHighestPrioritySpilledElement();
            if (highestSpilled != null)
                System.out.println(description + " - Highest priority spilled element: " + highestSpilled);

        } else if (heapInstance instanceof FibonacciHeap) {
            FibonacciHeap<Integer> fibHeap = (FibonacciHeap<Integer>) heapInstance;

            for (int i = 0; i < INSERTIONS; i++) fibHeap.insert(random.nextInt());
            for (int i = 0; i < REMOVALS; i++) if (fibHeap.size() > 0) fibHeap.removeMin();

            highestSpilled = fibHeap.getHighestPrioritySpilledElement();
            if (highestSpilled != null)
                System.out.println(description + " - Highest priority spilled element: " + highestSpilled);
        }
        highestSpilledMap.put(description, highestSpilled);
    }
    private static void exportdataToCSV(List<String> heapNames, List<Double> insertionTimes, List<Double> removalTimes) {
        try (PrintWriter writer = new PrintWriter(new File("benchmark_timer_output.csv"))) {
            writer.println("HeapName,InsertionTime,RemovalTime,HighestPrioritySpilledElement");

            for (int i = 0; i < heapNames.size(); i++) {
                Integer highestSpilled = highestSpilledMap.get(heapNames.get(i));
                writer.printf("%s,%.6f,%.6f,%s%n",
                        heapNames.get(i),
                        insertionTimes.get(i),
                        removalTimes.get(i),
                        highestSpilled != null ? highestSpilled : "None");
            }
        } catch (FileNotFoundException e) {
            System.err.println("Error writing to CSV: " + e.getMessage());
        }
    }
}