package io.zeebe.test.broker.protocol.brokerapi;

import static io.zeebe.protocol.clientapi.ExecuteCommandRequestDecoder.commandHeaderLength;

import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.io.DirectBufferInputStream;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.clientapi.ExecuteCommandRequestDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.util.buffer.BufferReader;

import io.zeebe.transport.RemoteAddress;

public class ExecuteCommandRequest implements BufferReader
{

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final ExecuteCommandRequestDecoder bodyDecoder = new ExecuteCommandRequestDecoder();

    protected final MsgPackHelper msgPackHelper;

    protected String topicName;
    protected Map<String, Object> command;
    protected RemoteAddress source;

    public ExecuteCommandRequest(RemoteAddress source, MsgPackHelper msgPackHelper)
    {
        this.source = source;
        this.msgPackHelper = msgPackHelper;
    }

    public long key()
    {
        return bodyDecoder.key();
    }

    public String topicName()
    {
        return topicName;
    }

    public int partitionId()
    {
        return bodyDecoder.partitionId();
    }

    public EventType eventType()
    {
        return bodyDecoder.eventType();
    }

    public Map<String, Object> getCommand()
    {
        return command;
    }

    public RemoteAddress getSource()
    {
        return source;
    }

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);

        bodyDecoder.wrap(buffer, offset + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

        topicName = bodyDecoder.topicName();

        final int commandLength = bodyDecoder.commandLength();
        final int commandOffset = bodyDecoder.limit() + commandHeaderLength();

        command = msgPackHelper.readMsgPack(new DirectBufferInputStream(
                buffer,
                commandOffset,
                commandLength));
    }

}
