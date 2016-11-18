package org.camunda.tngp.broker.wf.runtime.log.bpmn;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.log.LogEntryWriter;
import org.camunda.tngp.protocol.log.BpmnBranchEventEncoder;
import org.camunda.tngp.protocol.log.BpmnBranchEventEncoder.DeltaEncoder;

public class BpmnBranchEventWriter extends LogEntryWriter<BpmnBranchEventWriter, BpmnBranchEventEncoder>
{

    protected long key;

    protected UnsafeBuffer materializedPayloadBuffer = new UnsafeBuffer(0, 0);

    public BpmnBranchEventWriter()
    {
        super(new BpmnBranchEventEncoder());
    }

    @Override
    protected void writeBody(MutableDirectBuffer buffer, int offset)
    {
        bodyEncoder.wrap(buffer, offset)
            .key(key);

        bodyEncoder.deltaCount(0);
        bodyEncoder.putMaterializedPayload(materializedPayloadBuffer, 0, materializedPayloadBuffer.capacity());
    }

    public BpmnBranchEventWriter materializedPayload(DirectBuffer payload, int offset, int length)
    {
        this.materializedPayloadBuffer.wrap(payload, offset, length);
        return this;
    }

    @Override
    protected int getBodyLength()
    {
        return bodyEncoder.sbeBlockLength() +
                DeltaEncoder.sbeHeaderSize() + // TODO: assuming 0 deltas for now
                BpmnBranchEventEncoder.materializedPayloadHeaderLength() +
                materializedPayloadBuffer.capacity();
    }

    public BpmnBranchEventWriter key(long key)
    {
        this.key = key;
        return this;
    }

}
