package org.camunda.tngp.client.cmd;

import java.time.Instant;
import java.util.List;

public interface LockedTasksBatch
{
    Instant getLockTime();

    List<LockedTask> getLockedTasks();
}
