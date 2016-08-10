package org.camunda.tngp.broker.wf.runtime.log;

import org.camunda.tngp.broker.log.LogEntryHeaderReader.EventSource;
import org.camunda.tngp.taskqueue.data.MessageHeaderEncoder;
import org.camunda.tngp.taskqueue.data.WfDefinitionRequestType;
import org.camunda.tngp.taskqueue.data.WfDefinitionRuntimeRequestEncoder;
import org.camunda.tngp.util.buffer.BufferWriter;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class WfDefinitionRuntimeRequestWriter implements BufferWriter
{

    protected MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected WfDefinitionRuntimeRequestEncoder bodyEncoder = new WfDefinitionRuntimeRequestEncoder();

    protected long id;
    protected WfDefinitionRequestType type;
    protected UnsafeBuffer keyBuffer = new UnsafeBuffer(0, 0);
    protected UnsafeBuffer resourceBuffer = new UnsafeBuffer(0, 0);

    public WfDefinitionRuntimeRequestWriter()
    {
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
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH +
                WfDefinitionRuntimeRequestEncoder.BLOCK_LENGTH +
                WfDefinitionRuntimeRequestEncoder.keyHeaderLength() +
                keyBuffer.capacity() +
                WfDefinitionRuntimeRequestEncoder.resourceHeaderLength() +
                resourceBuffer.capacity();
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset)
            .blockLength(bodyEncoder.sbeBlockLength())
            .resourceId(0)
            .schemaId(bodyEncoder.sbeSchemaId())
            .shardId(0)
            .source(EventSource.NULL_VAL.value())
            .templateId(bodyEncoder.sbeTemplateId())
            .version(bodyEncoder.sbeSchemaVersion());

        bodyEncoder.wrap(buffer, offset + headerEncoder.encodedLength())
            .id(id)
            .type(type)
            .putKey(keyBuffer, 0, keyBuffer.capacity())
            .putResource(resourceBuffer, 0, resourceBuffer.capacity());

        reset();
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
