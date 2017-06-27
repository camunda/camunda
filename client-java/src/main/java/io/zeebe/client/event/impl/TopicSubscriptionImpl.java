package io.zeebe.client.event.impl;

import java.util.concurrent.atomic.AtomicBoolean;

import io.zeebe.client.event.PollableTopicSubscription;
import io.zeebe.client.event.TopicEventHandler;
import io.zeebe.client.event.TopicSubscription;
import io.zeebe.client.task.impl.subscription.EventAcquisition;
import io.zeebe.client.task.impl.subscription.EventSubscription;
import io.zeebe.client.task.impl.subscription.EventSubscriptionCreationResult;
import io.zeebe.util.CheckedConsumer;

public class TopicSubscriptionImpl
    extends EventSubscription<TopicSubscriptionImpl>
    implements TopicSubscription, PollableTopicSubscription
{

    protected static final int MAX_HANDLING_RETRIES = 2;

    protected CheckedConsumer<TopicEventImpl> handler;
    protected final TopicClientImpl client;

    protected AtomicBoolean processingFlag = new AtomicBoolean(false);
    protected volatile long lastProcessedEventPosition;
    protected long lastAcknowledgedPosition;

    protected final long startPosition;
    protected final boolean forceStart;
    protected final String name;
    protected final int prefetchCapacity;

    public TopicSubscriptionImpl(
            TopicClientImpl client,
            CheckedConsumer<TopicEventImpl> handler,
            int prefetchCapacity,
            long startPosition,
            boolean forceStart,
            String name,
            EventAcquisition<TopicSubscriptionImpl> acquisition)
    {
        super(prefetchCapacity, acquisition);
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
        this.forceStart = forceStart;
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
    protected EventSubscriptionCreationResult requestNewSubscription()
    {
        return client.createTopicSubscription()
                .startPosition(startPosition)
                .prefetchCapacity(prefetchCapacity)
                .name(name)
                .forceStart(forceStart)
                .execute();
    }

    @Override
    protected void requestSubscriptionClose()
    {
        acknowledgeLastProcessedEvent();

        client.closeTopicSubscription()
            .subscriberKey(subscriberKey)
            .execute();
    }

    @Override
    protected void requestEventSourceReplenishment(int eventsProcessed)
    {
        acknowledgeLastProcessedEvent();
    }

    protected void acknowledgeLastProcessedEvent()
    {

        // note: it is important we read lastProcessedEventPosition only once
        //   as it be changed concurrently by an executor thread
        final long positionToAck = lastProcessedEventPosition;

        if (positionToAck > lastAcknowledgedPosition)
        {
            client.acknowledgeEvent()
                .subscriptionName(name)
                .ackPosition(positionToAck)
                .execute();

            lastAcknowledgedPosition = positionToAck;
        }
    }

    protected void recordProcessedEvent(TopicEventImpl event)
    {
        this.lastProcessedEventPosition = event.getEventPosition();
    }

    protected void logEventHandlingError(Exception e, TopicEventImpl event, String resolution)
    {
        LOGGER.error("Topic subscription " + name + ": Unhandled exception during handling of event " + event + ". " + resolution, e);
    }

    @Override
    public int getPartitionId()
    {
        return client.getTopic().getPartitionId();
    }

    @Override
    public String getTopicName()
    {
        return client.getTopic().getTopicName();
    }

    @Override
    public String toString()
    {
        return "TopicSubscriptionImpl [name=" + name + ", subscriberKey=" + subscriberKey + "]";
    }

}
