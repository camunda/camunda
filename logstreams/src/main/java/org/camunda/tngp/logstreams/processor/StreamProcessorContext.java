package org.camunda.tngp.logstreams.processor;

import java.util.HashMap;
import java.util.Map;

import org.camunda.tngp.logstreams.LogStream;
import org.camunda.tngp.logstreams.spi.SnapshotSupport;

public class StreamProcessorContext
{
    protected final Map<String, SnapshotSupport> resources = new HashMap<>();

    protected LogStream sourceStream;

    public SnapshotSupport getResource(String name)
    {
        return resources.get(name);
    }

    public LogStream getSourceStream()
    {
        return sourceStream;
    }

}
