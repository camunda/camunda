package org.camunda.tngp.test.broker.protocol.brokerapi;

import java.util.Map;

import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.protocol.clientapi.MessageHeaderEncoder;
import org.camunda.tngp.protocol.clientapi.SubscribedEventEncoder;
import org.camunda.tngp.protocol.clientapi.SubscriptionType;
import org.camunda.tngp.test.broker.protocol.MsgPackHelper;
import org.camunda.tngp.test.util.collection.MapBuilder;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;
import org.camunda.tngp.transport.singlemessage.SingleMessageHeaderDescriptor;
import org.camunda.tngp.util.buffer.BufferWriter;

public class SubscribedEventBuilder implements BufferWriter
{
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final SubscribedEventEncoder bodyEncoder = new SubscribedEventEncoder();
    protected final TransportHeaderDescriptor transportHeaderDescriptor = new TransportHeaderDescriptor();

    protected final MsgPackHelper msgPackHelper;
    protected final Dispatcher sendBuffer;
    protected final ClaimedFragment claimedFragment = new ClaimedFragment();

    protected int topicId;
    protected long position;
    protected long longKey;
    protected long subscriptionId;
    protected SubscriptionType subscriptionType;
    protected EventType eventType;
    protected byte[] event;

    public SubscribedEventBuilder(MsgPackHelper msgPackHelper, Dispatcher sendBuffer)
    {
        this.msgPackHelper = msgPackHelper;
        this.sendBuffer = sendBuffer;
    }

    public SubscribedEventBuilder topicId(int topicId)
    {
        this.topicId = topicId;
        return this;
    }

    public SubscribedEventBuilder position(long position)
    {
        this.position = position;
        return this;
    }

    public SubscribedEventBuilder longKey(long longKey)
    {
        this.longKey = longKey;
        return this;
    }

    public SubscribedEventBuilder subscriptionId(long subscriptionId)
    {
        this.subscriptionId = subscriptionId;
        return this;
    }

    public SubscribedEventBuilder subscriptionType(SubscriptionType subscriptionType)
    {
        this.subscriptionType = subscriptionType;
        return this;
    }

    public SubscribedEventBuilder eventType(EventType eventType)
    {
        this.eventType = eventType;
        return this;
    }

    public SubscribedEventBuilder event(Map<String, Object> event)
    {
        this.event = msgPackHelper.encodeAsMsgPack(event);
        return this;
    }

    public MapBuilder<SubscribedEventBuilder> event()
    {
        return new MapBuilder<>(this, this::event);
    }

    public void push(int channelId)
    {
        final long claimedOffset = sendBuffer.claim(
                claimedFragment,
                getLength() + TransportHeaderDescriptor.HEADER_LENGTH + SingleMessageHeaderDescriptor.HEADER_LENGTH,
                channelId);

        if (claimedOffset < 0)
        {
            throw new RuntimeException("Could not claim fragment on send buffer");
        }

        writeMessageToFragment(this);
        claimedFragment.commit();
    }


    protected void writeMessageToFragment(BufferWriter bodyWriter)
    {
        final MutableDirectBuffer buffer = claimedFragment.getBuffer();
        int offset = claimedFragment.getOffset();

        // transport protocol header
        transportHeaderDescriptor.wrap(buffer, offset)
            .protocolId(Protocols.FULL_DUPLEX_SINGLE_MESSAGE);

        offset += TransportHeaderDescriptor.HEADER_LENGTH;
        offset += SingleMessageHeaderDescriptor.HEADER_LENGTH;

        bodyWriter.write(buffer, offset);
    }

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH +
                SubscribedEventEncoder.BLOCK_LENGTH +
                SubscribedEventEncoder.eventHeaderLength() +
                event.length;
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset)
            .blockLength(bodyEncoder.sbeBlockLength())
            .schemaId(bodyEncoder.sbeSchemaId())
            .templateId(bodyEncoder.sbeTemplateId())
            .version(bodyEncoder.sbeSchemaVersion());

        bodyEncoder.wrap(buffer, offset + headerEncoder.encodedLength())
            .eventType(eventType)
            .longKey(longKey)
            .position(position)
            .putEvent(event, 0, event.length)
            .subscriptionId(subscriptionId)
            .subscriptionType(subscriptionType)
            .topicId(topicId);
    }


}
