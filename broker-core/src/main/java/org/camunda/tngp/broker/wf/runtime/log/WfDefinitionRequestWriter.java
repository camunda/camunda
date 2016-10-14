package org.camunda.tngp.broker.wf.runtime.log;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.log.LogEntryWriter;
import org.camunda.tngp.protocol.log.WfDefinitionRequestEncoder;
import org.camunda.tngp.protocol.log.WfDefinitionRequestType;

public class WfDefinitionRequestWriter extends LogEntryWriter<WfDefinitionRequestWriter, WfDefinitionRequestEncoder>
{

    protected WfDefinitionRequestType type;
    protected UnsafeBuffer resourceBuffer = new UnsafeBuffer(0, 0);

    public WfDefinitionRequestWriter()
    {
        super(new WfDefinitionRequestEncoder());
    }

    @Override
    protected int getBodyLength()
    {
        return  WfDefinitionRequestEncoder.BLOCK_LENGTH +
                WfDefinitionRequestEncoder.resourceHeaderLength() +
                resourceBuffer.capacity();
    }

    @Override
    protected void writeBody(MutableDirectBuffer buffer, int offset)
    {
        bodyEncoder.wrap(buffer, offset)
            .type(type)
            .putResource(resourceBuffer, 0, resourceBuffer.capacity());
    }

    public WfDefinitionRequestWriter type(WfDefinitionRequestType type)
    {
        this.type = type;
        return this;
    }

    public WfDefinitionRequestWriter resource(DirectBuffer buffer, int offset, int length)
    {
        resourceBuffer.wrap(buffer, offset, length);
        return this;
    }

}
