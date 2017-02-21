package org.camunda.tngp.client.event;

public interface TopicSubscriptionBuilder
{

    /**
     * Registers a handler that handles all types of events.
     *
     * @param handler the handler to register
     * @return this builder
     */
    TopicSubscriptionBuilder handler(TopicEventHandler handler);

    /**
     * Opens a new topic subscription with the defined parameters.
     *
     * @return a new subscription
     */
    TopicSubscription open();
}
