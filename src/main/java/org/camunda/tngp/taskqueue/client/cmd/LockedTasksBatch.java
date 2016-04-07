package org.camunda.tngp.taskqueue.client.cmd;

import java.util.List;

public interface LockedTasksBatch
{
    long getLockTime();

    List<LockedTask> getLockedTasks();
}
