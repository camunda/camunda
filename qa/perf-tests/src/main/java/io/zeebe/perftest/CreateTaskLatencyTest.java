package io.zeebe.perftest;

import static io.zeebe.perftest.CommonProperties.DEFAULT_PARTITION_ID;
import static io.zeebe.perftest.CommonProperties.DEFAULT_TOPIC_NAME;

import java.util.Properties;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import io.zeebe.client.TaskTopicClient;
import io.zeebe.client.ZeebeClient;
import io.zeebe.perftest.helper.FixedRateLatencyTest;


public class CreateTaskLatencyTest extends FixedRateLatencyTest
{
    private static final String TASK_TYPE = "some-task-type";

    @Override
    protected void setDefaultProperties(Properties properties)
    {
        properties.putIfAbsent(TEST_REQUESTRATE, "50000");

        super.setDefaultProperties(properties);
    }

    @Override
    @SuppressWarnings("rawtypes")
    protected Supplier<Future> requestFn(ZeebeClient client)
    {
        final TaskTopicClient taskClient = client.taskTopic(DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_ID);

        return () ->
        {
            return taskClient.create()
                .taskType(TASK_TYPE)
                .executeAsync();
        };
    }

    public static void main(String[] args)
    {
        new CreateTaskLatencyTest().run();
    }
}
