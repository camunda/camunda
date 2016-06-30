package org.camunda.tngp.broker.wf.runtime.bpmn.event;

import org.camunda.tngp.protocol.wf.runtime.MessageHeaderDecoder;
import org.camunda.tngp.taskqueue.data.BpmnActivityEventDecoder;
import org.camunda.tngp.taskqueue.data.BpmnFlowElementEventDecoder;
import org.camunda.tngp.taskqueue.data.BpmnProcessEventDecoder;
import org.camunda.tngp.util.buffer.BufferReader;

import uk.co.real_logic.agrona.DirectBuffer;

public class BpmnEventReader implements BufferReader
{

    protected final BpmnFlowElementEventReader flowElementEventReader = new BpmnFlowElementEventReader();
    protected final BpmnProcessEventReader processEventReader = new BpmnProcessEventReader();
    protected final BpmnActivityEventReader activityEventReader = new BpmnActivityEventReader();

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);

        // TODO: the individual readers should be invalidated before reading
        //   so that we do not accidentally read old values

        final int templateId = headerDecoder.templateId();
        switch (templateId)
        {
            case BpmnProcessEventDecoder.TEMPLATE_ID:
                processEventReader.wrap(buffer, offset, length);
                break;
            case BpmnFlowElementEventDecoder.TEMPLATE_ID:
                flowElementEventReader.wrap(buffer, offset, length);
                break;
            case BpmnActivityEventDecoder.TEMPLATE_ID:
                activityEventReader.wrap(buffer, offset, length);
                break;
            default:
                throw new RuntimeException("Unsupported template " + templateId);
        }
    }

    public int templateId()
    {
        return headerDecoder.templateId();
    }

    public BpmnFlowElementEventReader flowElementEvent()
    {
        return flowElementEventReader;
    }

    public BpmnProcessEventReader processEvent()
    {
        return processEventReader;
    }

    public BpmnActivityEventReader activityEvent()
    {
        return activityEventReader;
    }

}
