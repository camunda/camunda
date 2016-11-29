package org.camunda.tngp.logstreams.processor;

import org.camunda.tngp.logstreams.LoggedEvent;

public interface StreamProcessor
{
    default void open(StreamProcessorContext streamProcessorContext)
    {
        // do nothing
    }

    default void close()
    {
        // no nothing
    }

    EventProcessor onEvent(LoggedEvent event);

    default void afterEvent()
    {
        // do nothing
    }
}
