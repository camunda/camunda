package org.camunda.tngp.logstreams.processor;

import org.camunda.tngp.logstreams.LogStreamWriter;

public interface EventProcessor
{
    void processEvent();

    default boolean executeSideEffects()
    {
        return true;
    }

    default boolean writeEvents(LogStreamWriter writer)
    {
        return true;
    }

    default void updateState()
    {
        // do nothing
    }
}
