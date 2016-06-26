package org.camunda.tngp.client.cmd;

import java.util.concurrent.TimeUnit;

import org.camunda.tngp.client.ClientCommand;
import org.camunda.tngp.client.impl.cmd.PollAndLockTasksCmdImpl;

public interface PollAndLockAsyncTasksCmd extends ClientCommand<LockedTasksBatch>
{
    PollAndLockAsyncTasksCmd taskQueueId(int taskQueueId);

    PollAndLockAsyncTasksCmd lockTime(long lockTimeMs);

    PollAndLockAsyncTasksCmd lockTime(long lockTime, TimeUnit timeUnit);

    PollAndLockAsyncTasksCmd maxTasks(int maxTasks);

    PollAndLockTasksCmdImpl taskType(String taskType);

}
