package org.camunda.tngp.logstreams.processor;

import org.camunda.tngp.logstreams.LoggedEvent;

public interface StreamProcessor
{
    void onEvent(LoggedEvent event, ControlledEventLogger targetEventLogger) throws Exception;

    default void onOpen(StreamProcessorContext ctx)
    {
        // empty default impl
    }

    default void onClose()
    {
        // empty default impl
    }
}
