package org.camunda.tngp.taskqueue;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.camunda.tngp.client.AsyncTaskService;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.cmd.LockedTask;
import org.camunda.tngp.client.cmd.LockedTasksBatch;

import static org.camunda.tngp.client.ClientProperties.*;

public class CreateLockCompleteSingleTask
{

    public static void main(final String[] args)
    {
        final Properties properties = new Properties();

        properties.put(BROKER_CONTACTPOINT, "127.0.0.1:51015");

        try (TngpClient client = TngpClient.create(properties))
        {
            client.connect();

            final AsyncTaskService asyncTaskService = client.tasks();

            Long taskId = asyncTaskService.create()
                .taskType("create-booking")
                .payload("hello")
                .execute();

            System.out.println("created task with id " + taskId);

            final LockedTasksBatch lockedTaskBatch = asyncTaskService.pollAndLock()
                .taskType("create-booking")
                .lockTime(1, TimeUnit.MINUTES)
                .maxTasks(1)
                .execute();

            final LockedTask lockedTask = lockedTaskBatch.getLockedTasks().get(0);
            System.out.println("locked task with id " + lockedTask.getId() + " and payload " + lockedTask.getPayloadString());

            taskId = asyncTaskService.complete()
                .taskId(lockedTask.getId())
                .payload("goodbye")
                .execute();
            System.out.println("Completed task with id " + taskId);

        }

    }

}
