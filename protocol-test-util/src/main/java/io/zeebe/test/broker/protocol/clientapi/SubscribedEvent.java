package io.zeebe.test.broker.protocol.clientapi;

import static io.zeebe.protocol.clientapi.SubscribedEventDecoder.eventHeaderLength;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.LangUtil;
import org.agrona.io.DirectBufferInputStream;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.protocol.clientapi.SubscribedEventDecoder;
import io.zeebe.protocol.clientapi.SubscriptionType;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.util.buffer.BufferReader;

public class SubscribedEvent implements BufferReader
{
    protected final RawMessage rawMessage;

    protected MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected SubscribedEventDecoder bodyDecoder = new SubscribedEventDecoder();

    protected String topicName;
    protected Map<String, Object> event;

    protected MsgPackHelper msgPackHelper = new MsgPackHelper();

    public SubscribedEvent(RawMessage rawMessage)
    {
        this.rawMessage = rawMessage;
        final DirectBuffer buffer = rawMessage.getMessage();
        wrap(buffer, 0, buffer.capacity());
    }

    public String topicName()
    {
        return topicName;
    }

    public int partitionId()
    {
        return bodyDecoder.partitionId();
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

        topicName = bodyDecoder.topicName();

        final int eventLength = bodyDecoder.eventLength();
        final int eventOffset = bodyDecoder.limit() + eventHeaderLength();

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
