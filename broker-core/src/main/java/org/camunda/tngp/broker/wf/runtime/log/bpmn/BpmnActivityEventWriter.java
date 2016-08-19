package org.camunda.tngp.broker.wf.runtime.log.bpmn;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.log.LogEntryWriter;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.taskqueue.data.BpmnActivityEventEncoder;

public class BpmnActivityEventWriter extends LogEntryWriter<BpmnActivityEventWriter, BpmnActivityEventEncoder>
{

    protected long key;
    protected long wfDefinitionId;
    protected long wfInstanceId;
    protected ExecutionEventType eventType;
    protected int flowElementId;
    protected int taskQueueId;

    protected final UnsafeBuffer taskTypeBuffer = new UnsafeBuffer(0, 0);

    public BpmnActivityEventWriter()
    {
        super(new BpmnActivityEventEncoder());
    }

    @Override
    protected int getBodyLength()
    {
        return BpmnActivityEventEncoder.BLOCK_LENGTH +
                BpmnActivityEventEncoder.taskTypeHeaderLength() +
                taskTypeBuffer.capacity();
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
            .putTaskType(taskTypeBuffer, 0, taskTypeBuffer.capacity());
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

}
