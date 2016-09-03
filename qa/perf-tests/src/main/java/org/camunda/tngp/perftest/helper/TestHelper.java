package org.camunda.tngp.perftest.helper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.HdrHistogram.Histogram;
import org.agrona.IoUtil;
import org.camunda.tngp.perftest.reporter.RateReporter;


public class TestHelper
{

    public static void printProperties(Properties properties)
    {
        System.out.println("Client configuration:");

        final TreeMap<String, String> sortedProperties = new TreeMap<>();

        final Enumeration<?> propertyNames = properties.propertyNames();
        while (propertyNames.hasMoreElements())
        {
            final String key = (String) propertyNames.nextElement();
            final String value = properties.getProperty(key);
            if (key.startsWith("tngp") || key.startsWith("test"))
            {
                sortedProperties.put(key, value);
            }
        }

        for (Map.Entry<String, String> property : sortedProperties.entrySet())
        {
            System.out.println(String.format("%s: %s", property.getKey(), property.getValue()));
        }

    }

    @SuppressWarnings("rawtypes")
    public static void awaitAll(Collection<Future> inFlightRequests)
    {
        while (!inFlightRequests.isEmpty())
        {
            poll(inFlightRequests);
        }
    }

    @SuppressWarnings("rawtypes")
    public static void poll(Collection<Future> inFlightRequests)
    {
        final Iterator<Future> iterator = inFlightRequests.iterator();
        while (iterator.hasNext())
        {
            final Future<?> future = iterator.next();
            if (future.isDone())
            {
                try
                {
                    future.get();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
                finally
                {
                    iterator.remove();
                }
            }
        }
    }

    /**
     * Executes requests at a given rate.
     *
     * @param requestFn the function invoked to execute a request
     * @param latencyRecorder the consumer invoked to record the latency for a given request
     * @param requestRate the rate at which requests should be executed (provided in requests / second)
     * @param runtimeMs the time to run
     * @return an error count (number of times a request could not be executed at the given rate due to backpressure)
     */
    @SuppressWarnings("rawtypes")
    public static int executeAtFixedRate(Supplier<Future> requestFn, Consumer<Long> latencyRecorder, int requestRate, int runtimeMs)
    {
        final LinkedList<Future> responseFutures = new LinkedList<>();
        final LinkedList<Long> requestTimes = new LinkedList<>();

        final long delayNs = TimeUnit.SECONDS.toNanos(1) / requestRate;
        long nextRequestTime = 0;
        int errors = 0;

        long now = System.nanoTime();
        final long endTime = now + TimeUnit.MILLISECONDS.toNanos(runtimeMs);

        System.out.format("Executing requests at fixed rate: [rate=%dreq/s, runtime=%ds]\n", requestRate, TimeUnit.MILLISECONDS.toSeconds(runtimeMs));

        while ((now = System.nanoTime()) <= endTime)
        {
            final Iterator<Future> iterator = responseFutures.iterator();
            while (iterator.hasNext())
            {
                final Future future = iterator.next();

                if (future.isDone())
                {
                    try
                    {
                        future.get();
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }

                    final long requestTime = requestTimes.removeFirst();
                    final long latency = now - requestTime;

                    latencyRecorder.accept(latency);

                    iterator.remove();
                }
                else
                {
                    break;
                }

            }

            now = System.nanoTime();

            if (now >= nextRequestTime)
            {
                try
                {
                    requestTimes.add(now);

                    final Future responseFuture = requestFn.get();

                    responseFutures.add(responseFuture);
                }
                catch (Exception e)
                {
                    errors++;
                }
                nextRequestTime = now + delayNs;
            }
        }

        TestHelper.awaitAll(responseFutures);

        return errors;
    }

    @SuppressWarnings("rawtypes")
    public static void executeAtMaxRate(Supplier<Future> requestFn, RateReporter rateReporter, int runtimeMs, int maxConcurrentRequests)
    {
        final LinkedList<Future> inFlightRequests = new LinkedList<>();

        System.out.format("Executing requests at max rate: [runtime=%ds, maxConcurrentRequests=%d]\n", TimeUnit.MILLISECONDS.toSeconds(runtimeMs), maxConcurrentRequests);

        long now = System.nanoTime();
        final long endTime = now + TimeUnit.MILLISECONDS.toNanos(runtimeMs);

        while ((now = System.nanoTime()) <= endTime)
        {
            if (inFlightRequests.size() < maxConcurrentRequests)
            {
                inFlightRequests.add(requestFn.get());
                rateReporter.increment();
            }

            poll(inFlightRequests);
        }

        awaitAll(inFlightRequests);
    }

    public static void gc()
    {
        for (int i = 0; i < 5; i++)
        {
            System.gc();
        }
    }

    public static void recordHistogram(Histogram histogram, String outputFileName)
    {
        try
        {
            final File outputFile = new File(outputFileName);

            IoUtil.deleteIfExists(outputFile);

            outputFile.createNewFile();
            final FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
            final PrintStream printStream = new PrintStream(fileOutputStream);

            histogram.outputPercentileDistribution(printStream, 1000.0);

            printStream.flush();
            fileOutputStream.close();

            System.out.printf("Wrote histogram (percentile distribution) to output file %s\n", outputFile.getAbsolutePath());
        }
        catch (Exception e)
        {
            System.err.printf("Failed to write histogram to output file %s\n", outputFileName);
            e.printStackTrace();
        }
    }

}
