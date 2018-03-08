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

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.management.PartitionManager;
import io.zeebe.broker.logstreams.LogStreamServiceNames;
import io.zeebe.broker.logstreams.processor.*;
import io.zeebe.broker.system.SystemConfiguration;
import io.zeebe.broker.system.SystemServiceNames;
import io.zeebe.broker.system.deployment.processor.PartitionCollector;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.servicecontainer.*;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerTransport;
import io.zeebe.util.sched.future.ActorFuture;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

public class SystemPartitionManager implements Service<SystemPartitionManager>
{
    public static final String CREATE_TOPICS_PROCESSOR = "create-topics";
    public static final String COLLECT_PARTITIONS_PROCESSOR = "collect-partitions";

    private ServiceStartContext serviceContext;

    private final Injector<ServerTransport> clientApiTransportInjector = new Injector<>();
    private final Injector<PartitionManager> partitionManagerInjector = new Injector<>();

    private final SystemConfiguration systemConfiguration;

    private PartitionManager partitionManager;
    private ServerTransport clientApiTransport;

    private final ServiceGroupReference<LogStream> logStreamsGroupReference = ServiceGroupReference.<LogStream>create()
        .onAdd((name, stream) -> addSystemPartition(stream, name))
        .onRemove((name, stream) -> removeSystemPartition())
        .build();

    private ResolvePendingPartitionsCommand resolvePendingPartitionsCommand;

    private AtomicReference<PartitionResponder> partitionResponderRef = new AtomicReference<>();

    public SystemPartitionManager(SystemConfiguration systemConfiguration)
    {
        this.systemConfiguration = systemConfiguration;
    }

    public void addSystemPartition(LogStream logStream, ServiceName<LogStream> serviceName)
    {
        Loggers.SYSTEM_LOGGER.debug("Add system partiton");
        final ServerOutput serverOutput = clientApiTransport.getOutput();

        final TypedStreamEnvironment streamEnvironment = new TypedStreamEnvironment(logStream, serverOutput);

        installCreateTopicProcessor(serviceName, streamEnvironment);
        installPartitionCollectorProcessor(serviceName, serverOutput, streamEnvironment);
    }

    private void installPartitionCollectorProcessor(
            ServiceName<LogStream> logStreamName,
            final ServerOutput serverOutput,
            final TypedStreamEnvironment streamEnvironment)
    {
        final PartitionResponder partitionResponder = new PartitionResponder(serverOutput);
        final TypedStreamProcessor streamProcessor = buildPartitionResponseProcessor(streamEnvironment, partitionResponder);

        final StreamProcessorService streamProcessorService = new StreamProcessorService(
            COLLECT_PARTITIONS_PROCESSOR,
            StreamProcessorIds.SYSTEM_COLLECT_PARTITION_PROCESSOR_ID,
            streamProcessor)
            .eventFilter(streamProcessor.buildTypeFilter());

        serviceContext.createService(SystemServiceNames.systemProcessorName(streamProcessorService.getName()), streamProcessorService)
            .dependency(logStreamName, streamProcessorService.getLogStreamInjector())
            .dependency(LogStreamServiceNames.SNAPSHOT_STORAGE_SERVICE, streamProcessorService.getSnapshotStorageInjector())
            .dependency(SystemServiceNames.ACTOR_SCHEDULER_SERVICE, streamProcessorService.getActorSchedulerInjector())
            .install();


        partitionResponderRef.set(partitionResponder);
    }

    private void installCreateTopicProcessor(
            ServiceName<LogStream> logStreamName,
            final TypedStreamEnvironment streamEnvironment)
    {
        final PendingPartitionsIndex partitionsIndex = new PendingPartitionsIndex();
        final TopicsIndex topicsIndex = new TopicsIndex();

        resolvePendingPartitionsCommand = new ResolvePendingPartitionsCommand(
                partitionsIndex,
                partitionManager,
                streamEnvironment.buildStreamReader(),
                streamEnvironment.buildStreamWriter());

        final TypedStreamProcessor streamProcessor =
                buildTopicCreationProcessor(
                        streamEnvironment,
                        partitionManager,
                        topicsIndex,
                        partitionsIndex,
                        Duration.ofSeconds(systemConfiguration.getPartitionCreationTimeoutSeconds()), resolvePendingPartitionsCommand);

        final StreamProcessorService streamProcessorService = new StreamProcessorService(
            CREATE_TOPICS_PROCESSOR,
            StreamProcessorIds.SYSTEM_CREATE_TOPIC_PROCESSOR_ID,
            streamProcessor)
            .eventFilter(streamProcessor.buildTypeFilter());

        serviceContext.createService(SystemServiceNames.systemProcessorName(streamProcessorService.getName()), streamProcessorService)
            .dependency(logStreamName, streamProcessorService.getLogStreamInjector())
            .dependency(LogStreamServiceNames.SNAPSHOT_STORAGE_SERVICE, streamProcessorService.getSnapshotStorageInjector())
            .dependency(SystemServiceNames.ACTOR_SCHEDULER_SERVICE, streamProcessorService.getActorSchedulerInjector())
            .install();
    }

