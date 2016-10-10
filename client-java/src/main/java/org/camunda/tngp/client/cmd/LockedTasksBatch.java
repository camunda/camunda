package org.camunda.tngp.client.cmd;

import java.util.List;

public interface LockedTasksBatch
{
    List<LockedTask> getLockedTasks();
}
