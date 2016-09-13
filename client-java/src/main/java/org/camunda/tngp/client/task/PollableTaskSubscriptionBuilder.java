package org.camunda.tngp.client.task;

public interface PollableTaskSubscriptionBuilder
{

    /**
     * Sets the task type to subscribe to. Must not be null.
     */
    PollableTaskSubscriptionBuilder taskType(String taskType);

    /**
     * Sets the lock duration for which subscribed tasks will be
     * exclusively locked for this task client.
     */
    PollableTaskSubscriptionBuilder lockTime(long lockTime);

    /**
     * Sets the task queue id to subscribe to.
     */
    PollableTaskSubscriptionBuilder taskQueueId(int taskQueueId);

    /**
     * Opens a new {@link PollableTaskSubscription}. Begins receiving
     * tasks from that point on.
     */
    PollableTaskSubscription open();
}
