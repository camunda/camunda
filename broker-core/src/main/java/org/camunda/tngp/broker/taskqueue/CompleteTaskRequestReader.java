package org.camunda.tngp.broker.taskqueue;

import org.camunda.tngp.protocol.taskqueue.CompleteTaskDecoder;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderDecoder;
import org.camunda.tngp.util.buffer.BufferReader;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class CompleteTaskRequestReader implements BufferReader
{

    protected MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected CompleteTaskDecoder bodyDecoder = new CompleteTaskDecoder();

    protected UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);

        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        offset += headerDecoder.blockLength();
        offset += CompleteTaskDecoder.payloadHeaderLength();

        payloadBuffer.wrap(buffer, offset, bodyDecoder.payloadLength());
    }

    public long taskId()
    {
        return bodyDecoder.taskId();
    }

    public int consumerId()
    {
        return bodyDecoder.consumerId();
    }

    public DirectBuffer getPayload()
    {
        return payloadBuffer;
    }

}
