package org.camunda.tngp.client.task.impl;

import org.camunda.tngp.client.event.impl.TopicEventImpl;

public interface SubscribedEventHandler
{

    void onEvent(long subscriptionId, TopicEventImpl event);
}
