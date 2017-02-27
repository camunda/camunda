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

    protected CheckedConsumer<TopicEventImpl> handler;
    protected final TopicClientImpl client;

    protected AtomicBoolean processingFlag = new AtomicBoolean(false);

    public TopicSubscriptionImpl(
            TopicClientImpl client,
            CheckedConsumer<TopicEventImpl> handler,
            EventAcquisition<TopicSubscriptionImpl> eventAcquisition,
            int prefetchSize)
    {
        super(eventAcquisition, prefetchSize);
        this.client = client;
        this.handler = handler;
    }

    @Override
    public boolean isManagedSubscription()
    {
        return handler != null;
    }

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

    public CheckedConsumer<TopicEventImpl> getHandler()
    {
        return handler;
    }

    @Override
    protected Long requestNewSubscription()
    {
        return client.createTopicSubscription().execute();
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
}
