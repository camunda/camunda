package io.zeebe.client.task;

/**
 * Represents the subscription to tasks of a certain topic. When a subscription is open,
 * the client continuously receives tasks from the broker and hands them to a registered
 * {@link TaskHandler}.
 *
 * @author Lindhauer
 */
public interface TaskSubscription
{

    /**
     * @return true if this subscription is currently active and tasks are received for it
     */
    boolean isOpen();

    /**
     * @return true if this subscription is not open and is not in the process of opening or closing
     */
    boolean isClosed();

    /**
     * Closes this subscription and stops receiving new tasks.
     * Blocks until all previously received tasks have been
     * handed to a handler.
     */
    void close();
}
