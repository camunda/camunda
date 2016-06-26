package org.camunda.tngp.client.cmd;

import java.util.List;

public interface LockedTasksBatch
{
    long getLockTime();

    List<LockedTask> getLockedTasks();
}
