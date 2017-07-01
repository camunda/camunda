package io.zeebe.client.task.impl.subscription;

import io.zeebe.client.event.impl.TopicEventImpl;

public interface SubscribedEventHandler
{

    /**
     * @return true if event could be successfully handled; false, if it should be retried later
     */
    boolean onEvent(long subscriberKey, TopicEventImpl event);
}
