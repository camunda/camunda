package org.camunda.tngp.broker.logstreams.processor;

import java.util.Objects;

import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.logstreams.processor.StreamProcessor;

/**
 * Implement to control which events should be handled by a {@link StreamProcessor} based on
 * the event's metadata.
 */
@FunctionalInterface
public interface MetadataFilter
{
    /**
     * @param metadata the metadata of the event to be processed next
     * @return true to mark the event for processing; false to skip it
     * @throws RuntimeException to signal that processing cannot continue
     */
    boolean applies(BrokerEventMetadata metadata);

    default MetadataFilter and(MetadataFilter other)
    {
        Objects.requireNonNull(other);
        return (e) -> this.applies(e) && other.applies(e);
    }
}
