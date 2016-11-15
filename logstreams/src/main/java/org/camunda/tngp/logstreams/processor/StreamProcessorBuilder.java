package org.camunda.tngp.logstreams.processor;

import java.util.ArrayList;
import java.util.List;

import org.agrona.concurrent.status.AtomicLongPosition;
import org.camunda.tngp.logstreams.BufferedLogStreamReader;
import org.camunda.tngp.logstreams.LogStreamWriter;
import org.camunda.tngp.logstreams.LogStream;
import org.camunda.tngp.logstreams.impl.processor.StreamProcessorContextImpl;
import org.camunda.tngp.logstreams.impl.processor.StreamProcessorControllerImpl;
import org.camunda.tngp.logstreams.spi.SnapshotStorage;
import org.camunda.tngp.logstreams.spi.SnapshotSupport;

public class StreamProcessorBuilder
{
    protected LogStream sourceStream;
    protected LogStream targetStream;
    protected StreamProcessor streamProcessor;
    protected RecoveryHandler recoveryHandler;
    protected String name;
    protected List<StreamProcessorResource> streamProcessorResources = new ArrayList<>();
    protected SnapshotStorage snapshotStorage;

    public StreamProcessorBuilder(String name, StreamProcessor streamProcessor)
    {
        this.name = name;
        this.streamProcessor = streamProcessor;
    }

    public StreamProcessorBuilder sourceStream(LogStream stream)
    {
        this.sourceStream = stream;
        return this;
    }

    public StreamProcessorBuilder targetStream(LogStream stream)
    {
        this.targetStream = stream;
        return this;
    }

    public StreamProcessorBuilder recoveryHandler(RecoveryHandler recoveryHandler)
    {
        this.recoveryHandler = recoveryHandler;
        return this;
    }

    public StreamProcessorBuilder resource(String name, SnapshotSupport resource)
    {
        streamProcessorResources.add(new StreamProcessorResource(name, resource));
        return this;
    }


    public StreamProcessorBuilder snapshotStorage(SnapshotStorage snapshotStorage)
    {
        this.snapshotStorage = snapshotStorage;
        return this;
    }

    public StreamProcessorController build()
    {
        final StreamProcessorContextImpl ctx = new StreamProcessorContextImpl();

        ctx.setTargetStream(targetStream);
        ctx.setSourceStream(sourceStream);
        ctx.setStreamProcessor(streamProcessor);
        ctx.setStreamReader(new BufferedLogStreamReader(sourceStream));
        ctx.setEventLogger(new LogStreamWriter(targetStream));
        ctx.setPosition(new AtomicLongPosition());
        ctx.setSnapshotStorage(snapshotStorage);

        return new StreamProcessorControllerImpl(name, ctx);
    }

}
