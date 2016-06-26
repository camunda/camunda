package org.camunda.tngp.client.impl.cmd;

import java.util.ArrayList;
import java.util.List;

import org.camunda.tngp.client.cmd.LockedTask;
import org.camunda.tngp.client.cmd.LockedTasksBatch;

public class LockedTasksBatchImpl implements LockedTasksBatch
{

    protected final List<LockedTask> lockedTasks;
    protected long lockTime;

    public LockedTasksBatchImpl()
    {
        lockedTasks = new ArrayList<>();
    }

    @Override
    public List<LockedTask> getLockedTasks()
    {
        return lockedTasks;
    }

    public void setLockTime(long lockTime)
    {
        this.lockTime = lockTime;
    }

    @Override
    public long getLockTime()
    {
        return lockTime;
    }

    public void addTask(LockedTaskImpl lockedTask)
    {
        lockedTasks.add(lockedTask);
    }

}
