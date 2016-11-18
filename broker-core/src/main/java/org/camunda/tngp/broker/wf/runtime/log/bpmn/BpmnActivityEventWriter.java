package org.camunda.tngp.broker.wf.runtime.log.bpmn;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.log.LogEntryWriter;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.protocol.log.BpmnActivityEventEncoder;

public class BpmnActivityEventWriter extends LogEntryWriter<BpmnActivityEventWriter, BpmnActivityEventEncoder>
{

    protected long key;
    protected long wfDefinitionId;
    protected long wfInstanceId;
    protected ExecutionEventType eventType;
    protected int flowElementId;
    protected int taskQueueId;
    protected long bpmnBranchKey;

    protected final UnsafeBuffer taskTypeBuffer = new UnsafeBuffer(0, 0);
    protected final UnsafeBuffer flowElementIdStringBuffer = new UnsafeBuffer(0, 0);
    protected final UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);


    public BpmnActivityEventWriter()
    {
        super(new BpmnActivityEventEncoder());
    }

    @Override
    protected int getBodyLength()
    {
        return BpmnActivityEventEncoder.BLOCK_LENGTH +
                BpmnActivityEventEncoder.flowElementIdStringHeaderLength() +
                flowElementIdStringBuffer.capacity() +
                BpmnActivityEventEncoder.taskTypeHeaderLength() +
                taskTypeBuffer.capacity() +
                BpmnActivityEventEncoder.payloadHeaderLength() +
                payloadBuffer.capacity();
    }

    @Override
    protected void writeBody(MutableDirectBuffer buffer, int offset)
    {
        bodyEncoder.wrap(buffer, offset)
            .key(key)
            .wfDefinitionId(wfDefinitionId)
            .wfInstanceId(wfInstanceId)
            .event(eventType.value())
            .flowElementId(flowElementId)
            .taskQueueId(taskQueueId)
            .bpmnBranchKey(bpmnBranchKey)
            .putTaskType(taskTypeBuffer, 0, taskTypeBuffer.capacity())
            .putFlowElementIdString(flowElementIdStringBuffer, 0, flowElementIdStringBuffer.capacity())
            .putPayload(payloadBuffer, 0, payloadBuffer.capacity());
    }

    public BpmnActivityEventWriter key(long key)
    {
        this.key = key;
        return this;
    }

    public BpmnActivityEventWriter wfDefinitionId(long processId)
    {
        this.wfDefinitionId = processId;
        return this;
    }

    public BpmnActivityEventWriter wfInstanceId(long processInstanceId)
    {
        this.wfInstanceId = processInstanceId;
        return this;
    }

    public BpmnActivityEventWriter eventType(ExecutionEventType eventType)
    {
        this.eventType = eventType;
        return this;
    }

    public BpmnActivityEventWriter flowElementId(int flowElementId)
    {
        this.flowElementId = flowElementId;
        return this;
    }

    public BpmnActivityEventWriter taskQueueId(int taskQueueId)
    {
        this.taskQueueId = taskQueueId;
        return this;
    }

    public BpmnActivityEventWriter taskType(DirectBuffer taskType, int offset, int length)
    {
        taskTypeBuffer.wrap(taskType, offset, length);
        return this;
    }

    public BpmnActivityEventWriter flowElementIdString(DirectBuffer flowElementIdString, int offset, int length)
    {
        flowElementIdStringBuffer.wrap(flowElementIdString, offset, length);
        return this;
    }

    public BpmnActivityEventWriter payload(DirectBuffer payload, int offset, int length)
    {
        this.payloadBuffer.wrap(payload, offset, length);
        return this;
    }


    public BpmnActivityEventWriter bpmnBranchKey(long bpmnBranchKey)
    {
        this.bpmnBranchKey = bpmnBranchKey;
        return this;
    }

}
