package org.camunda.tngp.perftest.helper;

import static org.camunda.tngp.client.ClientProperties.CLIENT_MAXREQUESTS;
import static org.camunda.tngp.perftest.helper.TestHelper.printProperties;

import java.util.Properties;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.HdrHistogram.Histogram;
import org.camunda.tngp.client.ClientProperties;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;

public abstract class FixedRateLatencyTest
{
    public static final String TEST_WARMUP_TIMEMS = "test.warmup.timems";
    public static final String TEST_WARMUP_REQUESTRATE = "test.warmup.requestRate";
    public static final String TEST_TIMEMS = "test.timems";
    public static final String TEST_REQUESTRATE = "test.requestRate";
    public static final String TEST_OUTPUT_FILE_NAME = "test.outputFileName";

    public void run()
    {
        final Properties properties = System.getProperties();

        setDefaultProperties(properties);
        ClientProperties.setDefaults(properties);

        printProperties(properties);

        TngpClient client = null;

        try
        {
            client = TngpClient.create(properties);
            client.connect();

            executeSetup(properties, client);
            executeWarmup(properties, client);
            executeTest(properties, client);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            client.close();
        }

    }

    protected void setDefaultProperties(final Properties properties)
    {
        properties.putIfAbsent(TEST_WARMUP_TIMEMS, "30000");
        properties.putIfAbsent(TEST_WARMUP_REQUESTRATE, "2500");
        properties.putIfAbsent(TEST_TIMEMS, "30000");
        properties.putIfAbsent(TEST_REQUESTRATE, "5000");
        properties.putIfAbsent(TEST_OUTPUT_FILE_NAME, "data/output.txt");
        properties.putIfAbsent(CLIENT_MAXREQUESTS, "2048");
    }

    protected void executeSetup(Properties properties, TngpClient client)
    {
        // noop
    }

    @SuppressWarnings("rawtypes")
    protected void executeWarmup(Properties properties, TngpClient client)
    {
        try (final TransportConnection conection = client.getConnectionPool().openConnection())
        {
            System.out.format("Executing warmup\n");

            final int warmupRequestRate = Integer.parseInt(properties.getProperty(TEST_WARMUP_REQUESTRATE));
            final int warmupTimeMs = Integer.parseInt(properties.getProperty(TEST_WARMUP_TIMEMS));

            final Consumer<Long> noopLatencyConsumer = (latency) ->
            {
                // ignore
            };

            final Supplier<Future> requestFn = requestFn(client, conection);

            final int errors = TestHelper.executeAtFixedRate(requestFn, noopLatencyConsumer, warmupRequestRate, warmupTimeMs);

            System.out.format("Finished warmup. Errors (failed to send request due to backpressure): %d\n", errors);
        }

        TestHelper.gc();
    }

    @SuppressWarnings("rawtypes")
    protected void executeTest(Properties properties, TngpClient client)
    {
        try (final TransportConnection conection = client.getConnectionPool().openConnection())
        {
            System.out.format("Executing test\n");

            final int requestRate = Integer.parseInt(properties.getProperty(TEST_REQUESTRATE));
            final int timeMs = Integer.parseInt(properties.getProperty(TEST_TIMEMS));

            final Histogram histogram = new Histogram(TimeUnit.SECONDS.toNanos(10), 3);

            final Consumer<Long> noopLatencyConsumer = (latency) ->
            {
                histogram.recordValue(latency);
            };

            final Supplier<Future> requestFn = requestFn(client, conection);

            final int errors = TestHelper.executeAtFixedRate(requestFn, noopLatencyConsumer, requestRate, timeMs);

            System.out.format("Finished test. Errors (failed to send request due to backpressure): %d\n", errors);

            final String outputFileName = properties.getProperty(TEST_OUTPUT_FILE_NAME);
            TestHelper.recordHistogram(histogram, outputFileName);
        }

        TestHelper.gc();
    }

    protected abstract Supplier<Future> requestFn(TngpClient client, TransportConnection conection);
}
