package org.camunda.tngp.broker.wf.runtime;

import org.camunda.tngp.protocol.wf.runtime.MessageHeaderDecoder;
import org.camunda.tngp.util.buffer.BufferReader;

import uk.co.real_logic.agrona.DirectBuffer;

public class BpmnEventHeaderReader implements BufferReader
{

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

    public int bpmnEventType()
    {
        return headerDecoder.templateId();
    }

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);

    }
}
