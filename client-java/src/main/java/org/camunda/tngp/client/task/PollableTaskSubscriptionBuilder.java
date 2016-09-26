package org.camunda.tngp.client.task;

import java.time.Duration;

/**
 * Builds a {@link PollableTaskSubscription} that can be manually polled for execution.
 */
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
     * @param lockDuration in milliseconds
     */
    PollableTaskSubscriptionBuilder lockTime(long lockDuration);

    /**
     * Sets the lock duration for which subscribed tasks will be
     * exclusively locked for this task client.
     *
     * @param lockDuration duration for which tasks are being locked
     */
    PollableTaskSubscriptionBuilder lockTime(Duration lockDuration);

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
