package org.camunda.tngp.logstreams.processor;

import java.util.Objects;

import org.camunda.tngp.logstreams.log.LoggedEvent;

@FunctionalInterface
public interface EventFilter
{

    /**
     * @param event
     * @return true to mark an event for processing
     */
    boolean applies(LoggedEvent event);

    default EventFilter and(EventFilter other)
    {
        Objects.requireNonNull(other);
        return (e) -> applies(e) && other.applies(e);
    }
}
