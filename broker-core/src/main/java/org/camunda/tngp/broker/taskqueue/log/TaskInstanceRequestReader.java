package org.camunda.tngp.broker.taskqueue.log;

import org.camunda.tngp.protocol.log.MessageHeaderDecoder;
import org.camunda.tngp.protocol.log.TaskInstanceRequestDecoder;
import org.camunda.tngp.protocol.log.TaskInstanceRequestType;
import org.camunda.tngp.util.buffer.BufferReader;

import org.agrona.DirectBuffer;

public class TaskInstanceRequestReader implements BufferReader
{

    protected MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected TaskInstanceRequestDecoder bodyDecoder = new TaskInstanceRequestDecoder();

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);

        bodyDecoder.wrap(buffer, offset + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

    }

    public TaskInstanceRequestType type()
    {
        return bodyDecoder.type();

    }

    public long key()
    {
        return bodyDecoder.key();
    }

    public long consumerId()
    {
        return bodyDecoder.lockOwnerId();
    }
}
