package org.camunda.tngp.broker.wf.runtime.log.bpmn;

import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.protocol.log.BpmnProcessEventDecoder;
import org.camunda.tngp.protocol.log.MessageHeaderDecoder;
import org.camunda.tngp.util.buffer.BufferReader;

import org.agrona.DirectBuffer;

public class BpmnProcessEventReader implements BufferReader
{

    protected MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected BpmnProcessEventDecoder bodyDecoder = new BpmnProcessEventDecoder();

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);

        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

    }

    public ExecutionEventType event()
    {
        return ExecutionEventType.get(bodyDecoder.event());
    }

    public long key()
    {
        return bodyDecoder.key();
    }

    public long processId()
    {
        return bodyDecoder.wfDefinitionId();
    }

    public long processInstanceId()
    {
        return bodyDecoder.wfInstanceId();
    }

    public int initialElementId()
    {
        return bodyDecoder.initialElementId();
    }

    public long bpmnBranchKey()
    {
        return bodyDecoder.bpmnBranchKey();
    }

}
