package io.zeebe.client;

import io.zeebe.client.event.PollableTopicSubscriptionBuilder;
import io.zeebe.client.event.TopicSubscriptionBuilder;

public interface TopicClient
{

    /**
     * @return a builder for an event subscription and managed event handling
     */
    TopicSubscriptionBuilder newSubscription();

    /**
     * @return a builder for an event subscription and manual event handling
     */
    PollableTopicSubscriptionBuilder newPollableSubscription();

}
