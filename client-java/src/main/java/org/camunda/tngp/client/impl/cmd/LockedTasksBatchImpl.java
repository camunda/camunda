package org.camunda.tngp.client.impl.cmd;

import java.util.ArrayList;
import java.util.List;

import org.camunda.tngp.client.cmd.LockedTask;
import org.camunda.tngp.client.cmd.LockedTasksBatch;

public class LockedTasksBatchImpl implements LockedTasksBatch
{

    protected final List<LockedTask> lockedTasks;

    public LockedTasksBatchImpl()
    {
        lockedTasks = new ArrayList<>();
    }

    @Override
    public List<LockedTask> getLockedTasks()
    {
        return lockedTasks;
    }

    public void addTask(LockedTaskImpl lockedTask)
    {
        lockedTasks.add(lockedTask);
    }

}
