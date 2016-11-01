package org.camunda.tngp.logstreams.processor;

import org.camunda.tngp.logstreams.spi.SnapshotSupport;

public class StreamProcessorResource
{
    protected final String name;
    protected final SnapshotSupport resource;

    public StreamProcessorResource(String name, SnapshotSupport resource)
    {
        this.name = name;
        this.resource = resource;
    }

    public String getName()
    {
        return name;
    }

    public SnapshotSupport getResource()
    {
        return resource;
    }
}
