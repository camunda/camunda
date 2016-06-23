package org.camunda.tngp.broker.wf.runtime;

import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.taskqueue.data.BpmnProcessEventDecoder;
import org.camunda.tngp.taskqueue.data.MessageHeaderDecoder;
import org.camunda.tngp.util.buffer.BufferReader;

import uk.co.real_logic.agrona.DirectBuffer;

public class BpmnProcessEventReader implements BufferReader
{


    protected MessageHeaderDecoder headerDecoder;
    protected BpmnProcessEventDecoder bodyDecoder;

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        // TODO Auto-generated method stub

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
        return bodyDecoder.processId();
    }

    public long processInstanceId()
    {
        return bodyDecoder.processInstanceId();
    }

    public int initialElementId()
    {
        return bodyDecoder.initialElementId();
    }

}
