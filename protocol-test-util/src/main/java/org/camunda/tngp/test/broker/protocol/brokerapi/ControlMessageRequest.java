package org.camunda.tngp.test.broker.protocol.brokerapi;

import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.io.DirectBufferInputStream;
import org.camunda.tngp.protocol.clientapi.ControlMessageRequestDecoder;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;
import org.camunda.tngp.protocol.clientapi.MessageHeaderDecoder;
import org.camunda.tngp.test.broker.protocol.MsgPackHelper;
import org.camunda.tngp.util.buffer.BufferReader;

public class ControlMessageRequest implements BufferReader
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final ControlMessageRequestDecoder bodyDecoder = new ControlMessageRequestDecoder();

    protected final MsgPackHelper msgPackHelper;

    protected Map<String, Object> data;

    protected int channelId;


    public ControlMessageRequest(int channelId, MsgPackHelper msgPackHelper)
    {
        this.channelId = channelId;
        this.msgPackHelper = msgPackHelper;
    }

    public int getChannelId()
    {
        return channelId;
    }

    public ControlMessageType messageType()
    {
        return bodyDecoder.messageType();
    }

    public Map<String, Object> getData()
    {
        return data;
    }


    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);

        bodyDecoder.wrap(buffer, offset + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

        final int dataLength = bodyDecoder.dataLength();
        data = msgPackHelper.readMsgPack(new DirectBufferInputStream(
                buffer,
                bodyDecoder.limit() + ControlMessageRequestDecoder.dataHeaderLength(),
                dataLength));

    }

}
