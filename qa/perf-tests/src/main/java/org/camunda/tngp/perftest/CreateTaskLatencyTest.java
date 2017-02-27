package org.camunda.tngp.perftest;

import java.util.Properties;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.camunda.tngp.client.TaskTopicClient;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.perftest.helper.FixedRateLatencyTest;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;

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
    protected Supplier<Future> requestFn(TngpClient client, TransportConnection connection)
    {
        final TaskTopicClient taskClient = client.taskTopic();

        return () ->
        {
            return taskClient.create()
                .taskType(TASK_TYPE)
                .executeAsync(connection);
        };
    }

    public static void main(String[] args)
    {
        new CreateTaskLatencyTest().run();
    }
}
