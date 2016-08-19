package org.camunda.tngp.broker.wf.runtime;

import org.camunda.tngp.protocol.wf.runtime.MessageHeaderDecoder;
import org.camunda.tngp.protocol.wf.runtime.StartWorkflowInstanceDecoder;
import org.camunda.tngp.util.buffer.BufferReader;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class StartWorkflowInstanceRequestReader implements BufferReader
{
    protected MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected StartWorkflowInstanceDecoder bodyDecoder = new StartWorkflowInstanceDecoder();

    protected final UnsafeBuffer keyBuffer = new UnsafeBuffer(0, 0);

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);

        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        final int wfDefinitionKeyLength = bodyDecoder.wfDefinitionKeyLength();

        offset += bodyDecoder.encodedLength();
        offset += StartWorkflowInstanceDecoder.wfDefinitionKeyHeaderLength();

        if (wfDefinitionKeyLength > 0)
        {
            keyBuffer.wrap(buffer, offset, wfDefinitionKeyLength);
        }
        else
        {
            keyBuffer.wrap(0, 0);
        }

    }

    public long wfDefinitionId()
    {
        return bodyDecoder.wfDefinitionId();
    }

    public DirectBuffer wfDefinitionKey()
    {
        return keyBuffer;
    }

}
