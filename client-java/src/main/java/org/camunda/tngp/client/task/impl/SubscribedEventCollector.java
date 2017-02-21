package org.camunda.tngp.client.task.impl;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.Agent;
import org.camunda.tngp.client.event.impl.EventTypeMapping;
import org.camunda.tngp.client.event.impl.TopicEventImpl;
import org.camunda.tngp.dispatcher.FragmentHandler;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.protocol.clientapi.MessageHeaderDecoder;
import org.camunda.tngp.protocol.clientapi.SubscribedEventDecoder;
import org.camunda.tngp.protocol.clientapi.SubscriptionType;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;

public class SubscribedEventCollector implements Agent
{
    protected static final String NAME = "event-collector";

    protected final TransportHeaderDescriptor transportHeaderDescriptor = new TransportHeaderDescriptor();
    protected final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    protected final SubscribedEventDecoder subscribedEventDecoder = new SubscribedEventDecoder();

    protected final Subscription receiveBufferSubscription;

    protected SubscribedEventHandler taskSubscriptionHandler;
    protected SubscribedEventHandler topicSubscriptionHandler;
    protected DataFrameHandler fragmentHandler = new DataFrameHandler();

    public SubscribedEventCollector(Subscription receiveBufferSubscription)
    {
        this.receiveBufferSubscription = receiveBufferSubscription;
    }

    public void setTaskHandler(SubscribedEventHandler taskSubscriptionHandler)
    {
        this.taskSubscriptionHandler = taskSubscriptionHandler;
    }

    public void setTopicEventHandler(SubscribedEventHandler topicSubscriptionHandler)
    {
        this.topicSubscriptionHandler = topicSubscriptionHandler;
    }

    @Override
    public int doWork()
    {
        return receiveBufferSubscription.poll(fragmentHandler, Integer.MAX_VALUE);
    }

    @Override
    public String roleName()
    {
        return NAME;
    }

    public class DataFrameHandler implements FragmentHandler
    {

        @Override
        public int onFragment(DirectBuffer buffer, int offset, int length, int streamId, boolean isMarkedFailed)
        {
            transportHeaderDescriptor.wrap(buffer, offset);

            offset += TransportHeaderDescriptor.HEADER_LENGTH;

            messageHeaderDecoder.wrap(buffer, offset);

            offset += MessageHeaderDecoder.ENCODED_LENGTH;

            final int protocolId = transportHeaderDescriptor.protocolId();
            final int templateId = messageHeaderDecoder.templateId();

            if (protocolId == Protocols.FULL_DUPLEX_SINGLE_MESSAGE && templateId == SubscribedEventDecoder.TEMPLATE_ID)
            {
                subscribedEventDecoder.wrap(buffer, offset, messageHeaderDecoder.blockLength(), messageHeaderDecoder.version());

                final SubscriptionType subscriptionType = subscribedEventDecoder.subscriptionType();
                final SubscribedEventHandler eventHandler = getHandlerForEvent(subscriptionType);

                if (eventHandler != null)
                {
                    final long key = subscribedEventDecoder.longKey();
                    final long subscriptionId = subscribedEventDecoder.subscriptionId();
                    final long position = subscribedEventDecoder.position();
                    final byte[] eventBuffer = new byte[subscribedEventDecoder.eventLength()];
                    subscribedEventDecoder.getEvent(eventBuffer, 0, eventBuffer.length);

                    final TopicEventImpl event = new TopicEventImpl(
                            key,
                            position,
                            EventTypeMapping.mapEventType(subscribedEventDecoder.eventType()),
                            eventBuffer);

                    eventHandler.onEvent(subscriptionId, event);
                }
                else
                {
                    System.out.println("Ignoring event for unknown subscription type");
                }
            }

            return FragmentHandler.CONSUME_FRAGMENT_RESULT;
        }
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

    public interface SubscribedEventHandler
    {
        void onEvent(long subscriptionId, TopicEventImpl event);
    }

}
