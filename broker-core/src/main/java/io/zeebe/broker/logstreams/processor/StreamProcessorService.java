/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.logstreams.processor;

import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.processor.EventFilter;
import io.zeebe.logstreams.processor.StreamProcessor;
import io.zeebe.logstreams.processor.StreamProcessorController;
import io.zeebe.logstreams.spi.SnapshotStorage;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.ActorScheduler;

import java.time.Duration;

public class StreamProcessorService implements Service<StreamProcessorController>
{
    private final Injector<LogStream> logStreamInjector = new Injector<>();
    private final Injector<SnapshotStorage> snapshotStorageInjector = new Injector<>();
    private final Injector<ActorScheduler> actorSchedulerInjector = new Injector<>();

    private final String name;
    private final int id;
    private final StreamProcessor streamProcessor;

    protected MetadataFilter customEventFilter;
    protected EventFilter customReprocessingEventFilter;
    protected boolean readOnly;

    protected final MetadataFilter versionFilter = (m) ->
    {
        if (m.getProtocolVersion() > Protocol.PROTOCOL_VERSION)
        {
            throw new RuntimeException(String.format("Cannot handle event with version newer " +
                    "than what is implemented by broker (%d > %d)", m.getProtocolVersion(), Protocol.PROTOCOL_VERSION));
        }

        return true;
    };


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

    @Override
    public void start(ServiceStartContext ctx)
    {
        final LogStream logStream = logStreamInjector.getValue();

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
            .logStream(logStream)
            .snapshotStorage(snapshotStorage)
            .snapshotPeriod(Duration.ofMinutes(15))
            .actorScheduler(actorScheduler)
            .eventFilter(eventFilter)
            .reprocessingEventFilter(reprocessingEventFilter)
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

    public Injector<LogStream> getLogStreamInjector()
    {
        return logStreamInjector;
    }

    public StreamProcessorController getStreamProcessorController()
    {
        return streamProcessorController;
    }

    public String getName()
    {
        return name;
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
