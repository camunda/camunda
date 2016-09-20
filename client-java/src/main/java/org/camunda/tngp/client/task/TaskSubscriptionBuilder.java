package org.camunda.tngp.client.task;

import java.util.concurrent.TimeUnit;

public interface TaskSubscriptionBuilder
{

    /**
     * Sets the task type to subscribe to. Must not be null.
     */
    TaskSubscriptionBuilder taskType(String taskType);

    /**
     * Sets the lock duration for which subscribed tasks will be
     * exclusively locked for this task client.
     *
     * @param lockTime in milliseconds
     */
    TaskSubscriptionBuilder lockTime(long lockTime);

    /**
     * Sets the lock duration for which subscribed tasks will be
     * exclusively locked for this task client.
     *
     * @param lockTime in the provided time unit
     */
    TaskSubscriptionBuilder lockTime(long lockTime, TimeUnit timeUnit);

    /**
     * Sets the task queue id to subscribe to.
     */
    TaskSubscriptionBuilder taskQueueId(int taskQueueId);

    /**
     * Sets the {@link TaskHandler} that is going to receive
     * callbacks for tasks that fulfill this subscription.
     */
    TaskSubscriptionBuilder handler(TaskHandler handler);

    /**
     * Opens a new {@link PollableTaskSubscription}. Begins receiving
     * tasks from that point on.
     */
    TaskSubscription open();
}
