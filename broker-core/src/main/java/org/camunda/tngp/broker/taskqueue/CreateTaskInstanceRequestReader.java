package org.camunda.tngp.broker.taskqueue;

import org.camunda.tngp.protocol.taskqueue.CreateTaskInstanceDecoder;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderDecoder;
import org.camunda.tngp.util.buffer.BufferReader;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class CreateTaskInstanceRequestReader implements BufferReader
{

    protected MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected CreateTaskInstanceDecoder bodyDecoder = new CreateTaskInstanceDecoder();

    protected UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);
    protected UnsafeBuffer taskTypeBuffer = new UnsafeBuffer(0, 0);

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);

        offset += headerDecoder.encodedLength();
        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        offset += headerDecoder.blockLength();
        offset += CreateTaskInstanceDecoder.taskTypeHeaderLength();
        taskTypeBuffer.wrap(buffer, offset, bodyDecoder.taskTypeLength());

        offset += bodyDecoder.taskTypeLength();
        offset += CreateTaskInstanceDecoder.payloadHeaderLength();
        payloadBuffer.wrap(buffer, offset, bodyDecoder.payloadLength());
    }

    public DirectBuffer getTaskType()
    {
        return taskTypeBuffer;
    }

    public DirectBuffer getPayload()
    {
        return payloadBuffer;
    }

}
