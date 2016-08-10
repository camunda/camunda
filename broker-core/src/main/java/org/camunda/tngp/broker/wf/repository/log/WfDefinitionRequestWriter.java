package org.camunda.tngp.broker.wf.repository.log;

import org.camunda.tngp.broker.log.LogEntryHeaderReader.EventSource;
import org.camunda.tngp.taskqueue.data.MessageHeaderEncoder;
import org.camunda.tngp.taskqueue.data.WfDefinitionRequestEncoder;
import org.camunda.tngp.taskqueue.data.WfDefinitionRequestType;
import org.camunda.tngp.util.buffer.BufferWriter;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class WfDefinitionRequestWriter implements BufferWriter
{

    protected MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected WfDefinitionRequestEncoder bodyEncoder = new WfDefinitionRequestEncoder();

    protected EventSource source;
    protected WfDefinitionRequestType type;
    protected UnsafeBuffer resourceBuffer = new UnsafeBuffer(0, 0);

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH +
                WfDefinitionRequestEncoder.BLOCK_LENGTH +
                WfDefinitionRequestEncoder.resourceHeaderLength() +
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
            .source(source.value())
            .templateId(bodyEncoder.sbeTemplateId())
            .version(bodyEncoder.sbeSchemaVersion());

        bodyEncoder.wrap(buffer, offset + headerEncoder.encodedLength())
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

    public WfDefinitionRequestWriter source(EventSource source)
    {
        this.source = source;
        return this;
    }

}
