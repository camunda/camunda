package org.camunda.tngp.protocol.taskqueue;

import static org.agrona.BitUtil.SIZE_OF_SHORT;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.util.buffer.BufferWriter;

/**
 * Note:
 * <ul>
 *   <li> this does not write a message header
 *   <li>This writer does not write exactly one sbe message. In fact, it writes a
 *     <code>lockedTask</code> type message, and then appends a payload buffer. In SBE, this
 *     cannot be expressed as a single composite
 */
public class LockedTaskWriter implements BufferWriter
{

    protected LockedTaskEncoder lockedTaskEncoder = new LockedTaskEncoder();

    protected long id;
    protected long lockTime;
    protected long workflowInstanceId;
    protected UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);

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

    public LockedTaskWriter payload(DirectBuffer payload, int offset, int length)
    {
        this.payloadBuffer.wrap(payload, offset, length);
        return this;
    }

    @Override
    public int getLength()
    {
        return LockedTaskEncoder.ENCODED_LENGTH +
                SIZE_OF_SHORT +
                payloadBuffer.capacity();
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        lockedTaskEncoder.wrap(buffer, offset)
            .id(id)
            .lockTime(lockTime)
            .wfInstanceId(workflowInstanceId);

        offset += lockedTaskEncoder.encodedLength();

        buffer.putShort(offset, (short) payloadBuffer.capacity());
        offset += SIZE_OF_SHORT;

        if (payloadBuffer.capacity() > 0)
        {
            buffer.putBytes(offset, payloadBuffer, 0, payloadBuffer.capacity());
        }

        reset();
    }

    protected void reset()
    {
        id = LockedTaskEncoder.idNullValue();
        lockTime = LockedTaskEncoder.lockTimeNullValue();
        workflowInstanceId = LockedTaskEncoder.wfInstanceIdNullValue();
        payloadBuffer.wrap(0, 0);
    }

}
