package org.camunda.tngp.broker.wf.runtime.log;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.log.LogEntryWriter;
import org.camunda.tngp.protocol.log.ProcessInstanceRequestType;
import org.camunda.tngp.protocol.log.WorkflowInstanceRequestEncoder;

public class WorkflowInstanceRequestWriter extends LogEntryWriter<WorkflowInstanceRequestWriter, WorkflowInstanceRequestEncoder>
{
    protected ProcessInstanceRequestType type;
    protected long wfDefinitionId;

    protected UnsafeBuffer wfDefinitionKeyBuffer = new UnsafeBuffer(0, 0);
    protected UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);

    public WorkflowInstanceRequestWriter()
    {
        super(new WorkflowInstanceRequestEncoder());
    }

    @Override
    protected int getBodyLength()
    {
        return WorkflowInstanceRequestEncoder.BLOCK_LENGTH +
                WorkflowInstanceRequestEncoder.wfDefinitionKeyHeaderLength() +
                wfDefinitionKeyBuffer.capacity() +
                WorkflowInstanceRequestEncoder.payloadHeaderLength() +
                payloadBuffer.capacity();
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

    public WorkflowInstanceRequestWriter payload(DirectBuffer buffer, int offset, int length)
    {
        this.payloadBuffer.wrap(buffer, offset, length);
        return this;
    }

    @Override
    protected void writeBody(MutableDirectBuffer buffer, int offset)
    {
        bodyEncoder.wrap(buffer, offset)
            .type(type)
            .wfDefinitionId(wfDefinitionId)
            .putWfDefinitionKey(wfDefinitionKeyBuffer, 0, wfDefinitionKeyBuffer.capacity())
            .putPayload(payloadBuffer, 0, payloadBuffer.capacity());

    }

}
