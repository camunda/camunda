package io.zeebe.client.task.impl.subscription;

import io.zeebe.client.impl.cmd.ReceiverAwareResponseResult;
import io.zeebe.transport.RemoteAddress;

public class EventSubscriptionCreationResult implements ReceiverAwareResponseResult
{
    protected final long subscriberKey;
    protected RemoteAddress eventPublisher;

    public EventSubscriptionCreationResult(final long subscriberKey)
    {
        this.subscriberKey = subscriberKey;
    }

    public RemoteAddress getEventPublisher()
    {
        return eventPublisher;
    }

    public long getSubscriberKey()
    {
        return subscriberKey;
    }

    @Override
    public void setReceiver(RemoteAddress receiver)
    {
        this.eventPublisher = receiver;
    }

}
