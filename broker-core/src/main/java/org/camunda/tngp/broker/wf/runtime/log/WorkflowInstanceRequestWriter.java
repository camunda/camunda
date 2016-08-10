package org.camunda.tngp.broker.wf.runtime.log;

import org.camunda.tngp.broker.log.LogEntryHeaderReader.EventSource;
import org.camunda.tngp.taskqueue.data.MessageHeaderEncoder;
import org.camunda.tngp.taskqueue.data.ProcessInstanceRequestType;
import org.camunda.tngp.taskqueue.data.WorkflowInstanceRequestEncoder;
import org.camunda.tngp.util.buffer.BufferWriter;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class WorkflowInstanceRequestWriter implements BufferWriter
{

    protected MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected WorkflowInstanceRequestEncoder bodyEncoder = new WorkflowInstanceRequestEncoder();

    protected ProcessInstanceRequestType type;
    protected long wfDefinitionId;
    protected EventSource source;

    protected UnsafeBuffer wfDefinitionKeyBuffer = new UnsafeBuffer(0, 0);

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH +
                WorkflowInstanceRequestEncoder.BLOCK_LENGTH +
                WorkflowInstanceRequestEncoder.wfDefinitionKeyHeaderLength() +
                wfDefinitionKeyBuffer.capacity();
    }

    public WorkflowInstanceRequestWriter type(ProcessInstanceRequestType type)
    {
        this.type = type;
        return this;
    }

    public WorkflowInstanceRequestWriter wfDefinitionId(long wfDefinitionId)
    {
        this.wfDefinitionId = wfDefinitionId;
        return this;

    }

    public WorkflowInstanceRequestWriter wfDefinitionKey(DirectBuffer buffer, int offset, int length)
    {
        this.wfDefinitionKeyBuffer.wrap(buffer, offset, length);
        return this;
    }

    public WorkflowInstanceRequestWriter source(EventSource source)
    {
        this.source = source;
        return this;
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
            .wfDefinitionId(wfDefinitionId)
            .putWfDefinitionKey(wfDefinitionKeyBuffer, 0, wfDefinitionKeyBuffer.capacity());
    }
}
