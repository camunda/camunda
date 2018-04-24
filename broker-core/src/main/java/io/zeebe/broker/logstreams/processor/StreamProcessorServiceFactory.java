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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.impl.service.StreamProcessorService;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.processor.EventFilter;
import io.zeebe.logstreams.processor.StreamProcessor;
import io.zeebe.logstreams.spi.SnapshotStorage;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.servicecontainer.*;
import io.zeebe.util.EnsureUtil;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.future.ActorFuture;

public class StreamProcessorServiceFactory implements Service<StreamProcessorServiceFactory>
{
    private static final Duration SNAPSHOT_INTERVAL = Duration.ofMinutes(15);

    private final ServiceContainer serviceContainer;

    private ActorScheduler actorScheduler;

    public StreamProcessorServiceFactory(ServiceContainer serviceContainer)
    {
        this.serviceContainer = serviceContainer;
    }

    @Override
    public void start(ServiceStartContext startContext)
    {
        this.actorScheduler = startContext.getScheduler();
    }

    @Override
    public StreamProcessorServiceFactory get()
    {
        return this;
    }

    public Builder createService(Partition partition, ServiceName<Partition> serviceName)
    {
        return new Builder(partition, serviceName);
    }

    public class Builder
    {
        private final LogStream logStream;
        private final SnapshotStorage snapshotStorage;

        private String processorName;
        private int processorId = -1;
        private StreamProcessor streamProcessor;
        private final List<ServiceName<?>> additionalDependencies = new ArrayList<>();

        protected MetadataFilter customEventFilter;
        protected boolean readOnly = false;


        public Builder(Partition partition, ServiceName<Partition> serviceName)
        {
            this.logStream = partition.getLogStream();
            snapshotStorage = partition.getSnapshotStorage();
            this.additionalDependencies.add(serviceName);
        }

        public Builder processorId(int processorId)
        {
            this.processorId = processorId;
            return this;
        }

        public Builder processorName(String processorName)
        {
            this.processorName = processorName;
            return this;
        }

        public Builder processor(StreamProcessor processor)
        {
            this.streamProcessor = processor;
            return this;
        }

        public Builder processor(TypedStreamProcessor processor)
        {
            this.streamProcessor = processor;
            this.customEventFilter = processor.buildTypeFilter();
            return this;
        }

        public Builder eventFilter(MetadataFilter eventFilter)
        {
            this.customEventFilter = eventFilter;
            return this;
        }

        public Builder readOnly(boolean readOnly)
        {
            this.readOnly = readOnly;
            return this;
        }

        public Builder additionalDependencies(ServiceName<?>... additionalDependencies)
        {
            for (ServiceName<?> serviceName : additionalDependencies)
            {
                this.additionalDependencies.add(serviceName);
            }
            return this;
        }

        public ActorFuture<StreamProcessorService> build()
        {
            EnsureUtil.ensureNotNull("stream processor", streamProcessor);
            EnsureUtil.ensureNotNullOrEmpty("processor name", processorName);
            EnsureUtil.ensureGreaterThan("process id", processorId, -1);

            MetadataFilter metadataFilter = new VersionFilter();
            if (customEventFilter != null)
            {
                metadataFilter = metadataFilter.and(customEventFilter);
            }
            final EventFilter eventFilter = new MetadataEventFilter(metadataFilter);

            return LogStreams.createStreamProcessor(processorName, processorId, streamProcessor)
                .actorScheduler(actorScheduler)
                .serviceContainer(serviceContainer)
                .snapshotStorage(snapshotStorage)
                .snapshotPeriod(SNAPSHOT_INTERVAL)
                .logStream(logStream)
                .eventFilter(eventFilter)
                .readOnly(readOnly)
                .additionalDependencies(additionalDependencies)
                .build();
        }
    }

    private static class MetadataEventFilter implements EventFilter
    {

        protected final RecordMetadata metadata = new RecordMetadata();
        protected final MetadataFilter metadataFilter;

        MetadataEventFilter(MetadataFilter metadataFilter)
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

    private final class VersionFilter implements MetadataFilter
    {
        @Override
        public boolean applies(RecordMetadata m)
        {
            if (m.getProtocolVersion() > Protocol.PROTOCOL_VERSION)
            {
                throw new RuntimeException(String.format("Cannot handle event with version newer " +
                        "than what is implemented by broker (%d > %d)", m.getProtocolVersion(), Protocol.PROTOCOL_VERSION));
            }

            return true;
        }
    }

}
