package org.camunda.tngp.client.task.cmd;

import java.time.Duration;

import org.camunda.tngp.client.cmd.ClientCommand;
import org.camunda.tngp.client.task.LockedTasksBatch;

public interface PollAndLockAsyncTasksCmd extends ClientCommand<LockedTasksBatch>
{
    PollAndLockAsyncTasksCmd lockTime(long lockTimeMs);

    PollAndLockAsyncTasksCmd lockTime(Duration timeDuration);

    PollAndLockAsyncTasksCmd maxTasks(int maxTasks);

    PollAndLockAsyncTasksCmd taskType(String taskType);

}
