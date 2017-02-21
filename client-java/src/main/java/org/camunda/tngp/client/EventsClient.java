package org.camunda.tngp.client;

import org.camunda.tngp.client.event.PollableTopicSubscriptionBuilder;
import org.camunda.tngp.client.event.TaskTopicSubscriptionBuilder;
import org.camunda.tngp.client.event.TopicSubscriptionBuilder;

public interface EventsClient
{

    /**
     * @param topicId the id of the topic to subscribe to
     * @return a builder for a general purpose event subscription and managed event handling
     */
    TopicSubscriptionBuilder newSubscription(int topicId);

    /**
     * @param topicId the id of the topic to subscribe to
     * @return a builder for a general purpose event subscription and manual event handling
     */
    PollableTopicSubscriptionBuilder newPollableSubscription(int topicId);

    /**
     * @param topicId the id of the topic to subscribe to
     * @return a builder for an event subscription on a task topic that allows registering
     *   specific event handlers for well known task events. Handler invocation is managed.
     */
    TaskTopicSubscriptionBuilder newTaskTopicSubscription(int topicId);
}
