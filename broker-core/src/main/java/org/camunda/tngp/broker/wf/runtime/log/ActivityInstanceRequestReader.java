package org.camunda.tngp.broker.wf.runtime.log;

import org.camunda.tngp.protocol.log.ActivityInstanceRequestDecoder;
import org.camunda.tngp.protocol.log.ActivityInstanceRequestType;
import org.camunda.tngp.protocol.log.MessageHeaderDecoder;
import org.camunda.tngp.util.buffer.BufferReader;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ActivityInstanceRequestReader implements BufferReader
{

    protected MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected ActivityInstanceRequestDecoder bodyDecoder = new ActivityInstanceRequestDecoder();

    protected UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);

    public long activityInstanceKey()
    {
        return bodyDecoder.key();
    }

    public ActivityInstanceRequestType type()
    {
        return bodyDecoder.type();
    }

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);
        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());
        offset += headerDecoder.blockLength();

        offset += ActivityInstanceRequestDecoder.payloadHeaderLength();

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

    public DirectBuffer payload()
    {
        return payloadBuffer;
    }

}
