package org.camunda.tngp.client;

import org.camunda.tngp.client.event.PollableTopicSubscriptionBuilder;
import org.camunda.tngp.client.event.TopicSubscriptionBuilder;

public interface TopicClient
{

    /**
     * @return a builder for a general purpose event subscription and managed event handling
     */
    TopicSubscriptionBuilder newSubscription();

    /**
     * @return a builder for a general purpose event subscription and manual event handling
     */
    PollableTopicSubscriptionBuilder newPollableSubscription();
}
