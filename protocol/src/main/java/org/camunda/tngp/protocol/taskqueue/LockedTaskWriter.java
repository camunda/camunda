package org.camunda.tngp.protocol.taskqueue;

import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.util.buffer.BufferWriter;

/**
 * Note: this does not write an entire sbe message but rather one instance of
 * the <code>lockedTask</code> type without any header, etc.
 */
public class LockedTaskWriter implements BufferWriter
{

    protected LockedTaskEncoder lockedTaskEncoder = new LockedTaskEncoder();

    protected long id;
    protected long lockTime;
    protected long workflowInstanceId;

    public LockedTaskWriter()
    {
        reset();
    }

    public LockedTaskWriter id(long id)
    {
        this.id = id;
        return this;
    }

    public LockedTaskWriter lockTime(long lockTime)
    {
        this.lockTime = lockTime;
        return this;
    }

    public LockedTaskWriter workflowInstanceId(long workflowInstanceId)
    {
        this.workflowInstanceId = workflowInstanceId;
        return this;
    }

    @Override
    public int getLength()
    {
        return LockedTaskEncoder.ENCODED_LENGTH;
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        lockedTaskEncoder.wrap(buffer, offset)
            .id(id)
            .lockTime(lockTime)
            .wfInstanceId(workflowInstanceId);

        reset();
    }

    protected void reset()
    {
        id = LockedTaskEncoder.idNullValue();
        lockTime = LockedTaskEncoder.lockTimeNullValue();
        workflowInstanceId = LockedTaskEncoder.wfInstanceIdNullValue();
    }

}
