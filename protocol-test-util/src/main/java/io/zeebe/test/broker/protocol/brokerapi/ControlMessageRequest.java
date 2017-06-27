package io.zeebe.test.broker.protocol.brokerapi;

import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.io.DirectBufferInputStream;
import io.zeebe.protocol.clientapi.ControlMessageRequestDecoder;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.util.buffer.BufferReader;

import io.zeebe.transport.RemoteAddress;

public class ControlMessageRequest implements BufferReader
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final ControlMessageRequestDecoder bodyDecoder = new ControlMessageRequestDecoder();

    protected final MsgPackHelper msgPackHelper;
    protected final RemoteAddress source;

    protected Map<String, Object> data;


    public ControlMessageRequest(RemoteAddress source, MsgPackHelper msgPackHelper)
    {
        this.source = source;
        this.msgPackHelper = msgPackHelper;
    }

    public RemoteAddress getSource()
    {
        return source;
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
        if (dataLength > 0)
        {
            data = msgPackHelper.readMsgPack(new DirectBufferInputStream(
                buffer,
                bodyDecoder.limit() + ControlMessageRequestDecoder.dataHeaderLength(),
                dataLength));
        }

    }

}
