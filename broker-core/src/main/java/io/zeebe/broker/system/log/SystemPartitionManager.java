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
package io.zeebe.broker.system.log;

import java.time.Duration;

import io.zeebe.broker.clustering.management.PartitionManager;
import io.zeebe.broker.logstreams.LogStreamServiceNames;
import io.zeebe.broker.logstreams.processor.*;
import io.zeebe.broker.system.SystemConfiguration;
import io.zeebe.broker.system.SystemServiceNames;
import io.zeebe.broker.system.executor.ScheduledCommand;
import io.zeebe.broker.system.executor.ScheduledExecutor;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.servicecontainer.*;
import io.zeebe.transport.ServerTransport;

public class SystemPartitionManager implements Service<SystemPartitionManager>
{
    private ServiceStartContext serviceContext;

    private final Injector<ServerTransport> clientApiTransportInjector = new Injector<>();
    private final Injector<ScheduledExecutor> executorInjector = new Injector<>();
    private final Injector<PartitionManager> partitionManagerInjector = new Injector<>();

    private final SystemConfiguration systemConfiguration;

    private PartitionManager partitionManager;
    private ServerTransport clientApiTransport;
    private ScheduledExecutor executor;

    private final ServiceGroupReference<LogStream> logStreamsGroupReference = ServiceGroupReference.<LogStream>create()
        .onAdd((name, stream) -> addSystemPartition(stream, name))
        .build();

    private ScheduledCommand command;

    public SystemPartitionManager(SystemConfiguration systemConfiguration)
    {
        this.systemConfiguration = systemConfiguration;
    }

    public void addSystemPartition(LogStream logStream, ServiceName<LogStream> serviceName)
    {
        final PendingPartitionsIndex partitionsIndex = new PendingPartitionsIndex();
        final TopicsIndex topicsIndex = new TopicsIndex();

        final TypedStreamEnvironment streamEnvironment = new TypedStreamEnvironment(logStream, clientApiTransport.getOutput());

        final ResolvePendingPartitionsCommand cmd =
                new ResolvePendingPartitionsCommand(
                        partitionsIndex,
                        partitionManager,
                        streamEnvironment.buildStreamReader(),
                        streamEnvironment.buildStreamWriter());

        final TypedStreamProcessor streamProcessor =
                buildSystemStreamProcessor(
                        streamEnvironment,
                        partitionManager,
                        topicsIndex,
                        partitionsIndex,
                        Duration.ofSeconds(systemConfiguration.getPartitionCreationTimeoutSeconds()));
        command = executor.scheduleAtFixedRate(() -> streamProcessor.runAsync(cmd), Duration.ofMillis(100));

        final StreamProcessorService streamProcessorService = new StreamProcessorService(
            "system",
            StreamProcessorIds.SYSTEM_PROCESSOR_ID,
            streamProcessor)
            .eventFilter(streamProcessor.buildTypeFilter());

        serviceContext.createService(SystemServiceNames.SYSTEM_PROCESSOR, streamProcessorService)
            .dependency(serviceName, streamProcessorService.getSourceStreamInjector())
            .dependency(serviceName, streamProcessorService.getTargetStreamInjector())
            .dependency(LogStreamServiceNames.SNAPSHOT_STORAGE_SERVICE, streamProcessorService.getSnapshotStorageInjector())
            .dependency(SystemServiceNames.ACTOR_SCHEDULER_SERVICE, streamProcessorService.getActorSchedulerInjector())
            .install();
    }

    public static TypedStreamProcessor buildSystemStreamProcessor(
            TypedStreamEnvironment streamEnvironment,
            PartitionManager partitionManager,
            TopicsIndex topicsIndex,
            PendingPartitionsIndex partitionsIndex,
            Duration creationExpiration)
    {
        final PartitionIdGenerator idGenerator = new PartitionIdGenerator();

        return streamEnvironment.newStreamProcessor()
            .onEvent(EventType.TOPIC_EVENT, TopicState.CREATE, new CreateTopicProcessor(topicsIndex, idGenerator))
            .onEvent(EventType.PARTITION_EVENT, PartitionState.CREATE, new CreatePartitionProcessor(partitionManager, partitionsIndex, creationExpiration))
            .onEvent(EventType.PARTITION_EVENT, PartitionState.CREATE_COMPLETE, new CompletePartitionProcessor(partitionsIndex))
            .onEvent(EventType.PARTITION_EVENT, PartitionState.CREATED, new PartitionCreatedProcessor(topicsIndex, streamEnvironment.buildStreamReader()))
            .onEvent(EventType.PARTITION_EVENT, PartitionState.CREATE_EXPIRE, new ExpirePartitionCreationProcessor(partitionsIndex, idGenerator))
            .withStateResource(topicsIndex.getRawMap())
            .withStateResource(partitionsIndex.getRawMap())
            .withStateResource(idGenerator)
            .build();
    }

    @Override
    public void start(ServiceStartContext startContext)
    {
        this.serviceContext = startContext;
        this.clientApiTransport = clientApiTransportInjector.getValue();
        this.executor = executorInjector.getValue();
        this.partitionManager = getPartitionManagerInjector().getValue();
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        if (command != null)
        {
            command.cancel();
        }
    }

    @Override
    public SystemPartitionManager get()
    {
        return this;
    }

    public ServiceGroupReference<LogStream> getLogStreamsGroupReference()
    {
        return logStreamsGroupReference;
    }

    public Injector<ServerTransport> getClientApiTransportInjector()
    {
        return clientApiTransportInjector;
    }

    public Injector<ScheduledExecutor> getExecutorInjector()
    {
        return executorInjector;
    }

    public Injector<PartitionManager> getPartitionManagerInjector()
    {
        return partitionManagerInjector;
    }

}
