package org.camunda.tngp.logstreams.processor;

import org.camunda.tngp.logstreams.log.LogStreamWriter;

/**
 * Process an event from a log stream. An implementation may be specified for
 * one type of event.
 */
public interface EventProcessor
{
    /**
     * Process the event. Do no execute any side effect, or write an event, or
     * update the internal state.
     */
    void processEvent();

    /**
     * (Optional) Execute the side effects which are caused by the processed
     * event. A side effect can be e.g., the reply of a client request. Note
     * that the controller may invoke this method multiple times if the
     * execution fails.
     *
     * @return <code>true</code>, if the execution completes successfully or no
     *         side effect was executed.
     */
    default boolean executeSideEffects()
    {
        return true;
    }

    /**
     * (Optional) Write an event to the target log stream that is caused by the
     * processed event. Note that the controller may invoke this method multiple
     * times if the write operation fails.
     *
     * @param writer
     *            the log stream writer to write the event to the target log
     *            stream.
     *
     * @return
     *         <li>the position of the written event, or</li>
     *         <li>zero, if no event was written, or</li>
     *         <li>a negate value, if the write operation fails.</li>
     */
    default long writeEvent(LogStreamWriter writer)
    {
        return 0;
    }

    /**
     * (Optional) Update the internal state of the processor based on the
     * processed event.
     */
    default void updateState()
    {
        // do nothing
    }

}
