package org.camunda.tngp.broker.logstreams.processor;

import org.camunda.tngp.broker.Constants;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.broker.system.threads.AgentRunnerServices;
import org.camunda.tngp.logstreams.LogStreams;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.processor.EventFilter;
import org.camunda.tngp.logstreams.processor.StreamProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessorController;
import org.camunda.tngp.logstreams.spi.SnapshotPositionProvider;
import org.camunda.tngp.logstreams.spi.SnapshotStorage;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.util.agent.AgentRunnerService;

public class StreamProcessorService implements Service<StreamProcessorController>
{
    private final Injector<LogStream> sourceStreamInjector = new Injector<>();
    private final Injector<LogStream> targetStreamInjector = new Injector<>();
    private final Injector<SnapshotStorage> snapshotStorageInjector = new Injector<>();
    private final Injector<AgentRunnerServices> agentRunnerServiceInjector = new Injector<>();

    private final String name;
    private final int id;
    private final StreamProcessor streamProcessor;

    protected MetadataFilter customEventFilter;
    protected EventFilter customReprocessingEventFilter;

    protected MetadataFilter versionFilter = (m) ->
    {
        if (m.getProtocolVersion() > Constants.PROTOCOL_VERSION)
        {
            throw new RuntimeException(String.format("Cannot handle event with version newer " +
                    "than what is implemented by broker (%d > %d)", m.getProtocolVersion(), Constants.PROTOCOL_VERSION));
        }

        return true;
    };

    protected SnapshotPositionProvider snapshotPositionProvider;

    private StreamProcessorController streamProcessorController;

    public StreamProcessorService(String name, int id, StreamProcessor streamProcessor)
    {
        this.name = name;
        this.id = id;
        this.streamProcessor = streamProcessor;
    }

    public StreamProcessorService eventFilter(MetadataFilter eventFilter)
    {
        this.customEventFilter = eventFilter;
        return this;
    }

    public StreamProcessorService reprocessingEventFilter(EventFilter reprocessingEventFilter)
    {
        this.customReprocessingEventFilter = reprocessingEventFilter;
        return this;
    }

    public StreamProcessorService snapshotPositionProvider(SnapshotPositionProvider snapshotPositionProvider)
    {
        this.snapshotPositionProvider = snapshotPositionProvider;
        return this;
    }

    @Override
    public void start(ServiceStartContext ctx)
    {
        final LogStream sourceStream = sourceStreamInjector.getValue();
        final LogStream targetStream = targetStreamInjector.getValue();

        final SnapshotStorage snapshotStorage = snapshotStorageInjector.getValue();

        final AgentRunnerService agentRunnerService = agentRunnerServiceInjector.getValue().logStreamProcessorAgentRunnerService();

        MetadataFilter metadataFilter = versionFilter;
        if (customEventFilter != null)
        {
            metadataFilter = metadataFilter.and(customEventFilter);
        }
        final EventFilter eventFilter = new MetadataEventFilter(metadataFilter);

        EventFilter reprocessingEventFilter = new MetadataEventFilter(versionFilter);
        if (customReprocessingEventFilter != null)
        {
            reprocessingEventFilter = reprocessingEventFilter.and(customReprocessingEventFilter);
        }

        streamProcessorController = LogStreams.createStreamProcessor(name, id, streamProcessor)
            .sourceStream(sourceStream)
            .targetStream(targetStream)
            .snapshotStorage(snapshotStorage)
            .snapshotPositionProvider(snapshotPositionProvider)
            .agentRunnerService(agentRunnerService)
            .eventFilter(eventFilter)
            .reprocessingEventFilter(reprocessingEventFilter)
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

    public Injector<SnapshotStorage> getSnapshotStorageInjector()
    {
        return snapshotStorageInjector;
    }

    public Injector<AgentRunnerServices> getAgentRunnerInjector()
    {
        return agentRunnerServiceInjector;
    }

    public Injector<LogStream> getSourceStreamInjector()
    {
        return sourceStreamInjector;
    }

    public Injector<LogStream> getTargetStreamInjector()
    {
        return targetStreamInjector;
    }

    public StreamProcessorController getStreamProcessorController()
    {
        return streamProcessorController;
    }

    protected static class MetadataEventFilter implements EventFilter
    {

        protected final BrokerEventMetadata metadata = new BrokerEventMetadata();
        protected final MetadataFilter metadataFilter;

        public MetadataEventFilter(MetadataFilter metadataFilter)
        {
            this.metadataFilter = metadataFilter;
        }

        @Override
        public boolean applies(LoggedEvent event)
        {
            event.readMetadata(metadata);
            return metadataFilter.applies(metadata);
        }

    }

}
