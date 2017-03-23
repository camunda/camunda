package org.camunda.tngp.client.impl.cmd;

import java.time.Instant;

import org.camunda.tngp.client.cmd.LockedTask;
import org.camunda.tngp.client.task.impl.MsgPackField;

public class LockedTaskImpl implements LockedTask
{
    protected long id;
    protected Long workflowInstanceKey;
    protected Instant lockTime;
    protected MsgPackField payload = new MsgPackField();

    public void setId(long taskId)
    {
        this.id = taskId;
    }

    @Override
    public long getId()
    {
        return id;
    }

    public void setLockTime(Instant lockTime)
    {
        this.lockTime = lockTime;
    }

    @Override
    public Instant getLockTime()
    {
        return lockTime;
    }

    @Override
    public Long getWorkflowInstanceKey()
    {
        return workflowInstanceKey;
    }

    public void setWorkflowInstanceKey(Long workflowInstanceKey)
    {
        this.workflowInstanceKey = workflowInstanceKey;
    }

    @Override
    public String getPayloadString()
    {
        return payload.getAsJson();
    }

}