    private void removeSystemPartition()
    {
        Loggers.SYSTEM_LOGGER.debug("Remove");
        partitionResponderRef.set(null);
    }

    public static TypedStreamProcessor buildTopicCreationProcessor(
            TypedStreamEnvironment streamEnvironment,
            PartitionManager partitionManager,
            TopicsIndex topicsIndex,
            PendingPartitionsIndex partitionsIndex,
            Duration creationExpiration,
            Runnable onOpen)
    {
        final PartitionIdGenerator idGenerator = new PartitionIdGenerator();
        final PartitionCreatorSelectionStrategy creationStrategy = new RoundRobinSelectionStrategy(partitionManager);

        return streamEnvironment.newStreamProcessor()
            .onEvent(EventType.TOPIC_EVENT, TopicState.CREATE, new CreateTopicProcessor(topicsIndex, idGenerator, creationStrategy))
            .onEvent(EventType.PARTITION_EVENT, PartitionState.CREATE, new CreatePartitionProcessor(partitionManager, partitionsIndex, creationExpiration))
            .onEvent(EventType.PARTITION_EVENT, PartitionState.CREATE_COMPLETE, new CompletePartitionProcessor(partitionsIndex))
            .onEvent(EventType.PARTITION_EVENT, PartitionState.CREATED, new PartitionCreatedProcessor(topicsIndex, streamEnvironment.buildStreamReader()))
            .onEvent(EventType.PARTITION_EVENT, PartitionState.CREATE_EXPIRE, new ExpirePartitionCreationProcessor(partitionsIndex, idGenerator, creationStrategy))
            .withStateResource(topicsIndex.getRawMap())
            .withStateResource(partitionsIndex.getRawMap())
            .withStateResource(idGenerator)
            .withListener(new StreamProcessorLifecycleAware()
            {
                @Override
                public void onOpen(TypedStreamProcessor streamProcessor)
                {
                    // TODO check needs to be re-implemented
                    streamProcessor.getActor().runAtFixedRate(Duration.ofMillis(100), onOpen);
                }
            })
            .build();
    }

    public static TypedStreamProcessor buildPartitionResponseProcessor(
            TypedStreamEnvironment streamEnvironment,
            PartitionResponder partitionResponder)
    {
        final PartitionCollector partitionCollector = new PartitionCollector(partitionResponder);

        final TypedEventStreamProcessorBuilder streamProcessorBuilder = streamEnvironment.newStreamProcessor();
        partitionCollector.registerWith(streamProcessorBuilder);
        partitionResponder.registerWith(streamProcessorBuilder);

        return streamProcessorBuilder.build();
    }

    @Override
    public void start(ServiceStartContext startContext)
    {
        this.serviceContext = startContext;
        this.clientApiTransport = clientApiTransportInjector.getValue();
        this.partitionManager = getPartitionManagerInjector().getValue();
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        if (resolvePendingPartitionsCommand != null)
        {
            resolvePendingPartitionsCommand.close();
        }
        partitionResponderRef.set(null);
    }

    @Override
    public SystemPartitionManager get()
    {
        return this;
    }

    public ActorFuture<Void> sendPartitions(int requestStream, long request)
    {
        final PartitionResponder responder = partitionResponderRef.get();

        if (responder != null)
        {
            return responder.sendPartitions(requestStream, request);
        }
        else
        {
            return null;
        }
    }


    public ServiceGroupReference<LogStream> getLogStreamsGroupReference()
    {
        return logStreamsGroupReference;
    }

    public Injector<ServerTransport> getClientApiTransportInjector()
    {
        return clientApiTransportInjector;
    }

    public Injector<PartitionManager> getPartitionManagerInjector()
    {
        return partitionManagerInjector;
    }

}
