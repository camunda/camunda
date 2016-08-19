package org.camunda.tngp.broker.wf.runtime.log.bpmn;

import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.broker.log.LogEntryWriter;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.taskqueue.data.BpmnFlowElementEventEncoder;

public class BpmnFlowElementEventWriter extends LogEntryWriter<BpmnFlowElementEventWriter, BpmnFlowElementEventEncoder>
{

    protected long key;
    protected long processId;
    protected long workflowInstanceId;
    protected ExecutionEventType eventType;
    protected int flowElementId;

    public BpmnFlowElementEventWriter()
    {
        super(new BpmnFlowElementEventEncoder());
    }

    @Override
    protected int getBodyLength()
    {
        return BpmnFlowElementEventEncoder.BLOCK_LENGTH;
    }

    @Override
    protected void writeBody(MutableDirectBuffer buffer, int offset)
    {
        bodyEncoder.wrap(buffer, offset)
            .key(key)
            .wfDefinitionId(processId)
            .wfInstanceId(workflowInstanceId)
            .event(eventType.value())
            .flowElementId(flowElementId);

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
}
