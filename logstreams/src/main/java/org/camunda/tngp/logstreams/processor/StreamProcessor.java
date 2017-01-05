package org.camunda.tngp.logstreams.processor;

import org.camunda.tngp.logstreams.log.LoggedEvent;

/**
 * Process events from a log stream.
 */
public interface StreamProcessor
{

    /**
     * Returns a specific processor to process the event which is read from the
     * log stream, if available.
     *
     * @param event
     *            the event to process
     *
     * @return specific processor to process the event, or <code>null</code> if the event can't  be processed
     */
    EventProcessor onEvent(LoggedEvent event);

    /**
     * Callback which is invoked by the controller when an event is processed.
     * An implementation can provide any clean up logic here.
     */
    default void afterEvent()
    {
        // do nothing
    }

    /**
     * Callback which is invoked by the controller when it opens. An
     * implementation can provide any setup logic here.
     */
    default void onOpen(StreamProcessorContext context)
    {
        // do nothing
    }

    /**
     * Callback which is invoked by the controller when it closes. An
     * implementation can provide any clean up logic here.
     */
    default void onClose()
    {
        // no nothing
    }

}
