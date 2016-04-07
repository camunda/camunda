package org.camunda.tngp.taskqueue.impl.cmd;

import java.util.ArrayList;
import java.util.List;

import org.camunda.tngp.taskqueue.client.cmd.LockedTask;
import org.camunda.tngp.taskqueue.client.cmd.LockedTasksBatch;

public class LockedTasksBatchImpl implements LockedTasksBatch
{

    protected final List<LockedTask> lockedTasks;
    protected long lockTime;

    public LockedTasksBatchImpl(int maxTasks)
    {
        lockedTasks = new ArrayList<>(maxTasks);
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
