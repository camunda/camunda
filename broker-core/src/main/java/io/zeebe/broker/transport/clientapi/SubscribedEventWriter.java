package io.zeebe.broker.transport.clientapi;

import static io.zeebe.protocol.clientapi.SubscribedEventEncoder.eventHeaderLength;
import static io.zeebe.protocol.clientapi.SubscribedEventEncoder.keyNullValue;
import static io.zeebe.protocol.clientapi.SubscribedEventEncoder.partitionIdNullValue;
import static io.zeebe.protocol.clientapi.SubscribedEventEncoder.positionNullValue;
import static io.zeebe.protocol.clientapi.SubscribedEventEncoder.subscriberKeyNullValue;
import static io.zeebe.protocol.clientapi.SubscribedEventEncoder.topicNameHeaderLength;

import java.util.Objects;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.clientapi.MessageHeaderEncoder;
import io.zeebe.protocol.clientapi.SubscribedEventEncoder;
import io.zeebe.protocol.clientapi.SubscriptionType;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.TransportMessage;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.buffer.DirectBufferWriter;

public class SubscribedEventWriter implements BufferWriter
{
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final SubscribedEventEncoder bodyEncoder = new SubscribedEventEncoder();

    protected DirectBuffer topicName = new UnsafeBuffer(0, 0);
    protected int partitionId = partitionIdNullValue();
    protected long position = positionNullValue();
    protected long key = keyNullValue();
    protected long subscriberKey = subscriberKeyNullValue();
    protected SubscriptionType subscriptionType;
    protected EventType eventType;
    protected DirectBufferWriter eventBuffer = new DirectBufferWriter();
    protected BufferWriter eventWriter;

    protected final ServerOutput output;
    protected final TransportMessage message = new TransportMessage();

    public SubscribedEventWriter(final ServerOutput output)
    {
        this.output = output;
    }

    public SubscribedEventWriter topicName(final DirectBuffer topicName)
    {
        this.topicName.wrap(topicName);
        return this;
    }

    public SubscribedEventWriter partitionId(final int partitionId)
    {
        this.partitionId = partitionId;
        return this;
    }

    public SubscribedEventWriter position(final long position)
    {
        this.position = position;
        return this;
    }

    public SubscribedEventWriter key(final long key)
    {
        this.key = key;
        return this;
    }

    public SubscribedEventWriter subscriberKey(final long subscriberKey)
    {
        this.subscriberKey = subscriberKey;
        return this;
    }

    public SubscribedEventWriter subscriptionType(final SubscriptionType subscriptionType)
    {
        this.subscriptionType = subscriptionType;
        return this;
    }

    public SubscribedEventWriter eventType(final EventType eventType)
    {
        this.eventType = eventType;
        return this;
    }

    public SubscribedEventWriter event(final DirectBuffer buffer, final int offset, final int length)
    {
        this.eventBuffer.wrap(buffer, offset, length);
        this.eventWriter = eventBuffer;
        return this;
    }

    public SubscribedEventWriter eventWriter(final BufferWriter eventWriter)
    {
        this.eventWriter = eventWriter;
        return this;
    }

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH +
                SubscribedEventEncoder.BLOCK_LENGTH +
                topicNameHeaderLength() +
                topicName.capacity() +
                eventHeaderLength() +
                eventWriter.getLength();
    }

    @Override
    public void write(final MutableDirectBuffer buffer, int offset)
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
            .partitionId(partitionId)
            .putTopicName(topicName, 0, topicName.capacity())
            .position(position)
            .key(key)
            .subscriberKey(subscriberKey)
            .subscriptionType(subscriptionType)
            .eventType(eventType);

        offset += SubscribedEventEncoder.BLOCK_LENGTH + topicNameHeaderLength() + topicName.capacity();

        final int eventLength = eventWriter.getLength();
        buffer.putShort(offset, (short) eventLength, Protocol.ENDIANNESS);

        offset += eventHeaderLength();
        eventWriter.write(buffer, offset);
    }

    public boolean tryWriteMessage(int remoteStreamId)
    {
        Objects.requireNonNull(eventWriter);

        try
        {
            message.reset()
                .remoteStreamId(remoteStreamId)
                .writer(this);

            return output.sendMessage(message);
        }
        finally
        {
            reset();
        }
    }

    protected void reset()
    {
        this.partitionId = partitionIdNullValue();
        this.topicName.wrap(0, 0);
        this.position = positionNullValue();
        this.key = keyNullValue();
        this.subscriberKey = subscriberKeyNullValue();
        this.subscriptionType = SubscriptionType.NULL_VAL;
        this.eventType = EventType.NULL_VAL;
        this.eventWriter = null;
    }
}
