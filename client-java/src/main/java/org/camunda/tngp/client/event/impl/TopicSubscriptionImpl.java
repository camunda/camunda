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

    protected InnerHandler innerHandler = new InnerHandler();
    protected CheckedConsumer<TopicEventImpl> handler;
    protected final TopicClientImpl client;

    protected AtomicBoolean processingFlag = new AtomicBoolean(false);
    protected long lastProcessedEventPosition;
    protected long lastAcknowledgedPosition;

    protected final long startPosition;
    protected final String name;

    public TopicSubscriptionImpl(
            TopicClientImpl client,
            CheckedConsumer<TopicEventImpl> handler,
            EventAcquisition<TopicSubscriptionImpl> eventAcquisition,
            int prefetchSize,
            long startPosition,
            String name)
    {
        super(eventAcquisition, prefetchSize);
        this.client = client;
        this.handler = handler;
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
        return pollEvents((e) -> taskHandler.handle(e, e));
    }

    @Override
    public int pollEvents(CheckedConsumer<TopicEventImpl> pollHandler)
    {

        // ensuring at most one thread polls at a time which is the guarantee we give for
        // topic subscriptions
        if (processingFlag.compareAndSet(false, true))
        {
            innerHandler.wrap(pollHandler);
            try
            {
                return super.pollEvents(innerHandler);
            }
            finally
            {
                innerHandler.wrap(null);
                processingFlag.set(false);
            }
        }
        else
        {
            return 0;
        }
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
                .name(name)
                .execute();
    }

    @Override
    protected void requestSubscriptionClose()
    {
        client.closeTopicSubscription()
            .id(id)
            .execute();
    }

    @Override
    protected void onEventsPolled(int numEvents)
    {
    }

    @Override
    protected int performMaintenance()
    {
        if (!isClosed() && lastProcessedEventPosition > lastAcknowledgedPosition)
        {
            client.acknowledgeEvent()
                .subscriptionId(id)
                .eventPosition(lastProcessedEventPosition)
                .execute();

            lastAcknowledgedPosition = lastProcessedEventPosition;
            return 1;
        }
        else
        {
            return 0;
        }
    }

    protected class InnerHandler implements CheckedConsumer<TopicEventImpl>
    {

        protected CheckedConsumer<TopicEventImpl> wrappedHandler;

        public void wrap(CheckedConsumer<TopicEventImpl> wrappedHandler)
        {
            this.wrappedHandler = wrappedHandler;
        }

        @Override
        public void accept(TopicEventImpl t) throws Exception
        {
            try
            {
                wrappedHandler.accept(t);
            }
            finally
            {
                lastProcessedEventPosition = t.getEventPosition();
            }
        }
    }

}
