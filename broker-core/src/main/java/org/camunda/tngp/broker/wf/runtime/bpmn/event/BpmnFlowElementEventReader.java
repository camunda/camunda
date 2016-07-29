package org.camunda.tngp.broker.wf.runtime.bpmn.event;

import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.taskqueue.data.BpmnFlowElementEventDecoder;
import org.camunda.tngp.taskqueue.data.MessageHeaderDecoder;
import org.camunda.tngp.util.buffer.BufferReader;

import uk.co.real_logic.agrona.DirectBuffer;

public class BpmnFlowElementEventReader implements BufferReader
{

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final BpmnFlowElementEventDecoder bodyDecoder = new BpmnFlowElementEventDecoder();

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);
        bodyDecoder.wrap(buffer, offset + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());
    }

    public long key()
    {
        return bodyDecoder.key();
    }

    public long wfDefinitionId()
    {
        return bodyDecoder.wfDefinitionId();
    }

    public ExecutionEventType event()
    {
        return ExecutionEventType.get(bodyDecoder.event());
    }

    public int flowElementId()
    {
        return bodyDecoder.flowElementId();
    }

    public long wfInstanceId()
    {
        return bodyDecoder.wfInstanceId();
    }

}
