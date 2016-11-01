package org.camunda.tngp.logstreams.processor;

import org.camunda.tngp.logstreams.LogStream;
import org.camunda.tngp.logstreams.spi.SnapshotSupport;

public interface StreamProcessorContext
{
    LogStream getSourceStream();

    SnapshotSupport getResource(String name);
}
