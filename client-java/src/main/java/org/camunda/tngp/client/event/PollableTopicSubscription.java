package org.camunda.tngp.client.event;

public interface PollableTopicSubscription
{
    /**
     * @return true if this subscription currently receives events
     */
    boolean isOpen();

    /**
     * @return true if this subscription is not open and is not in the process of opening or closing
     */
    boolean isClosed();

    /**
     * Closes the subscription. Blocks until all pending events have been handled.
     */
    void close();

    /**
     * Handles currently pending events by invoking the supplied event handler.
     *
     * @param eventHandler the handler that is invoked for each event
     * @return number of handled events
     */
    int poll(TopicEventHandler eventHandler);

}
