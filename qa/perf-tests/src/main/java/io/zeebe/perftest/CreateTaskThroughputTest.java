package io.zeebe.perftest;

import static io.zeebe.perftest.CommonProperties.DEFAULT_PARTITION_ID;
import static io.zeebe.perftest.CommonProperties.DEFAULT_TOPIC_NAME;

import java.util.concurrent.Future;
import java.util.function.Supplier;

import io.zeebe.client.TaskTopicClient;
import io.zeebe.client.ZeebeClient;
import io.zeebe.perftest.helper.MaxRateThroughputTest;


public class CreateTaskThroughputTest extends MaxRateThroughputTest
{
    private static final String TASK_TYPE = "example-task-type";

    public static void main(String[] args)
    {
        new CreateTaskThroughputTest().run();
    }

    @Override
    @SuppressWarnings("rawtypes")
    protected Supplier<Future> requestFn(ZeebeClient client)
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
