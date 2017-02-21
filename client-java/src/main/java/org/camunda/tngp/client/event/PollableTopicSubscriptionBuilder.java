package org.camunda.tngp.client.event;

public interface PollableTopicSubscriptionBuilder
{

    /**
     * Opens a new topic subscription with the defined parameters.
     *
     * @return a new subscription
     */
    PollableTopicSubscription open();
}
