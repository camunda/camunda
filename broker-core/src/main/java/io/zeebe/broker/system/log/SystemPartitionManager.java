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
import java.util.concurrent.atomic.AtomicReference;

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.logstreams.processor.*;
import io.zeebe.broker.system.deployment.processor.PartitionCollector;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.servicecontainer.*;
import io.zeebe.transport.*;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;

public class SystemPartitionManager implements Service<SystemPartitionManager>
{
    public static final String CREATE_TOPICS_PROCESSOR = "create-topics";
    public static final String COLLECT_PARTITIONS_PROCESSOR = "collect-partitions";

    private final ServiceGroupReference<Partition> partitionsGroupReference = ServiceGroupReference.<Partition>create()
        .onAdd((name, partition) -> addSystemPartition(partition, name))
        .onRemove((name, partition) -> removeSystemPartition())
        .build();

    private final Injector<ServerTransport> clientApiTransportInjector = new Injector<>();
    private final Injector<StreamProcessorServiceFactory> streamProcessorServiceFactoryInjector = new Injector<>();
    private final Injector<TopologyManager> topologyManagerInjector = new Injector<>();
    private final Injector<ClientTransport> clientTransportInjector = new Injector<>();

    private final RoundRobinSelectionStrategy nodeSelectionStrategy = new RoundRobinSelectionStrategy();

    private ServerTransport clientApiTransport;
    private StreamProcessorServiceFactory streamProcessorServiceFactory;
    private ClientTransport clientTransport;
    private TopologyManager topologyManager;

    private AtomicReference<PartitionResponder> partitionResponderRef = new AtomicReference<>();

    public void addSystemPartition(Partition partition, ServiceName<Partition> serviceName)
    {
        final ServerOutput serverOutput = clientApiTransport.getOutput();

        final TypedStreamEnvironment streamEnvironment = new TypedStreamEnvironment(partition.getLogStream(), serverOutput);

        installCreateTopicProcessor(partition, serviceName, streamEnvironment);
        installPartitionCollectorProcessor(partition, serviceName, serverOutput, streamEnvironment);
    }

    private void installPartitionCollectorProcessor(Partition partition,
        ServiceName<Partition> partitionServiceName,
        final ServerOutput serverOutput,
        final TypedStreamEnvironment streamEnvironment)
    {
        final PartitionResponder partitionResponder = new PartitionResponder(serverOutput);
        final TypedStreamProcessor streamProcessor = buildPartitionResponseProcessor(streamEnvironment, partitionResponder);

        streamProcessorServiceFactory.createService(partition, partitionServiceName)
            .processor(streamProcessor)
            .processorId(StreamProcessorIds.SYSTEM_COLLECT_PARTITION_PROCESSOR_ID)
            .processorName(COLLECT_PARTITIONS_PROCESSOR)
            .build();

        partitionResponderRef.set(partitionResponder);
    }

    private void installCreateTopicProcessor(Partition partition,
            ServiceName<Partition> partitionServiceName,
            final TypedStreamEnvironment streamEnvironment)
    {
        final PendingPartitionsIndex partitionsIndex = new PendingPartitionsIndex();
        final TopicsIndex topicsIndex = new TopicsIndex();

        final TypedStreamProcessor streamProcessor = buildTopicCreationProcessor(clientTransport,
            topologyManager,
            streamEnvironment,
            nodeSelectionStrategy,
            topicsIndex,
            partitionsIndex,
            Duration.ofSeconds(20));

        streamProcessorServiceFactory.createService(partition, partitionServiceName)
            .processor(streamProcessor)
            .processorId(StreamProcessorIds.SYSTEM_CREATE_TOPIC_PROCESSOR_ID)
            .processorName(CREATE_TOPICS_PROCESSOR)
            .build();
    }

    private void removeSystemPartition()
    {
        partitionResponderRef.set(null);
    }

    public static TypedStreamProcessor buildTopicCreationProcessor(ClientTransport clientTransport,
            TopologyManager topologyManager,
            TypedStreamEnvironment streamEnvironment,
            PartitionCreatorSelectionStrategy nodeSelectionStrategy,
            TopicsIndex topicsIndex,
            PendingPartitionsIndex partitionsIndex,
            Duration creationExpiration)
    {
        final PartitionIdGenerator idGenerator = new PartitionIdGenerator();

        final ResolvePendingPartitionsCommand partitionsCommand = new ResolvePendingPartitionsCommand(partitionsIndex, streamEnvironment.buildStreamReader(), streamEnvironment.buildStreamWriter());

        return streamEnvironment.newStreamProcessor()
            .onEvent(EventType.TOPIC_EVENT, TopicState.CREATE, new CreateTopicProcessor(topicsIndex, idGenerator, nodeSelectionStrategy))
            .onEvent(EventType.PARTITION_EVENT, PartitionState.CREATE, new CreatePartitionProcessor(clientTransport, partitionsIndex, creationExpiration))
            .onEvent(EventType.PARTITION_EVENT, PartitionState.CREATE_COMPLETE, new CompletePartitionProcessor(partitionsIndex))
            .onEvent(EventType.PARTITION_EVENT, PartitionState.CREATED, new PartitionCreatedProcessor(topicsIndex, streamEnvironment.buildStreamReader()))
            .onEvent(EventType.PARTITION_EVENT, PartitionState.CREATE_EXPIRE, new ExpirePartitionCreationProcessor(partitionsIndex, idGenerator, nodeSelectionStrategy))
            .withStateResource(topicsIndex.getRawMap())
            .withStateResource(partitionsIndex.getRawMap())
            .withStateResource(idGenerator)
            .withListener(new StreamProcessorLifecycleAware()
            {
                @Override
                public void onOpen(TypedStreamProcessor streamProcessor)
                {
                    partitionsCommand.init(streamProcessor.getActor());
                    topologyManager.addTopologyPartitionListener(partitionsCommand);
                }

                @Override
                public void onClose()
                {
                    topologyManager.removeTopologyPartitionListener(partitionsCommand);
                    partitionsCommand.close();
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
        this.clientTransport = clientTransportInjector.getValue();
        this.topologyManager = topologyManagerInjector.getValue();
        this.clientApiTransport = clientApiTransportInjector.getValue();
        this.streamProcessorServiceFactory = streamProcessorServiceFactoryInjector.getValue();

        topologyManager.addTopologyMemberListener(nodeSelectionStrategy);
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        partitionResponderRef.set(null);

        topologyManager.removeTopologyMemberListener(nodeSelectionStrategy);
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
            return CompletableActorFuture.completedExceptionally(new IllegalStateException("No partition responder available."));
        }
    }


    public ServiceGroupReference<Partition> getPartitionsGroupReference()
    {
        return partitionsGroupReference;
    }

    public Injector<ServerTransport> getClientApiTransportInjector()
    {
        return clientApiTransportInjector;
    }

    public Injector<TopologyManager> getTopologyManagerInjector()
    {
        return topologyManagerInjector;
    }

    public Injector<StreamProcessorServiceFactory> getStreamProcessorServiceFactoryInjector()
    {
        return streamProcessorServiceFactoryInjector;
    }

    public Injector<ClientTransport> getClientTransportInjector()
    {
        return clientTransportInjector;
    }
}
