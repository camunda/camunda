package org.camunda.tngp.broker.logstreams.processor;

import java.util.Objects;

import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;

@FunctionalInterface
public interface MetadataFilter
{
    boolean applies(BrokerEventMetadata metadata);

    default MetadataFilter and(MetadataFilter other)
    {
        Objects.requireNonNull(other);
        return (e) -> this.applies(e) && other.applies(e);
    }
}
