package org.camunda.tngp.broker.wf.runtime.bpmn.event;

import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.taskqueue.data.BpmnActivityEventEncoder;
import org.camunda.tngp.taskqueue.data.MessageHeaderEncoder;
import org.camunda.tngp.util.buffer.BufferWriter;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class BpmnActivityEventWriter implements BufferWriter
{

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final BpmnActivityEventEncoder bodyEncoder = new BpmnActivityEventEncoder();

    protected long key;
    protected long processId;
    protected long processInstanceId;
    protected ExecutionEventType eventType;
    protected int flowElementId;
    protected int taskQueueId;

    protected final UnsafeBuffer taskTypeBuffer = new UnsafeBuffer(0, 0);

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH +
                BpmnActivityEventEncoder.BLOCK_LENGTH +
                BpmnActivityEventEncoder.taskTypeHeaderLength() +
                taskTypeBuffer.capacity();
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset)
            .blockLength(bodyEncoder.sbeBlockLength())
            .templateId(bodyEncoder.sbeTemplateId())
            .schemaId(bodyEncoder.sbeSchemaId())
            .version(bodyEncoder.sbeSchemaVersion())
            .resourceId(0)
            .shardId(0);

        bodyEncoder.wrap(buffer, offset + MessageHeaderEncoder.ENCODED_LENGTH)
            .key(key)
            .processId(processId)
            .processInstanceId(processInstanceId)
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

    public BpmnActivityEventWriter processId(long processId)
    {
        this.processId = processId;
        return this;
    }

    public BpmnActivityEventWriter processInstanceId(long processInstanceId)
    {
        this.processInstanceId = processInstanceId;
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
