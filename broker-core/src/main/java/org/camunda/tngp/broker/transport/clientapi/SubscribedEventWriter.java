package org.camunda.tngp.broker.transport.clientapi;

import java.util.Objects;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.protocol.Protocol;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.protocol.clientapi.MessageHeaderEncoder;
import org.camunda.tngp.protocol.clientapi.SubscribedEventEncoder;
import org.camunda.tngp.protocol.clientapi.SubscriptionType;
import org.camunda.tngp.util.buffer.BufferWriter;
import org.camunda.tngp.util.buffer.DirectBufferWriter;

public class SubscribedEventWriter implements BufferWriter
{
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final SubscribedEventEncoder bodyEncoder = new SubscribedEventEncoder();

    protected int channelId;

    protected int topicId;
    protected long position;
    protected long key;
    protected long subscriberKey;
    protected SubscriptionType subscriptionType;
    protected EventType eventType;
    protected DirectBufferWriter eventBuffer = new DirectBufferWriter();
    protected BufferWriter eventWriter;

    protected final SingleMessageWriter singleMessageWriter;

    public SubscribedEventWriter(SingleMessageWriter singleMessageWriter)
    {
        this.singleMessageWriter = singleMessageWriter;
    }

    public SubscribedEventWriter channelId(int channelId)
    {
        this.channelId = channelId;
        return this;
    }

    public SubscribedEventWriter topicId(int topicId)
    {
        this.topicId = topicId;
        return this;
    }

    public SubscribedEventWriter position(long position)
    {
        this.position = position;
        return this;
    }

    public SubscribedEventWriter key(long key)
    {
        this.key = key;
        return this;
    }

    public SubscribedEventWriter subscriberKey(long subscriberKey)
    {
        this.subscriberKey = subscriberKey;
        return this;
    }

    public SubscribedEventWriter subscriptionType(SubscriptionType subscriptionType)
    {
        this.subscriptionType = subscriptionType;
        return this;
    }

    public SubscribedEventWriter eventType(EventType eventType)
    {
        this.eventType = eventType;
        return this;
    }

    public SubscribedEventWriter event(DirectBuffer buffer, int offset, int length)
    {
        this.eventBuffer.wrap(buffer, offset, length);
        this.eventWriter = eventBuffer;
        return this;
    }

    public SubscribedEventWriter eventWriter(BufferWriter eventWriter)
    {
        this.eventWriter = eventWriter;
        return this;
    }

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH +
                SubscribedEventEncoder.BLOCK_LENGTH +
                SubscribedEventEncoder.eventHeaderLength() +
                eventWriter.getLength();
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        headerEncoder
            .wrap(buffer, offset)
            .blockLength(bodyEncoder.sbeBlockLength())
            .templateId(bodyEncoder.sbeTemplateId())
            .schemaId(bodyEncoder.sbeSchemaId())
            .version(bodyEncoder.sbeSchemaVersion());

        offset += MessageHeaderEncoder.ENCODED_LENGTH;

        bodyEncoder
            .wrap(buffer, offset)
            .topicId(topicId)
            .position(position)
            .key(key)
            .subscriberKey(subscriberKey)
            .subscriptionType(subscriptionType)
            .eventType(eventType);

        offset += SubscribedEventEncoder.BLOCK_LENGTH;

        final int eventLength = eventWriter.getLength();
        buffer.putShort(offset, (short) eventLength, Protocol.ENDIANNESS);

        offset += SubscribedEventEncoder.eventHeaderLength();
        eventWriter.write(buffer, offset);
    }

    public boolean tryWriteMessage()
    {
        Objects.requireNonNull(eventWriter);

        try
        {
            return singleMessageWriter.tryWrite(channelId, this);
        }
        finally
        {
            reset();
        }
    }

    protected void reset()
    {
        this.channelId = -1;
        this.topicId = SubscribedEventEncoder.topicIdNullValue();
        this.position = SubscribedEventEncoder.positionNullValue();
        this.key = SubscribedEventEncoder.keyNullValue();
        this.subscriberKey = SubscribedEventEncoder.subscriberKeyNullValue();
        this.subscriptionType = SubscriptionType.NULL_VAL;
        this.eventType = EventType.NULL_VAL;
        this.eventWriter = null;
    }
}
