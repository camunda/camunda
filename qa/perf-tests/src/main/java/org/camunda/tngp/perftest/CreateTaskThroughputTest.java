package org.camunda.tngp.perftest;

import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.camunda.tngp.client.TaskTopicClient;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.perftest.helper.MaxRateThroughputTest;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;

public class CreateTaskThroughputTest extends MaxRateThroughputTest
{
    private static final String TASK_TYPE = "example-task-type";

    public static void main(String[] args)
    {
        new CreateTaskThroughputTest().run();
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
