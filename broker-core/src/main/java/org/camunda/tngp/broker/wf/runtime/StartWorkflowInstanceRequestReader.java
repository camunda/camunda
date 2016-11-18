package org.camunda.tngp.broker.wf.runtime;

import org.camunda.tngp.protocol.wf.MessageHeaderDecoder;
import org.camunda.tngp.protocol.wf.StartWorkflowInstanceDecoder;
import org.camunda.tngp.util.buffer.BufferReader;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class StartWorkflowInstanceRequestReader implements BufferReader
{
    protected MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected StartWorkflowInstanceDecoder bodyDecoder = new StartWorkflowInstanceDecoder();

    protected final UnsafeBuffer keyBuffer = new UnsafeBuffer(0, 0);
    protected final UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);

        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        final int wfDefinitionKeyLength = bodyDecoder.wfDefinitionKeyLength();

        offset += bodyDecoder.encodedLength();
        offset += StartWorkflowInstanceDecoder.wfDefinitionKeyHeaderLength();

        keyBuffer.wrap(buffer, offset, wfDefinitionKeyLength);

        offset += wfDefinitionKeyLength;
        bodyDecoder.limit(offset);
        offset += StartWorkflowInstanceDecoder.payloadHeaderLength();

        final int payloadLength = bodyDecoder.payloadLength();

        if (payloadLength > 0)
        {
            payloadBuffer.wrap(buffer, offset, payloadLength);
        }
        else
        {
            payloadBuffer.wrap(0, 0);
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

    public DirectBuffer payload()
    {
        return payloadBuffer;
    }

}
