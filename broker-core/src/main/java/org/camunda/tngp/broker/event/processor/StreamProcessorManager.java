package org.camunda.tngp.broker.event.processor;

import static org.camunda.tngp.broker.logstreams.LogStreamServiceNames.SNAPSHOT_STORAGE_SERVICE;
import static org.camunda.tngp.broker.system.SystemServiceNames.AGENT_RUNNER_SERVICE;

import java.util.concurrent.CompletableFuture;

import org.agrona.collections.Int2ObjectHashMap;
import org.camunda.tngp.broker.logstreams.processor.MetadataFilter;
import org.camunda.tngp.broker.logstreams.processor.StreamProcessorService;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.processor.StreamProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessorController;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.util.AsyncContext;

public class StreamProcessorManager
{

    protected final ServiceStartContext serviceContext;
    protected final Int2ObjectHashMap<LogStreamContext> logStreamById;

    protected final AsyncContext asyncContext;

    public StreamProcessorManager(ServiceStartContext serviceContext, AsyncContext asyncContext)
    {
        this.serviceContext = serviceContext;
        this.logStreamById = new Int2ObjectHashMap<>();
        this.asyncContext = asyncContext;
    }

    public <T extends StreamProcessor> CompletableFuture<StreamProcessorService> createStreamProcessorService(
            ServiceName<LogStream> logStreamName,
            ServiceName<StreamProcessorController> processorName,
            int processorId,
            T streamProcessor)
    {
        return createStreamProcessorService(logStreamName, processorName, processorId, streamProcessor, null);
    }

    public <T extends StreamProcessor> CompletableFuture<StreamProcessorService> createStreamProcessorService(
            ServiceName<LogStream> logStreamName,
            ServiceName<StreamProcessorController> processorName,
            int processorId,
            T streamProcessor,
            MetadataFilter eventFilter)
    {
        final CompletableFuture<StreamProcessorService> future = new CompletableFuture<>();

        final StreamProcessorService streamProcessorService = new StreamProcessorService(
                processorName.getName(),
                processorId,
                streamProcessor);

        if (eventFilter != null)
        {
            streamProcessorService.eventFilter(eventFilter);
        }

        serviceContext.createService(processorName, streamProcessorService)
            .dependency(logStreamName, streamProcessorService.getSourceStreamInjector())
            .dependency(logStreamName, streamProcessorService.getTargetStreamInjector())
            .dependency(SNAPSHOT_STORAGE_SERVICE, streamProcessorService.getSnapshotStorageInjector())
            .dependency(AGENT_RUNNER_SERVICE, streamProcessorService.getAgentRunnerInjector())
            .install()
            .thenApply(v -> future.complete(streamProcessorService));

        return future;
    }

    public CompletableFuture<Void> removeStreamProcessorService(ServiceName<StreamProcessorController> processorName)
    {
        return serviceContext.removeService(processorName);
    }

    public ServiceName<LogStream> getServiceName(int streamId)
    {
        final LogStreamContext context = logStreamById.get(streamId);
        if (context != null)
        {
            return context.serviceName;
        }
        else
        {
            return null;
        }
    }

    public void addLogStream(ServiceName<LogStream> streamName, LogStream logStream)
    {
        asyncContext.runAsync((f) ->
        {
            final LogStreamContext ctx = new LogStreamContext(logStream, streamName);
            logStreamById.put(logStream.getId(), ctx);
        });
    }


    public void removeLogStream(LogStream stream)
    {
        asyncContext.runAsync((f) ->
        {
            logStreamById.remove(stream.getId());
        });
    }

    protected static class LogStreamContext
    {
        protected final LogStream logStream;
        protected final ServiceName<LogStream> serviceName;

        public LogStreamContext(LogStream logStream, ServiceName<LogStream> serviceName)
        {
            this.logStream = logStream;
            this.serviceName = serviceName;
        }
    }
}
