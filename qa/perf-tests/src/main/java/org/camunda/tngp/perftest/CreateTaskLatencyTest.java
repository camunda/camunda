package org.camunda.tngp.perftest;

import static org.camunda.tngp.perftest.CommonProperties.DEFAULT_PARTITION_ID;
import static org.camunda.tngp.perftest.CommonProperties.DEFAULT_TOPIC_NAME;

import java.util.Properties;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.camunda.tngp.client.TaskTopicClient;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.perftest.helper.FixedRateLatencyTest;


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
    protected Supplier<Future> requestFn(TngpClient client)
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
