package org.camunda.tngp.broker.wf.runtime;

import org.camunda.tngp.protocol.wf.MessageHeaderEncoder;
import org.camunda.tngp.protocol.wf.StartWorkflowInstanceResponseEncoder;
import org.camunda.tngp.util.buffer.BufferWriter;

import uk.co.real_logic.agrona.MutableDirectBuffer;

public class StartProcessInstanceResponseWriter implements BufferWriter
{

    protected MessageHeaderEncoder headerEncoder;
    protected StartWorkflowInstanceResponseEncoder bodyEncoder;

    protected long processInstanceId;

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH
                + StartWorkflowInstanceResponseEncoder.BLOCK_LENGTH;
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
            .wfInstanceId(processInstanceId);
    }

    public StartProcessInstanceResponseWriter processInstanceId(long processInstanceId)
    {
        this.processInstanceId = processInstanceId;
        return this;
    }
}
