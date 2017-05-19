package org.camunda.tngp.client.task;

import java.util.List;

public interface LockedTasksBatch
{
    List<LockedTask> getLockedTasks();
}
