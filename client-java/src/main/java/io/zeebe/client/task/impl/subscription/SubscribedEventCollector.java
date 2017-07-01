package io.zeebe.client.task.impl.subscription;

import static io.zeebe.util.VarDataUtil.readBytes;

import org.agrona.DirectBuffer;
import io.zeebe.client.event.impl.EventTypeMapping;
import io.zeebe.client.event.impl.TopicEventImpl;
import io.zeebe.client.impl.Loggers;
import io.zeebe.dispatcher.FragmentHandler;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.protocol.clientapi.*;
import io.zeebe.transport.protocol.Protocols;
import io.zeebe.transport.protocol.TransportHeaderDescriptor;
import io.zeebe.util.actor.Actor;
import org.slf4j.Logger;

public class SubscribedEventCollector implements Actor, FragmentHandler
{
    protected static final Logger LOGGER = Loggers.SUBSCRIPTION_LOGGER;
    protected static final String NAME = "event-collector";

    protected final TransportHeaderDescriptor transportHeaderDescriptor = new TransportHeaderDescriptor();
    protected final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    protected final SubscribedEventDecoder subscribedEventDecoder = new SubscribedEventDecoder();

    protected final Subscription receiveBufferSubscription;

    protected final SubscribedEventHandler taskSubscriptionHandler;
    protected final SubscribedEventHandler topicSubscriptionHandler;

    public SubscribedEventCollector(
            Subscription receiveBufferSubscription,
            SubscribedEventHandler taskSubscriptionHandler,
            SubscribedEventHandler topicSubscriptionHandler)
    {
        this.receiveBufferSubscription = receiveBufferSubscription;
        this.taskSubscriptionHandler = taskSubscriptionHandler;
        this.topicSubscriptionHandler = topicSubscriptionHandler;
    }

    @Override
    public int doWork()
    {
        return receiveBufferSubscription.peekAndConsume(this, Integer.MAX_VALUE);
    }

    @Override
    public String name()
    {
        return NAME;
    }


    @Override
    public int onFragment(DirectBuffer buffer, int offset, int length, int streamId, boolean isMarkedFailed)
    {
        transportHeaderDescriptor.wrap(buffer, offset);

        offset += TransportHeaderDescriptor.HEADER_LENGTH;

        messageHeaderDecoder.wrap(buffer, offset);

        offset += MessageHeaderDecoder.ENCODED_LENGTH;

        final int protocolId = transportHeaderDescriptor.protocolId();
        final int templateId = messageHeaderDecoder.templateId();

        final boolean messageHandled;

        if (protocolId == Protocols.FULL_DUPLEX_SINGLE_MESSAGE && templateId == SubscribedEventDecoder.TEMPLATE_ID)
        {
            subscribedEventDecoder.wrap(buffer, offset, messageHeaderDecoder.blockLength(), messageHeaderDecoder.version());

            final SubscriptionType subscriptionType = subscribedEventDecoder.subscriptionType();
            final SubscribedEventHandler eventHandler = getHandlerForEvent(subscriptionType);

            if (eventHandler != null)
            {
                final long key = subscribedEventDecoder.key();
                final long subscriberKey = subscribedEventDecoder.subscriberKey();
                final long position = subscribedEventDecoder.position();
                final int partitionId = subscribedEventDecoder.partitionId();
                final String topicName = subscribedEventDecoder.topicName();
                final byte[] eventBuffer = readBytes(subscribedEventDecoder::getEvent, subscribedEventDecoder::eventLength);

                final TopicEventImpl event = new TopicEventImpl(
                        topicName,
                        partitionId,
                        key,
                        position,
                        EventTypeMapping.mapEventType(subscribedEventDecoder.eventType()),
                        eventBuffer);

                messageHandled = eventHandler.onEvent(subscriberKey, event);
            }
            else
            {
                LOGGER.info("Ignoring event for unknown subscription type " + subscriptionType.toString());
                messageHandled = true;
            }
        }
        else
        {
            // ignoring
            messageHandled = true;
        }


        return messageHandled ? FragmentHandler.CONSUME_FRAGMENT_RESULT : FragmentHandler.POSTPONE_FRAGMENT_RESULT;
    }

    protected SubscribedEventHandler getHandlerForEvent(SubscriptionType subscriptionType)
    {
        if (subscriptionType == SubscriptionType.TASK_SUBSCRIPTION)
        {
            return taskSubscriptionHandler;
        }
        else if (subscriptionType == SubscriptionType.TOPIC_SUBSCRIPTION)
        {
            return topicSubscriptionHandler;
        }
        else
        {
            return null;
        }
    }

}
