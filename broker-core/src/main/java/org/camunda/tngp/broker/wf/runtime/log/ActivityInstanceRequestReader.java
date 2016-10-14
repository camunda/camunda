package org.camunda.tngp.broker.wf.runtime.log;

import org.camunda.tngp.protocol.log.ActivityInstanceRequestDecoder;
import org.camunda.tngp.protocol.log.ActivityInstanceRequestType;
import org.camunda.tngp.protocol.log.MessageHeaderDecoder;
import org.camunda.tngp.util.buffer.BufferReader;

import org.agrona.DirectBuffer;

public class ActivityInstanceRequestReader implements BufferReader
{

    protected MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected ActivityInstanceRequestDecoder bodyDecoder = new ActivityInstanceRequestDecoder();

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

        bodyDecoder.wrap(buffer, offset + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());
    }

}
