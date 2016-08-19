package org.camunda.tngp.broker.wf.runtime.log;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.log.LogEntryWriter;
import org.camunda.tngp.taskqueue.data.WfDefinitionRequestType;
import org.camunda.tngp.taskqueue.data.WfDefinitionRuntimeRequestEncoder;

public class WfDefinitionRuntimeRequestWriter extends LogEntryWriter<WfDefinitionRuntimeRequestWriter, WfDefinitionRuntimeRequestEncoder>
{

    protected long id;
    protected WfDefinitionRequestType type;
    protected UnsafeBuffer keyBuffer = new UnsafeBuffer(0, 0);
    protected UnsafeBuffer resourceBuffer = new UnsafeBuffer(0, 0);

    public WfDefinitionRuntimeRequestWriter()
    {
        super(new WfDefinitionRuntimeRequestEncoder());
        reset();
    }

    protected void reset()
    {
        id = 0L;
        type = WfDefinitionRequestType.NULL_VAL;
        keyBuffer.wrap(0, 0);
        resourceBuffer.wrap(0, 0);
    }

    @Override
    protected int getBodyLength()
    {
        return WfDefinitionRuntimeRequestEncoder.BLOCK_LENGTH +
                WfDefinitionRuntimeRequestEncoder.keyHeaderLength() +
                keyBuffer.capacity() +
                WfDefinitionRuntimeRequestEncoder.resourceHeaderLength() +
                resourceBuffer.capacity();
    }

    @Override
    protected void writeBody(MutableDirectBuffer buffer, int offset)
    {
        bodyEncoder.wrap(buffer, offset)
            .id(id)
            .type(type)
            .putKey(keyBuffer, 0, keyBuffer.capacity())
            .putResource(resourceBuffer, 0, resourceBuffer.capacity());
    }

    public WfDefinitionRuntimeRequestWriter id(long id)
    {
        this.id = id;
        return this;
    }

    public WfDefinitionRuntimeRequestWriter type(WfDefinitionRequestType type)
    {
        this.type = type;
        return this;
    }

    public WfDefinitionRuntimeRequestWriter key(DirectBuffer buffer, int offset, int length)
    {
        this.keyBuffer.wrap(buffer, offset, length);
        return this;
    }

    public WfDefinitionRuntimeRequestWriter resource(DirectBuffer buffer, int offset, int length)
    {
        resourceBuffer.wrap(buffer, offset, length);
        return this;
    }

}
