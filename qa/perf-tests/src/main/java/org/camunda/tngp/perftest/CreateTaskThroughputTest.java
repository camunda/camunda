package org.camunda.tngp.perftest;

import static org.camunda.tngp.perftest.CommonProperties.DEFAULT_PARTITION_ID;
import static org.camunda.tngp.perftest.CommonProperties.DEFAULT_TOPIC_NAME;

import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.camunda.tngp.client.TaskTopicClient;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.perftest.helper.MaxRateThroughputTest;


public class CreateTaskThroughputTest extends MaxRateThroughputTest
{
    private static final String TASK_TYPE = "example-task-type";

    public static void main(String[] args)
    {
        new CreateTaskThroughputTest().run();
    }

    @Override
    @SuppressWarnings("rawtypes")
    protected Supplier<Future> requestFn(TngpClient client)
    {
        final TaskTopicClient tasksClient = client.taskTopic(DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_ID);

        return () ->
        {
            return tasksClient.create()
                    .taskType(TASK_TYPE)
                    .executeAsync();
        };
    }

}
