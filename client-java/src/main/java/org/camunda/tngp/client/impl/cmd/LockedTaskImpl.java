package org.camunda.tngp.client.impl.cmd;

import java.time.Instant;

import org.camunda.tngp.client.cmd.LockedTask;
import org.camunda.tngp.client.task.impl.PayloadField;

public class LockedTaskImpl implements LockedTask
{
    protected long id;
    protected Long workflowInstanceId;
    protected Instant lockTime;
    protected PayloadField payload = new PayloadField();

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
    public Long getWorkflowInstanceId()
    {
        return workflowInstanceId;
    }

    public void setWorkflowInstanceId(Long workflowInstanceId)
    {
        this.workflowInstanceId = workflowInstanceId;
    }

    @Override
    public String getPayloadString()
    {
        return payload.getJsonPayload();
    }

}
