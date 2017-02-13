package org.camunda.tngp.broker.transport.clientapi;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.protocol.clientapi.MessageHeaderEncoder;
import org.camunda.tngp.protocol.clientapi.SubscribedEventEncoder;
import org.camunda.tngp.util.buffer.BufferWriter;

public class SubscribedEventWriter implements BufferWriter
{
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final SubscribedEventEncoder bodyEncoder = new SubscribedEventEncoder();

    protected int channelId;

    protected int topicId;
    protected long position;
    protected long longKey;
    protected EventType eventType;
    protected UnsafeBuffer event = new UnsafeBuffer(0, 0);

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

    public SubscribedEventWriter longKey(long longKey)
    {
        this.longKey = longKey;
        return this;
    }

    public SubscribedEventWriter eventType(EventType eventType)
    {
        this.eventType = eventType;
        return this;
    }

    public SubscribedEventWriter event(DirectBuffer buffer, int offset, int length)
    {
        this.event.wrap(buffer, offset, length);
        return this;
    }

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH +
                SubscribedEventEncoder.BLOCK_LENGTH +
                SubscribedEventEncoder.eventHeaderLength() +
                event.capacity();
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

        bodyEncoder
            .wrap(buffer, offset + MessageHeaderEncoder.ENCODED_LENGTH)
            .topicId(topicId)
            .position(position)
            .longKey(longKey)
            .eventType(eventType)
            .putEvent(event, 0, event.capacity());
    }

    public boolean tryWriteMessage()
    {
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
        this.longKey = SubscribedEventEncoder.longKeyNullValue();
        this.eventType = EventType.NULL_VAL;
        this.event.wrap(0, 0);
    }
}
