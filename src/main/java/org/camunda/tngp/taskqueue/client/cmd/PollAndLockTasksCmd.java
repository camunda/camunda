package org.camunda.tngp.taskqueue.client.cmd;

import java.util.concurrent.TimeUnit;

import org.camunda.tngp.taskqueue.client.ClientCommand;
import org.camunda.tngp.taskqueue.impl.cmd.PollAndLockTasksCmdImpl;

public interface PollAndLockTasksCmd extends ClientCommand<LockedTasksBatch>, RecyclableCmd
{
    PollAndLockTasksCmd lockTime(long lockTimeMs);

    PollAndLockTasksCmd lockTime(long lockTime, TimeUnit timeUnit);

    PollAndLockTasksCmd maxTasks(int maxTasks);

    PollAndLockTasksCmdImpl taskType(String taskType);

}
