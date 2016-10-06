package org.camunda.tngp.protocol.taskqueue;

import org.agrona.DirectBuffer;
import org.camunda.tngp.util.buffer.BufferReader;

public class ProvideSubscriptionCreditsReader implements BufferReader
{

    protected MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected ProvideSubscriptionCreditsDecoder bodyDecoder = new ProvideSubscriptionCreditsDecoder();

    public int consumerId()
    {
        return bodyDecoder.consumerId();
    }

    public long subscriptionId()
    {
        return bodyDecoder.subscriptionId();
    }

    public long credits()
    {
        return bodyDecoder.credits();
    }

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);

        bodyDecoder.wrap(buffer,
                offset + headerDecoder.encodedLength(),
                headerDecoder.blockLength(),
                headerDecoder.version());

    }

}
