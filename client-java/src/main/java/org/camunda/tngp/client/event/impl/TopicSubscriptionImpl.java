package org.camunda.tngp.client.event.impl;

import java.util.concurrent.atomic.AtomicBoolean;

import org.camunda.tngp.client.event.PollableTopicSubscription;
import org.camunda.tngp.client.event.TopicEventHandler;
import org.camunda.tngp.client.event.TopicSubscription;
import org.camunda.tngp.util.CheckedConsumer;

public class TopicSubscriptionImpl
    extends EventSubscription<TopicSubscriptionImpl>
    implements TopicSubscription, PollableTopicSubscription
{

    protected static final int MAX_HANDLING_RETRIES = 2;

    protected CheckedConsumer<TopicEventImpl> handler;
    protected final TopicClientImpl client;

    protected AtomicBoolean processingFlag = new AtomicBoolean(false);
    protected long lastProcessedEventPosition;
    protected long lastAcknowledgedPosition;

    protected final long startPosition;
    protected final String name;
    protected final int prefetchCapacity;

    public TopicSubscriptionImpl(
            TopicClientImpl client,
            CheckedConsumer<TopicEventImpl> handler,
            EventAcquisition<TopicSubscriptionImpl> eventAcquisition,
            int prefetchCapacity,
            long startPosition,
            String name)
    {
        super(eventAcquisition, prefetchCapacity);
        this.prefetchCapacity = prefetchCapacity;
        this.client = client;
        if (handler != null)
        {
            // default behavior for managed subscriptions
            this.handler = handler
                    .andThen(this::recordProcessedEvent)
                    .andOnExceptionRetry(MAX_HANDLING_RETRIES, this::logRetry)
                    .andOnException(this::logExceptionAndClose);
        }
        this.startPosition = startPosition;
        this.name = name;
        this.lastProcessedEventPosition = startPosition;
        this.lastAcknowledgedPosition = startPosition;
    }

    @Override
    public boolean isManagedSubscription()
    {
        return handler != null;
    }

    @Override
    public int poll()
    {
        return pollEvents(handler);
    }

    @Override
    public int poll(TopicEventHandler taskHandler)
    {
        final CheckedConsumer<TopicEventImpl> consumer = (e) -> taskHandler.handle(e, e);
        return pollEvents(consumer
                .andThen(this::recordProcessedEvent)
                .andOnException(this::logExceptionAndPropagate));
    }

    @Override
    public int pollEvents(CheckedConsumer<TopicEventImpl> pollHandler)
    {

        // ensuring at most one thread polls at a time which is the guarantee we give for
        // topic subscriptions
        if (processingFlag.compareAndSet(false, true))
        {
            try
            {
                return super.pollEvents(pollHandler);
            }
            finally
            {
                processingFlag.set(false);
            }
        }
        else
        {
            return 0;
        }
    }

    protected void logExceptionAndClose(TopicEventImpl event, Exception e)
    {
        logEventHandlingError(e, event, "Closing subscription.");
        this.closeAsync();
    }

    protected void logExceptionAndPropagate(TopicEventImpl event, Exception e)
    {
        logEventHandlingError(e, event, "Propagating exception to caller.");
        throw new RuntimeException(e);
    }

    protected void logRetry(TopicEventImpl event, Exception e)
    {
        logEventHandlingError(e, event, "Retrying.");
    }

    public CheckedConsumer<TopicEventImpl> getHandler()
    {
        return handler;
    }

    @Override
    protected Long requestNewSubscription()
    {
        return client.createTopicSubscription()
                .startPosition(startPosition)
                .prefetchCapacity(prefetchCapacity)
                .name(name)
                .execute();
    }

    @Override
    protected void requestSubscriptionClose()
    {
        acknowledgeLastProcessedEvent();

        client.closeTopicSubscription()
            .id(id)
            .execute();
    }

    @Override
    protected void onEventsPolled(int numEvents)
    {
        if (isOpen())
        {
            acknowledgeLastProcessedEvent();
        }
    }

    protected void acknowledgeLastProcessedEvent()
    {
        if (lastProcessedEventPosition > lastAcknowledgedPosition)
        {
            client.acknowledgeEvent()
                .subscriptionName(name)
                .ackPosition(lastProcessedEventPosition)
                .execute();

            lastAcknowledgedPosition = lastProcessedEventPosition;
        }
    }

    protected void recordProcessedEvent(TopicEventImpl event)
    {
        this.lastProcessedEventPosition = event.getEventPosition();
    }

    protected void logEventHandlingError(Exception e, TopicEventImpl event, String resolution)
    {
        LOGGER.error("Topic subscription " + name + ": Unhandled exception during handling of event " +
                formatEvent(event) + ". " + resolution, e);
    }

    protected String formatEvent(TopicEventImpl event)
    {
        return String.format("[position=%s, key=%s, type=%s, content=%s]", event.getEventPosition(), event.getEventKey(), event.getEventType(), event.getJson());
    }

    @Override
    public int getTopicId()
    {
        return client.getTopicId();
    }

}
