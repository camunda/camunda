package org.camunda.tngp.client.cmd;

import java.util.concurrent.TimeUnit;

import org.camunda.tngp.client.ClientCommand;

public interface PollAndLockAsyncTasksCmd extends ClientCommand<LockedTasksBatch>
{
    PollAndLockAsyncTasksCmd taskQueueId(int taskQueueId);

    PollAndLockAsyncTasksCmd lockTime(long lockTimeMs);

    PollAndLockAsyncTasksCmd lockTime(long lockTime, TimeUnit timeUnit);

    PollAndLockAsyncTasksCmd maxTasks(int maxTasks);

    PollAndLockAsyncTasksCmd taskType(String taskType);

}
