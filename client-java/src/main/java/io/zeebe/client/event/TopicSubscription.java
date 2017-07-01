package io.zeebe.client.event;

public interface TopicSubscription
{

    /**
     * @return true if this subscription is currently active and events are received for it
     */
    boolean isOpen();

    /**
     * @return true if this subscription is not open and is not in the process of opening or closing
     */
    boolean isClosed();

    /**
     * Closes this subscription and stops receiving new events.
     * Blocks until all previously received events have been
     * handed to a handler.
     */
    void close();

}
