package org.camunda.tngp.client.event;

public interface TaskTopicSubscriptionBuilder
{

    /**
     * Registers a handler that handles all types of events that don't have a
     * specific handler registered for.
     *
     * @param handler the handler to register
     * @return this builder
     */
    TaskTopicSubscriptionBuilder defaultHandler(TopicEventHandler handler);

    /**
     * Registers a handler for all events of type {@link TopicEventType#TASK}
     *
     * @param handler the handler to register
     * @return this builder
     */
    TaskTopicSubscriptionBuilder taskEventHandler(TaskEventHandler handler);

    /**
     * Opens a new topic subscription with the defined parameters.
     *
     * @return a new subscription
     */
    TopicSubscription open();

}
