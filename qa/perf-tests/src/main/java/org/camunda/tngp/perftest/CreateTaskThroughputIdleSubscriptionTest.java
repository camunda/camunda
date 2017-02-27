package org.camunda.tngp.perftest;

import java.util.Properties;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.camunda.tngp.client.TaskTopicClient;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.perftest.helper.MaxRateThroughputTest;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;

public class CreateTaskThroughputIdleSubscriptionTest extends MaxRateThroughputTest
{
    private static final String TASK_TYPE = "example-task-type";

    public static void main(String[] args)
    {
        new CreateTaskThroughputIdleSubscriptionTest().run();
    }

    @Override
    protected void executeSetup(Properties properties, TngpClient client)
    {
        client.taskTopic().newSubscription()
            .topicId(0)
            .taskType("another" + TASK_TYPE)
            .handler((t) ->
            { })
            .lockTime(10000L)
            .open();
    }

    @Override
    @SuppressWarnings("rawtypes")
    protected Supplier<Future> requestFn(TngpClient client, TransportConnection connection)
    {
        final TaskTopicClient tasksClient = client.taskTopic();

        return () ->
        {
            return tasksClient.create()
                    .taskType(TASK_TYPE)
                    .executeAsync(connection);
        };
    }

}
