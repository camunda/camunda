package org.camunda.tngp.test.broker.protocol.clientapi;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.LangUtil;
import org.agrona.io.DirectBufferInputStream;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.protocol.clientapi.MessageHeaderDecoder;
import org.camunda.tngp.protocol.clientapi.SubscribedEventDecoder;
import org.camunda.tngp.protocol.clientapi.SubscriptionType;
import org.camunda.tngp.test.broker.protocol.MsgPackHelper;
import org.camunda.tngp.util.buffer.BufferReader;

public class SubscribedEvent implements BufferReader
{
    protected final RawMessage rawMessage;

    protected MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected SubscribedEventDecoder bodyDecoder = new SubscribedEventDecoder();

    protected Map<String, Object> event;

    protected MsgPackHelper msgPackHelper = new MsgPackHelper();

    public SubscribedEvent(RawMessage rawMessage)
    {
        this.rawMessage = rawMessage;
        final DirectBuffer buffer = rawMessage.getMessage();
        wrap(buffer, 0, buffer.capacity());
    }

    public int topicId()
    {
        return bodyDecoder.topicId();
    }

    public long position()
    {
        return bodyDecoder.position();
    }

    public long key()
    {
        return bodyDecoder.key();
    }

    public long subscriberKey()
    {
        return bodyDecoder.subscriberKey();
    }

    public SubscriptionType subscriptionType()
    {
        return bodyDecoder.subscriptionType();
    }

    public EventType eventType()
    {
        return bodyDecoder.eventType();
    }

    public Map<String, Object> event()
    {
        return event;
    }

    public RawMessage getRawMessage()
    {
        return rawMessage;
    }

    @Override
    public void wrap(DirectBuffer responseBuffer, int offset, int length)
    {
        headerDecoder.wrap(responseBuffer, offset);

        if (headerDecoder.templateId() != bodyDecoder.sbeTemplateId())
        {
            throw new RuntimeException("Unexpected response from broker.");
        }

        bodyDecoder.wrap(responseBuffer, offset + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

        final int eventLength = bodyDecoder.eventLength();
        final int eventOffset = headerDecoder.encodedLength() + headerDecoder.blockLength() + SubscribedEventDecoder.eventHeaderLength();

        try (final InputStream is = new DirectBufferInputStream(responseBuffer, offset + eventOffset, eventLength))
        {
            event = msgPackHelper.readMsgPack(is);
        }
        catch (IOException e)
        {
            LangUtil.rethrowUnchecked(e);
        }

    }

}
