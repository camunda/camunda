package org.camunda.tngp.broker.wf.runtime;

import org.camunda.tngp.protocol.wf.runtime.MessageHeaderDecoder;
import org.camunda.tngp.taskqueue.data.BpmnFlowElementEventDecoder;
import org.camunda.tngp.taskqueue.data.BpmnProcessEventDecoder;
import org.camunda.tngp.util.buffer.BufferReader;

import uk.co.real_logic.agrona.DirectBuffer;

public class BpmnEventReader implements BufferReader
{

    protected final BpmnFlowElementEventReader flowElementEventReader = new BpmnFlowElementEventReader();

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);

        final int templateId = headerDecoder.templateId();
        switch (templateId)
        {
            case BpmnProcessEventDecoder.TEMPLATE_ID:
                break;
            case BpmnFlowElementEventDecoder.TEMPLATE_ID:
                flowElementEventReader.wrap(buffer, offset, length);
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

}
