package org.camunda.tngp.broker.wf.repository.log;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.log.LogEntryWriter;
import org.camunda.tngp.taskqueue.data.MessageHeaderEncoder;
import org.camunda.tngp.taskqueue.data.WfDefinitionEncoder;

public class WfDefinitionWriter extends LogEntryWriter<WfDefinitionWriter, WfDefinitionEncoder>
{

    protected long id;

    protected final UnsafeBuffer typeKeyBuffer = new UnsafeBuffer(0, 0);
    protected final UnsafeBuffer resourceBuffer = new UnsafeBuffer(0, 0);

    public WfDefinitionWriter()
    {
        super(new WfDefinitionEncoder());
    }

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH +
               WfDefinitionEncoder.BLOCK_LENGTH +
               WfDefinitionEncoder.keyHeaderLength() +
               typeKeyBuffer.capacity() +
               WfDefinitionEncoder.resourceHeaderLength() +
               resourceBuffer.capacity();
    }

    @Override
    protected int getBodyLength()
    {
        return WfDefinitionEncoder.BLOCK_LENGTH +
                WfDefinitionEncoder.keyHeaderLength() +
                typeKeyBuffer.capacity() +
                WfDefinitionEncoder.resourceHeaderLength() +
                resourceBuffer.capacity();
    }

    @Override
    protected void writeBody(MutableDirectBuffer buffer, int offset)
    {
        bodyEncoder.wrap(buffer, offset)
            .id(id)
            .putKey(typeKeyBuffer, 0, typeKeyBuffer.capacity())
            .putResource(resourceBuffer, 0, resourceBuffer.capacity());

        typeKeyBuffer.wrap(0, 0);
        resourceBuffer.wrap(0, 0);
    }

    public WfDefinitionWriter id(final long value)
    {
        this.id = value;
        return this;
    }

    public WfDefinitionWriter wfDefinitionKey(final byte[] bytes)
    {
        typeKeyBuffer.wrap(bytes);
        return this;
    }

    public WfDefinitionWriter resource(final DirectBuffer buffer, final int offset, final int length)
    {
        resourceBuffer.wrap(buffer, offset, length);
        return this;
    }
}
