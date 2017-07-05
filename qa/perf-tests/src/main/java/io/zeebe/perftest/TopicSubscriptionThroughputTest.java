package io.zeebe.perftest;

import static io.zeebe.client.ClientProperties.*;
import static io.zeebe.perftest.CommonProperties.*;
import static io.zeebe.perftest.helper.TestHelper.*;

import java.util.Properties;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

import io.zeebe.client.ClientProperties;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.event.TopicSubscription;
import io.zeebe.perftest.helper.TestHelper;
import io.zeebe.perftest.reporter.FileReportWriter;
import io.zeebe.perftest.reporter.RateReporter;

public class TopicSubscriptionThroughputTest
{
    public static final String TEST_NUM_TASKS = "test.tasks";
    public static final String TEST_SETUP_TIMEMS = "test.setup.timems";

    public static final String TASK_TYPE = "foo";
    public static final String SUBSCRIPTION_NAME = "bar";

    public static void main(String[] args)
    {
        new TopicSubscriptionThroughputTest().run();
    }

    public void run()
    {
        final Properties properties = System.getProperties();
        properties.putIfAbsent(CLIENT_MAXREQUESTS, "2048");
        properties.putIfAbsent(CLIENT_TASK_EXECUTION_THREADS, "8");
        properties.putIfAbsent(CLIENT_MAXCONNECTIONS, "16");
        properties.putIfAbsent(CLIENT_TOPIC_SUBSCRIPTION_PREFETCH_CAPACITY, "12000");
        ClientProperties.setDefaults(properties);
        setDefaultProperties(properties);

        printProperties(properties);

        ZeebeClient client = null;

        try
        {
            client = ZeebeClient.create(properties);
            client.connect();

            executeSetup(properties, client);
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

    protected void setDefaultProperties(Properties properties)
    {
        properties.putIfAbsent(CommonProperties.TEST_OUTPUT_FILE_NAME, "data/output.txt");
        properties.putIfAbsent(CommonProperties.TEST_TIMEMS, "30000");
        properties.putIfAbsent(TEST_NUM_TASKS, "150000");
        properties.putIfAbsent(TEST_SETUP_TIMEMS, "30000");
    }

    private void executeTest(Properties properties, ZeebeClient client) throws InterruptedException
    {

        final int testTimeMs = Integer.parseInt(properties.getProperty(CommonProperties.TEST_TIMEMS));
        final String outFile = properties.getProperty(CommonProperties.TEST_OUTPUT_FILE_NAME);

        final FileReportWriter fileReportWriter = new FileReportWriter();
        final RateReporter reporter = new RateReporter(1, TimeUnit.SECONDS, fileReportWriter);


        new Thread()
        {
            @Override
            public void run()
            {
                reporter.doReport();
            }

        }.start();

        final long testTimeNanos = TimeUnit.MILLISECONDS.toNanos(testTimeMs);

        final TopicSubscription subscription = client.topic(DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_ID)
            .newSubscription()
            .handler((m, e) -> reporter.increment())
            .name(SUBSCRIPTION_NAME)
            .startAtHeadOfTopic()
            .open();

        LockSupport.parkNanos(testTimeNanos);

        subscription.close();

        reporter.exit();

        fileReportWriter.writeToFile(outFile);
    }

    private void executeSetup(Properties properties, ZeebeClient client)
    {
        final int numTasks = Integer.parseInt(properties.getProperty(TEST_NUM_TASKS));
        final int setUpTimeMs = Integer.parseInt(properties.getProperty(TEST_SETUP_TIMEMS));

        final Supplier<Future> request = () -> client.taskTopic(DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_ID).create()
                .taskType(TASK_TYPE)
                .executeAsync();

        TestHelper.executeAtFixedRate(
            request,
            (l) ->
            { },
            numTasks / (int) TimeUnit.MILLISECONDS.toSeconds(setUpTimeMs),
            setUpTimeMs);
    }
}
