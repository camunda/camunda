package org.camunda.tngp.client.task;

/**
 * Represents the subscription to tasks of a certain topic. When a subscription is open,
 * the client continuously receives tasks from the broker. Such tasks can be handled by calling
 * the {@link #poll(TaskHandler)} method.
 *
 * @author Lindhauer
 */
public interface PollableTaskSubscription
{

    boolean isOpen();

    /**
     * Closes the subscription. Blocks until all remaining tasks have been handled.
     */
    void close();

    /**
     * Calls the provided {@link TaskHandler} for a number of tasks that fulfill the subscriptions definition.
     *
     * @return the number of handled tasks
     */
    int poll(TaskHandler taskHandler);
}
