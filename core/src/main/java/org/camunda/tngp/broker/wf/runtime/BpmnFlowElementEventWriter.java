package org.camunda.tngp.broker.wf.runtime;

import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.taskqueue.data.BpmnFlowElementEventEncoder;
import org.camunda.tngp.taskqueue.data.MessageHeaderEncoder;
import org.camunda.tngp.util.buffer.BufferWriter;

import uk.co.real_logic.agrona.MutableDirectBuffer;

public class BpmnFlowElementEventWriter implements BufferWriter
{

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final BpmnFlowElementEventEncoder bodyEncoder = new BpmnFlowElementEventEncoder();

    protected long key;
    protected long processId;
    protected long processInstanceId;
    protected ExecutionEventType eventType;
    protected int flowElementId;

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH
                + BpmnFlowElementEventEncoder.BLOCK_LENGTH;
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset);
        headerEncoder
            .blockLength(bodyEncoder.sbeBlockLength())
            .templateId(bodyEncoder.sbeTemplateId())
            .schemaId(bodyEncoder.sbeSchemaId())
            .version(bodyEncoder.sbeSchemaVersion())
            // TODO: make resourceId and shardId setters
            .resourceId(0)
            .shardId(0);

        bodyEncoder.wrap(buffer, offset + headerEncoder.encodedLength())
            .key(key)
            .processId(processId)
            .processInstanceId(processInstanceId)
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

    public BpmnFlowElementEventWriter processInstanceId(long processInstanceId)
    {
        this.processInstanceId = processInstanceId;
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
