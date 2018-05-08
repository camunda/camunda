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
package io.zeebe.broker.clustering.orchestration.state;

import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.streamProcessorService;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.agrona.DirectBuffer;
import org.slf4j.Logger;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.clustering.orchestration.topic.TopicRecord;
import io.zeebe.broker.logstreams.processor.StreamProcessorIds;
import io.zeebe.broker.logstreams.processor.StreamProcessorLifecycleAware;
import io.zeebe.broker.logstreams.processor.StreamProcessorServiceFactory;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.logstreams.impl.service.StreamProcessorService;
import io.zeebe.msgpack.property.ArrayProperty;
import io.zeebe.msgpack.value.ArrayValue;
import io.zeebe.msgpack.value.IntegerValue;
import io.zeebe.msgpack.value.ValueArray;
import io.zeebe.protocol.clientapi.Intent;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.ServerTransport;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;

public class KnownTopics implements Service<KnownTopics>, StreamProcessorLifecycleAware
{
    private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

    private final Injector<Partition> partitionInjector = new Injector<>();
    private final Injector<ServerTransport> serverTransportInjector = new Injector<>();
    private final Injector<StreamProcessorServiceFactory> streamProcessorServiceFactoryInjector = new Injector<>();

    private final TopicCreateProcessor topicCreateProcessor;
    private final TopicCreatedProcessor topicCreatedProcessor;

    private final List<KnownTopicsListener> topicsListeners = new ArrayList<>();

    private final ArrayValue<TopicInfo> knownTopics = new ArrayValue<>(new TopicInfo());
    private ActorControl actor;
    private ServiceContainer serviceContainer;
    private ServiceName<StreamProcessorService> streamProcessorServiceName;

    public KnownTopics(final ServiceContainer serviceContainer)
    {
        this.serviceContainer = serviceContainer;
        topicCreateProcessor = new TopicCreateProcessor(this::topicExists, this::notifyTopicAdded, this::addTopic);
        topicCreatedProcessor = new TopicCreatedProcessor(this::topicCreated, this::notifyTopicCreated, this::completeTopicCreation);
    }

    @Override
    public KnownTopics get()
    {
        return this;
    }

    public void registerTopicListener(final KnownTopicsListener topicsListener)
    {
        actor.run(() -> topicsListeners.add(topicsListener));
    }

    private boolean topicExists(final DirectBuffer topicName)
    {
        return getTopic(topicName) != null;
    }

    private boolean topicCreated(final DirectBuffer topicName)
    {
        final TopicInfo topicInfo = getTopic(topicName);
        return topicInfo != null && topicInfo.getPartitionIds().iterator().hasNext();
    }

    private TopicInfo getTopic(final DirectBuffer topicName)
    {
        for (final TopicInfo topicInfo : knownTopics)
        {
            if (topicInfo.getTopicNameBuffer().equals(topicName))
            {
                return topicInfo;
            }
        }

        return null;
    }


    private void addTopic(final long key, final TopicRecord topicEvent)
    {
        final TopicInfo topicInfo = createTopicInfo(key, topicEvent);

        final ValueArray<IntegerValue> partitionIds = topicInfo.getPartitionIds();
        topicEvent.getPartitionIds().forEach(id -> partitionIds.add().setValue(id.getValue()));

        LOG.info("Adding topic {}", topicInfo);

    }

    private void notifyTopicAdded(final DirectBuffer topicName)
    {
        final String name = BufferUtil.bufferAsString(topicName);
        topicsListeners.forEach(l -> l.topicAdded(name));
    }

    private void notifyTopicCreated(final DirectBuffer topicName)
    {
        final String name = BufferUtil.bufferAsString(topicName);
        topicsListeners.forEach(l -> l.topicCreated(name));
    }

    private TopicInfo createTopicInfo(final long key, final TopicRecord topicEvent)
    {
        final TopicInfo topicInfo = knownTopics.add();
        topicInfo.setTopicName(topicEvent.getName())
                 .setPartitionCount(topicEvent.getPartitions())
                 .setReplicationFactor(topicEvent.getReplicationFactor())
                 .setKey(key);

        return topicInfo;
    }

    private TopicInfo getOrCreateTopicInfo(final long key, final TopicRecord topicEvent)
    {
        final TopicInfo topicInfo = getTopic(topicEvent.getName());

        if (topicInfo != null)
        {
            return topicInfo;
        }
        else
        {
            return createTopicInfo(key, topicEvent);
        }
    }

    private void completeTopicCreation(final long key, final TopicRecord topicEvent)
    {
        final TopicInfo topicInfo = getOrCreateTopicInfo(key, topicEvent);

        final ArrayProperty<IntegerValue> partitionIds = topicInfo.partitionIds;
        partitionIds.reset();
        topicEvent.getPartitionIds().forEach(id -> partitionIds.add().setValue(id.getValue()));

        LOG.info("Updating topic {}", topicInfo);
    }


    @Override
    public void onOpen(final TypedStreamProcessor streamProcessor)
    {
        actor = streamProcessor.getActor();
    }

    @Override
    public void start(final ServiceStartContext startContext)
    {
        final Partition partition = partitionInjector.getValue();
        final ServerTransport serverTransport = serverTransportInjector.getValue();
        final StreamProcessorServiceFactory streamProcessorServiceFactory = streamProcessorServiceFactoryInjector.getValue();

        final TypedStreamProcessor streamProcessor = new TypedStreamEnvironment(partition.getLogStream(), serverTransport.getOutput())
            .newStreamProcessor()
            .onCommand(ValueType.TOPIC, Intent.CREATE, topicCreateProcessor)
            .onEvent(ValueType.TOPIC, Intent.CREATE_COMPLETE, topicCreatedProcessor)
            .withListener(this)
            .withStateResource(knownTopics)
            .build();

        final String streamProcessorName = "topics";
        streamProcessorServiceName = streamProcessorService(partitionInjector.getValue().getLogStream().getLogName(), streamProcessorName);
        final ActorFuture<StreamProcessorService> future = streamProcessorServiceFactory.createService(partition, partitionInjector.getInjectedServiceName())
                                                                                        .processor(streamProcessor)
                                                                                        .processorId(StreamProcessorIds.CLUSTER_TOPIC_STATE)
                                                                                        .processorName(streamProcessorName)
                                                                                        .build();

        startContext.async(future);
    }

    @Override
    public void stop(final ServiceStopContext stopContext)
    {
        if (serviceContainer.hasService(streamProcessorServiceName))
        {
            stopContext.async(serviceContainer.removeService(streamProcessorServiceName));
        }
    }

    public Injector<Partition> getPartitionInjector()
    {
        return partitionInjector;
    }

    public Injector<ServerTransport> getServerTransportInjector()
    {
        return serverTransportInjector;
    }

    public Injector<StreamProcessorServiceFactory> getStreamProcessorServiceFactoryInjector()
    {
        return streamProcessorServiceFactoryInjector;
    }

    public <R> ActorFuture<R> queryTopics(final Function<Iterable<TopicInfo>, R> query)
    {
        return actor.call(() -> query.apply(knownTopics));
    }

}
