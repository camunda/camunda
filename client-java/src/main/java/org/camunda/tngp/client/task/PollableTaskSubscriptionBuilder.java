package org.camunda.tngp.client.task;

import java.util.concurrent.TimeUnit;

public interface PollableTaskSubscriptionBuilder
{

    /**
     * Sets the task type to subscribe to. Must not be null.
     */
    PollableTaskSubscriptionBuilder taskType(String taskType);

    /**
     * Sets the lock duration for which subscribed tasks will be
     * exclusively locked for this task client.
     *
     * @param lockTime in milliseconds
     */
    PollableTaskSubscriptionBuilder lockTime(long lockTime);

    /**
     * Sets the lock duration for which subscribed tasks will be
     * exclusively locked for this task client.
     *
     * @param lockTime in the provided time unit
     */
    PollableTaskSubscriptionBuilder lockTime(long lockTime, TimeUnit timeUnit);

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
