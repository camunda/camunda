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
package io.zeebe.broker.event.processor;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.logstreams.processor.*;
import io.zeebe.broker.transport.clientapi.*;
import io.zeebe.logstreams.impl.service.StreamProcessorService;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.servicecontainer.*;
import io.zeebe.transport.*;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import org.agrona.collections.Int2ObjectHashMap;
import org.slf4j.Logger;

public class TopicSubscriptionService extends Actor
    implements Service<TopicSubscriptionService>, TransportListener {
  private static final Logger LOG = Loggers.SERVICES_LOGGER;

  protected static final MetadataFilter TOPIC_SUBSCRIPTION_EVENT_FILTER =
      m -> {
        final ValueType valueType = m.getValueType();
        return
        // don't push subscription or subscriber events;
        // this may lead to infinite loops of pushing events that in turn trigger creation of more
        // such events (e.g. ACKs)
        valueType != ValueType.SUBSCRIPTION
            && valueType != ValueType.SUBSCRIBER
            &&
            // don't push internal events
            valueType != ValueType.ID
            && valueType != ValueType.NOOP;
      };

  protected final Injector<ServerTransport> clientApiTransportInjector = new Injector<>();
  protected final Injector<StreamProcessorServiceFactory> streamProcessorServiceFactoryInjector =
      new Injector<>();

  protected final ServiceContainer serviceContainer;

  protected final Int2ObjectHashMap<TopicSubscriptionManagementProcessor> managersByPartition =
      new Int2ObjectHashMap<>();

  protected ServerOutput serverOutput;
  protected StreamProcessorServiceFactory streamProcessorServiceFactory;

  protected final ServiceGroupReference<Partition> partitionsGroupReference =
      ServiceGroupReference.<Partition>create()
          .onAdd(this::onPartitionAdded)
          .onRemove(this::onPartitionRemoved)
          .build();

  protected final ServiceGroupReference<Partition> systemPartitionGroupReference =
      ServiceGroupReference.<Partition>create()
          .onAdd(this::onPartitionAdded)
          .onRemove(this::onPartitionRemoved)
          .build();

  public TopicSubscriptionService(ServiceContainer serviceContainer) {
    this.serviceContainer = serviceContainer;
  }

  @Override
  public TopicSubscriptionService get() {
    return this;
  }

  public Injector<ServerTransport> getClientApiTransportInjector() {
    return clientApiTransportInjector;
  }

  public Injector<StreamProcessorServiceFactory> getStreamProcessorServiceFactoryInjector() {
    return streamProcessorServiceFactoryInjector;
  }

  public ServiceGroupReference<Partition> getPartitionsGroupReference() {
    return partitionsGroupReference;
  }

  public ServiceGroupReference<Partition> getSystemPartitionGroupReference() {
    return systemPartitionGroupReference;
  }

  @Override
  public void start(ServiceStartContext startContext) {
    this.streamProcessorServiceFactory = streamProcessorServiceFactoryInjector.getValue();

    final ServerTransport transport = clientApiTransportInjector.getValue();
    this.serverOutput = transport.getOutput();

    final ActorFuture<Void> registration = transport.registerChannelListener(this);
    startContext.async(registration);

    startContext.getScheduler().submitActor(this);
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    actor.close();
  }

  public void onPartitionAdded(ServiceName<Partition> partitionServiceName, Partition partition) {
    actor.call(
        () -> {
          final TopicSubscriptionManagementProcessor ackProcessor =
              new TopicSubscriptionManagementProcessor(
                  partition,
                  partitionServiceName,
                  TOPIC_SUBSCRIPTION_EVENT_FILTER,
                  new CommandResponseWriter(serverOutput),
                  new ErrorResponseWriter(serverOutput),
                  () -> new SubscribedRecordWriter(serverOutput),
                  streamProcessorServiceFactory,
                  serviceContainer);

          final ActorFuture<StreamProcessorService> openFuture =
              streamProcessorServiceFactory
                  .createService(partition, partitionServiceName)
                  .processor(ackProcessor)
                  .processorId(StreamProcessorIds.TOPIC_SUBSCRIPTION_MANAGEMENT_PROCESSOR_ID)
                  .processorName("topic-management")
                  .eventFilter(TopicSubscriptionManagementProcessor.filter())
                  .build();

          actor.runOnCompletion(
              openFuture,
              (aVoid, throwable) -> {
                if (throwable == null) {
                  managersByPartition.put(partition.getInfo().getPartitionId(), ackProcessor);
                } else {
                  LOG.error(
                      "Failed to create topic subscription stream processor service for log stream service '{}'",
                      partitionServiceName);
                }
              });
        });
  }

  public void onPartitionRemoved(ServiceName<Partition> partitionServiceName, Partition partition) {
    actor.call(() -> managersByPartition.remove(partition.getInfo().getPartitionId()));
  }

  public void onClientChannelCloseAsync(int channelId) {
    actor.call(
        () -> {
          managersByPartition.forEach(
              (partitionId, manager) -> manager.onClientChannelCloseAsync(channelId));
        });
  }

  @Override
  public String getName() {
    return "subscription-service";
  }

  public ActorFuture<Void> closeSubscriptionAsync(final int partitionId, final long subscriberKey) {
    final TopicSubscriptionManagementProcessor managementProcessor = getManager(partitionId);

    if (managementProcessor != null) {
      return managementProcessor.closePushProcessorAsync(subscriberKey);
    } else {
      return CompletableActorFuture.completedExceptionally(
          new RuntimeException(
              String.format(
                  "No subscription management processor registered for partition '%d'",
                  partitionId)));
    }
  }

  private TopicSubscriptionManagementProcessor getManager(final int partitionId) {
    return managersByPartition.get(partitionId);
  }

  @Override
  public void onConnectionEstablished(RemoteAddress remoteAddress) {}

  @Override
  public void onConnectionClosed(RemoteAddress remoteAddress) {
    onClientChannelCloseAsync(remoteAddress.getStreamId());
  }
}
