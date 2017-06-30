package io.zeebe.logstreams.processor;

import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.spi.SnapshotSupport;

/**
 * Process events from a log stream.
 */
public interface StreamProcessor
{
    /**
     * Returns the resource which holds the state of the processor.
     *
     * @return the processor state resource
     */
    SnapshotSupport getStateResource();

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
     * Checks if the stream processor is currently suspended. If yes, the stream
     * processor controller stop polling new events until the processor is
     * active again.
     *
     * @return <code>true</code> if the stream processor is suspended
     */
    default boolean isSuspended()
    {
        return false;
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
