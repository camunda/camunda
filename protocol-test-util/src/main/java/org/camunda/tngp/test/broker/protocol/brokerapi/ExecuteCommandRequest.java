package org.camunda.tngp.test.broker.protocol.brokerapi;

import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.io.DirectBufferInputStream;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.protocol.clientapi.ExecuteCommandRequestDecoder;
import org.camunda.tngp.protocol.clientapi.MessageHeaderDecoder;
import org.camunda.tngp.test.broker.protocol.MsgPackHelper;
import org.camunda.tngp.util.buffer.BufferReader;

public class ExecuteCommandRequest implements BufferReader
{

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final ExecuteCommandRequestDecoder bodyDecoder = new ExecuteCommandRequestDecoder();

    protected final MsgPackHelper msgPackHelper;

    protected Map<String, Object> command;
    protected int channelId;

    public ExecuteCommandRequest(int channelId, MsgPackHelper msgPackHelper)
    {
        this.channelId = channelId;
        this.msgPackHelper = msgPackHelper;
    }

    public long key()
    {
        return bodyDecoder.key();
    }

    public int topicId()
    {
        return bodyDecoder.topicId();
    }

    public EventType eventType()
    {
        return bodyDecoder.eventType();
    }

    public Map<String, Object> getCommand()
    {
        return command;
    }

    public int getChannelId()
    {
        return channelId;
    }

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);

        bodyDecoder.wrap(buffer, offset + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

        final int commandLength = bodyDecoder.commandLength();
        command = msgPackHelper.readMsgPack(new DirectBufferInputStream(
                buffer,
                bodyDecoder.limit() + ExecuteCommandRequestDecoder.commandHeaderLength(),
                commandLength));
    }

}
