package org.camunda.tngp.broker.wf.runtime.log.bpmn;

import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.broker.log.LogEntryWriter;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.protocol.log.BpmnProcessEventEncoder;

public class BpmnProcessEventWriter extends LogEntryWriter<BpmnProcessEventWriter, BpmnProcessEventEncoder>
{

    protected long key;
    protected long processId;
    protected long processInstanceId;
    protected ExecutionEventType event;
    protected int initialElementId;
    protected long bpmnBranchKey;

    public BpmnProcessEventWriter()
    {
        super(new BpmnProcessEventEncoder());
    }

    @Override
    protected int getBodyLength()
    {
        return BpmnProcessEventEncoder.BLOCK_LENGTH;
    }

    @Override
    protected void writeBody(MutableDirectBuffer buffer, int offset)
    {
        bodyEncoder.wrap(buffer, offset)
            .key(key)
            .wfDefinitionId(processId)
            .wfInstanceId(processInstanceId)
            .event(event.value())
            .initialElementId(initialElementId)
            .bpmnBranchKey(bpmnBranchKey);

    }

    public BpmnProcessEventWriter key(long key)
    {
        this.key = key;
        return this;
    }

    public BpmnProcessEventWriter processId(long processId)
    {
        this.processId = processId;
        return this;
    }

    public BpmnProcessEventWriter processInstanceId(long processInstanceId)
    {
        this.processInstanceId = processInstanceId;
        return this;
    }

    public BpmnProcessEventWriter event(ExecutionEventType event)
    {
        this.event = event;
        return this;
    }

    public BpmnProcessEventWriter bpmnBranchKey(long bpmnBranchKey)
    {
        this.bpmnBranchKey = bpmnBranchKey;
        return this;
    }

    public BpmnProcessEventWriter initialElementId(int initialElementId)
    {
        this.initialElementId = initialElementId;
        return this;
    }

}
