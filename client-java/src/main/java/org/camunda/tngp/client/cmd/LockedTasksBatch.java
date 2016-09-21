package org.camunda.tngp.client.cmd;

import java.util.Date;
import java.util.List;

public interface LockedTasksBatch
{
    Date getLockTime();

    List<LockedTask> getLockedTasks();
}
