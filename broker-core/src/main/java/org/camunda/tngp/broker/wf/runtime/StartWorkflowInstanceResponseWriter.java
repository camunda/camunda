package org.camunda.tngp.broker.wf.runtime;

import org.camunda.tngp.protocol.wf.MessageHeaderEncoder;
import org.camunda.tngp.protocol.wf.StartWorkflowInstanceResponseEncoder;
import org.camunda.tngp.util.buffer.BufferWriter;

import org.agrona.MutableDirectBuffer;

public class StartWorkflowInstanceResponseWriter implements BufferWriter
{
    protected MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected StartWorkflowInstanceResponseEncoder bodyEncoder = new StartWorkflowInstanceResponseEncoder();

    protected long id;

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH + StartWorkflowInstanceResponseEncoder.BLOCK_LENGTH;
    }
    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset)
            .blockLength(bodyEncoder.sbeBlockLength())
            .templateId(bodyEncoder.sbeTemplateId())
            .schemaId(bodyEncoder.sbeSchemaId())
            .version(bodyEncoder.sbeSchemaVersion())
            // TODO: setters for resource id and shard id
            .resourceId(0)
            .shardId(0);

        offset += headerEncoder.encodedLength();

        bodyEncoder.wrap(buffer, offset)
            .id(id);
    }

    public StartWorkflowInstanceResponseWriter id(long processInstanceId)
    {
        this.id = processInstanceId;
        return this;
    }
}
