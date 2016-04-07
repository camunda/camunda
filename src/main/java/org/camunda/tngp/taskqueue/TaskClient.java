package org.camunda.tngp.taskqueue;

import static org.camunda.tngp.taskqueue.client.ClientProperties.BROKER_CONTACTPOINT;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.camunda.tngp.taskqueue.client.TngpClient;
import org.camunda.tngp.taskqueue.client.cmd.LockedTask;
import org.camunda.tngp.taskqueue.client.cmd.LockedTasksBatch;

public class TaskClient
{

    public static void main(String[] args)
    {
        final Properties properties = new Properties();

        properties.put(BROKER_CONTACTPOINT, "127.0.0.1:8800");

        try(TngpClient client = TngpClient.create(properties))
        {
            client.connect();

            Long taskId = client.createAsyncTask()
                .taskType("create-booking")
                .payload("{}")
                .execute();
            System.out.println("created task with id "+taskId);

            LockedTasksBatch lockedTaskBatch = client.pollAndLockTasks()
                .taskType("create-booking")
                .lockTime(1, TimeUnit.MINUTES)
                .maxTasks(1)
                .execute();

            LockedTask lockedTask = lockedTaskBatch.getLockedTasks().get(0);
            System.out.println("locked task with id "+lockedTask.getId() + " and payload "+lockedTask.getPayloadString());

            taskId = client.completeTask()
                .taskId(lockedTask.getId())
                .payload("{\"completed\": true}")
                .execute();
            System.out.println("Completed task with id "+taskId);

        }

    }

}
