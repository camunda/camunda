package org.camunda.tngp.protocol.taskqueue;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.util.buffer.BufferReader;

public class CloseTaskSubscriptionRequestReader implements BufferReader
{

    protected MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected CloseTaskSubscriptionDecoder bodyDecoder = new CloseTaskSubscriptionDecoder();

    protected UnsafeBuffer taskTypeBuffer = new UnsafeBuffer(0, 0);

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);

        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());
    }

    public long subscriptionId()
    {
        return bodyDecoder.subscriptionId();
    }

    public int consumerId()
    {
        return bodyDecoder.consumerId();
    }

}
