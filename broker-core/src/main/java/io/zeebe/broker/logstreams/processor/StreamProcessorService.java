package io.zeebe.broker.logstreams.processor;

import io.zeebe.broker.Constants;
import io.zeebe.broker.logstreams.BrokerEventMetadata;
import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.processor.EventFilter;
import io.zeebe.logstreams.processor.StreamProcessor;
import io.zeebe.logstreams.processor.StreamProcessorController;
import io.zeebe.logstreams.processor.StreamProcessorErrorHandler;
import io.zeebe.logstreams.spi.SnapshotPositionProvider;
import io.zeebe.logstreams.spi.SnapshotStorage;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.actor.ActorScheduler;

public class StreamProcessorService implements Service<StreamProcessorController>
{
    private final Injector<LogStream> sourceStreamInjector = new Injector<>();
    private final Injector<LogStream> targetStreamInjector = new Injector<>();
    private final Injector<SnapshotStorage> snapshotStorageInjector = new Injector<>();
    private final Injector<ActorScheduler> actorSchedulerInjector = new Injector<>();

    private final String name;
    private final int id;
    private final StreamProcessor streamProcessor;

    protected MetadataFilter customEventFilter;
    protected EventFilter customReprocessingEventFilter;
    protected boolean readOnly;
    protected StreamProcessorErrorHandler errorHandler = (failedEvent, error) -> StreamProcessorErrorHandler.RESULT_REJECT;

    protected final MetadataFilter versionFilter = (m) ->
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

    public StreamProcessorService readOnly(boolean readOnly)
    {
        this.readOnly = readOnly;
        return this;
    }

    public StreamProcessorService snapshotPositionProvider(SnapshotPositionProvider snapshotPositionProvider)
    {
        this.snapshotPositionProvider = snapshotPositionProvider;
        return this;
    }

    public StreamProcessorService errorHandler(StreamProcessorErrorHandler errorHandler)
    {
        this.errorHandler = errorHandler;
        return this;
    }

    @Override
    public void start(ServiceStartContext ctx)
    {
        final LogStream sourceStream = sourceStreamInjector.getValue();
        final LogStream targetStream = targetStreamInjector.getValue();

        final SnapshotStorage snapshotStorage = snapshotStorageInjector.getValue();

        final ActorScheduler actorScheduler = actorSchedulerInjector.getValue();

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
            .actorScheduler(actorScheduler)
            .eventFilter(eventFilter)
            .reprocessingEventFilter(reprocessingEventFilter)
            .errorHandler(errorHandler)
            .readOnly(readOnly)
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

    public Injector<ActorScheduler> getActorSchedulerInjector()
    {
        return actorSchedulerInjector;
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
