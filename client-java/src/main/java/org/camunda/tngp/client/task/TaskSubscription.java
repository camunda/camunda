package org.camunda.tngp.client.task;

/**
 * Represents the subscription to tasks of a certain topic. When a subscription is open,
 * the client continuously receives tasks from the broker and hands them to a registered
 * {@link TaskHandler}.
 *
 * @author Lindhauer
 */
public interface TaskSubscription
{

    boolean isOpen();

    /**
     * Closes this subscription and stops receiving new tasks.
     * Blocks until all previously received tasks have been
     * handed to a handler.
     */
    void close();
}
