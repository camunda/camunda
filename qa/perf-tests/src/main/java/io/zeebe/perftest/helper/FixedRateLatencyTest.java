package io.zeebe.perftest.helper;

import static io.zeebe.client.ClientProperties.CLIENT_MAXREQUESTS;
import static io.zeebe.perftest.helper.TestHelper.printProperties;

import java.util.Properties;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.HdrHistogram.Histogram;
import io.zeebe.client.ClientProperties;
import io.zeebe.client.ZeebeClient;
import io.zeebe.perftest.CommonProperties;
import io.zeebe.transport.requestresponse.client.TransportConnection;

public abstract class FixedRateLatencyTest
{
    public static final String TEST_WARMUP_TIMEMS = "test.warmup.timems";
    public static final String TEST_WARMUP_REQUESTRATE = "test.warmup.requestRate";
    public static final String TEST_REQUESTRATE = "test.requestRate";

    public void run()
    {
        final Properties properties = System.getProperties();

        setDefaultProperties(properties);
        ClientProperties.setDefaults(properties);

        printProperties(properties);

        ZeebeClient client = null;

        try
        {
            client = ZeebeClient.create(properties);
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
        properties.putIfAbsent(TEST_WARMUP_REQUESTRATE, "1000");
        properties.putIfAbsent(CommonProperties.TEST_TIMEMS, "30000");
        properties.putIfAbsent(TEST_REQUESTRATE, "5000");
        properties.putIfAbsent(CommonProperties.TEST_OUTPUT_FILE_NAME, "data/output.txt");
        properties.putIfAbsent(CLIENT_MAXREQUESTS, "2048");
    }

    protected void executeSetup(Properties properties, ZeebeClient client)
    {
        // noop
    }

    @SuppressWarnings("rawtypes")
    protected void executeWarmup(Properties properties, ZeebeClient client)
    {
        try (TransportConnection conection = client.getConnectionPool().openConnection())
        {
            System.out.format("Executing warmup\n");

            final int warmupRequestRate = Integer.parseInt(properties.getProperty(TEST_WARMUP_REQUESTRATE));
            final int warmupTimeMs = Integer.parseInt(properties.getProperty(TEST_WARMUP_TIMEMS));

            final Consumer<Long> noopLatencyConsumer = (latency) ->
            {
                // ignore
            };

            final Supplier<Future> requestFn = requestFn(client);

            TestHelper.executeAtFixedRate(requestFn, noopLatencyConsumer, warmupRequestRate, warmupTimeMs);

            System.out.println("Finished warmup.");
        }

        TestHelper.gc();
    }

    @SuppressWarnings("rawtypes")
    protected void executeTest(Properties properties, ZeebeClient client)
    {
        try (TransportConnection conection = client.getConnectionPool().openConnection())
        {
            System.out.format("Executing test\n");

            final int requestRate = Integer.parseInt(properties.getProperty(TEST_REQUESTRATE));
            final int timeMs = Integer.parseInt(properties.getProperty(CommonProperties.TEST_TIMEMS));

            final Histogram histogram = new Histogram(TimeUnit.SECONDS.toNanos(10), 3);

            final Consumer<Long> noopLatencyConsumer = (latency) ->
            {
                histogram.recordValue(latency);
            };

            final Supplier<Future> requestFn = requestFn(client);

            final int errors = TestHelper.executeAtFixedRate(requestFn, noopLatencyConsumer, requestRate, timeMs);

            System.out.format("Finished test. Errors (failed to send request due to backpressure): %d\n", errors);

            final String outputFileName = properties.getProperty(CommonProperties.TEST_OUTPUT_FILE_NAME);
            TestHelper.recordHistogram(histogram, outputFileName);
        }

        TestHelper.gc();
    }

    protected abstract Supplier<Future> requestFn(ZeebeClient client);
}
