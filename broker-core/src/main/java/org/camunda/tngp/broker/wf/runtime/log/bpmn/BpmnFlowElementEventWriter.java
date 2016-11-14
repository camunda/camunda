package org.camunda.tngp.broker.wf.runtime.log.bpmn;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.log.LogEntryWriter;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.protocol.log.BpmnFlowElementEventEncoder;

public class BpmnFlowElementEventWriter extends LogEntryWriter<BpmnFlowElementEventWriter, BpmnFlowElementEventEncoder>
{

    protected long key;
    protected long processId;
    protected long workflowInstanceId;
    protected ExecutionEventType eventType;
    protected int flowElementId;

    protected final UnsafeBuffer flowElementIdStringBuffer = new UnsafeBuffer(0, 0);

    public BpmnFlowElementEventWriter()
    {
        super(new BpmnFlowElementEventEncoder());
    }

    @Override
    protected int getBodyLength()
    {
        return BpmnFlowElementEventEncoder.BLOCK_LENGTH + BpmnFlowElementEventEncoder.flowElementIdStringHeaderLength() + flowElementIdStringBuffer.capacity();
    }

    @Override
    protected void writeBody(MutableDirectBuffer buffer, int offset)
    {
        bodyEncoder.wrap(buffer, offset)
            .key(key)
            .wfDefinitionId(processId)
            .wfInstanceId(workflowInstanceId)
            .event(eventType.value())
            .flowElementId(flowElementId)
            .putFlowElementIdString(flowElementIdStringBuffer, 0, flowElementIdStringBuffer.capacity());
    }

    public BpmnFlowElementEventWriter key(long key)
    {
        this.key = key;
        return this;
    }

    public BpmnFlowElementEventWriter processId(long processId)
    {
        this.processId = processId;
        return this;
    }

    public BpmnFlowElementEventWriter workflowInstanceId(long processInstanceId)
    {
        this.workflowInstanceId = processInstanceId;
        return this;
    }

    public BpmnFlowElementEventWriter eventType(ExecutionEventType eventType)
    {
        this.eventType = eventType;
        return this;
    }

    public BpmnFlowElementEventWriter flowElementId(int flowElementId)
    {
        this.flowElementId = flowElementId;
        return this;
    }

    public BpmnFlowElementEventWriter flowElementIdString(DirectBuffer flowElementIdString, int offset, int length)
    {
        flowElementIdStringBuffer.wrap(flowElementIdString, offset, length);
        return this;
    }
}
