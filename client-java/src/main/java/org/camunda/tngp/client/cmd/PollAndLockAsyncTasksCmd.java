package org.camunda.tngp.client.cmd;

import java.time.Duration;

import org.camunda.tngp.client.ClientCommand;

public interface PollAndLockAsyncTasksCmd extends ClientCommand<LockedTasksBatch>
{
    PollAndLockAsyncTasksCmd lockTime(long lockTimeMs);

    PollAndLockAsyncTasksCmd lockTime(Duration timeDuration);

    PollAndLockAsyncTasksCmd maxTasks(int maxTasks);

    PollAndLockAsyncTasksCmd taskType(String taskType);

}
