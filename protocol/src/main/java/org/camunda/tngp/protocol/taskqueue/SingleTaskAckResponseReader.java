package org.camunda.tngp.protocol.taskqueue;

import org.camunda.tngp.util.buffer.BufferReader;

import uk.co.real_logic.agrona.DirectBuffer;

public class SingleTaskAckResponseReader implements BufferReader
{
    protected MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected SingleTaskAckDecoder bodyDecoder = new SingleTaskAckDecoder();

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);
        bodyDecoder.wrap(buffer, offset + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());
    }

    public long taskId()
    {
        return bodyDecoder.taskId();
    }

}
