package org.camunda.tngp.protocol.taskqueue;

import org.agrona.BitUtil;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.util.buffer.BufferWriter;

public class SubscribedTaskWriter implements BufferWriter
{
    // the offset of the lockedTask field in the message body
    // Must be manually kept in sync since SBE does not offer this as a constant
    public static final int LOCKED_TASK_OFFSET = BitUtil.SIZE_OF_LONG;

    protected MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected SubscribedTaskEncoder bodyEncoder = new SubscribedTaskEncoder();

    protected long subscriptionId;

    protected LockedTaskWriter taskWriter;

    public SubscribedTaskWriter subscriptionId(long subscriptionId)
    {
        this.subscriptionId = subscriptionId;
        return this;
    }

    public SubscribedTaskWriter task(LockedTaskWriter taskWriter)
    {
        this.taskWriter = taskWriter;
        return this;
    }

    protected void reset()
    {
        this.taskWriter = null;
    }

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH + LOCKED_TASK_OFFSET + taskWriter.getLength();
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        if (taskWriter == null)
        {
            throw new RuntimeException("No task writer set");
        }

        headerEncoder.wrap(buffer, offset)
            .blockLength(bodyEncoder.sbeBlockLength())
            .schemaId(bodyEncoder.sbeSchemaId())
            .templateId(bodyEncoder.sbeTemplateId())
            .version(bodyEncoder.sbeSchemaVersion());

        offset += headerEncoder.encodedLength();
        bodyEncoder.wrap(buffer, offset)
            .subscriptionId(subscriptionId);

        offset += LOCKED_TASK_OFFSET;
        taskWriter.write(buffer, offset);

        reset();
    }

}
