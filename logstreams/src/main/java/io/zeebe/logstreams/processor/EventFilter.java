package io.zeebe.logstreams.processor;

import java.util.Objects;

import io.zeebe.logstreams.log.LoggedEvent;

/**
 * Implement to control which events should be handled by a {@link StreamProcessor}.
 */
@FunctionalInterface
public interface EventFilter
{

    /**
     * @param event the event to be processed next
     * @return true to mark an event for processing; false to skip it
     * @throws RuntimeException to signal that processing cannot continue
     */
    boolean applies(LoggedEvent event);

    default EventFilter and(EventFilter other)
    {
        Objects.requireNonNull(other);
        return (e) -> applies(e) && other.applies(e);
    }
}
