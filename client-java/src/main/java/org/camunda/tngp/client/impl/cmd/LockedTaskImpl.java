package org.camunda.tngp.client.impl.cmd;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.time.Instant;

import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.client.cmd.LockedTask;

public class LockedTaskImpl implements LockedTask
{
    protected long id;
    protected Long workflowInstanceId;
    protected Instant lockTime;

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

    public MutableDirectBuffer getPayloadBuffer()
    {
        return null;
    }

    @Override
    public int payloadLength()
    {
        return 0;
    }

    @Override
    public int putPayload(ByteBuffer buffer)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public int putPayload(MutableDirectBuffer buffer, int offset, int lenght)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public byte[] getPayloadBytes()
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String getPayloadString()
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public InputStream getPayloadStream()
    {
        throw new UnsupportedOperationException("Not implemented");
    }

}
