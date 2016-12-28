package org.camunda.tngp.broker.logstreams.processor;

import org.camunda.tngp.logstreams.LogStreams;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.processor.StreamProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessorController;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;

public class StreamProcessorService implements Service<StreamProcessorController>
{
    private final Injector<LogStream> sourceStreamInjector = new Injector<>();
    private final Injector<LogStream> targetStreamInjector = new Injector<>();

    private final String name;
    private final int id;
    private final StreamProcessor streamProcessor;

    private StreamProcessorController streamProcessorController;

    public StreamProcessorService(String name, int id, StreamProcessor streamProcessor)
    {
        this.name = name;
        this.id = id;
        this.streamProcessor = streamProcessor;
    }

    @Override
    public void start(ServiceStartContext ctx)
    {
        final LogStream sourceStream = sourceStreamInjector.getValue();
        final LogStream targetStream = targetStreamInjector.getValue();

        streamProcessorController = LogStreams.createStreamProcessor(name, id, streamProcessor)
            .sourceStream(sourceStream)
            .targetStream(targetStream)
            .build();

        ctx.async(streamProcessorController.openAsync());
    }

    @Override
    public StreamProcessorController get()
    {
        return streamProcessorController;
    }

    @Override
    public void stop(ServiceStopContext ctx)
    {
        ctx.async(streamProcessorController.closeAsync());
    }

    public Injector<LogStream> getSourceStreamInjector()
    {
        return sourceStreamInjector;
    }

    public Injector<LogStream> getTargetStreamInjector()
    {
        return targetStreamInjector;
    }

}
