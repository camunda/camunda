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
package io.zeebe.broker.subscription.message;

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.logstreams.processor.StreamProcessorIds;
import io.zeebe.broker.logstreams.processor.StreamProcessorServiceFactory;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.subscription.command.SubscriptionCommandSender;
import io.zeebe.broker.subscription.message.processor.MessageStreamProcessor;
import io.zeebe.broker.system.configuration.ClusterCfg;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.ServerTransport;

public class MessageService implements Service<MessageService> {

  private final Injector<ServerTransport> clientApiTransportInjector = new Injector<>();
  private final Injector<StreamProcessorServiceFactory> streamProcessorServiceFactoryInjector =
      new Injector<>();

  private final Injector<ClientTransport> managementApiClientInjector = new Injector<>();
  private final Injector<ClientTransport> subscriptionApiClientInjector = new Injector<>();
  private final Injector<TopologyManager> topologyManagerInjector = new Injector<>();

  private final ServiceGroupReference<Partition> partitionsGroupReference =
      ServiceGroupReference.<Partition>create()
          .onAdd((partitionName, partition) -> startStreamProcessors(partitionName, partition))
          .build();

  private final ClusterCfg clusterCfg;

  public MessageService(final ClusterCfg clusterCfg) {
    this.clusterCfg = clusterCfg;
  }

  private void startStreamProcessors(
      final ServiceName<Partition> partitionServiceName, final Partition partition) {
    final ServerTransport transport = clientApiTransportInjector.getValue();
    final StreamProcessorServiceFactory factory = streamProcessorServiceFactoryInjector.getValue();
    final TopologyManager topologyManager = topologyManagerInjector.getValue();

    final SubscriptionCommandSender subscriptionCommandSender =
        new SubscriptionCommandSender(clusterCfg, getSubscriptionApiClientInjector().getValue());

    final TypedStreamEnvironment env =
        new TypedStreamEnvironment(partition.getLogStream(), transport.getOutput());

    final MessageStreamProcessor streamProcessor =
        new MessageStreamProcessor(subscriptionCommandSender, topologyManager);

    final StateStorage stateStorage =
        partition
            .getStateStorageFactory()
            .create(StreamProcessorIds.MESSAGE_PROCESSOR_ID, "message");
    final StateSnapshotController stateSnapshotController =
        streamProcessor.createStateSnapshotController(stateStorage);

    factory
        .createService(partition, partitionServiceName)
        .processor(streamProcessor.createStreamProcessors(env))
        .snapshotController(stateSnapshotController)
        .processorId(StreamProcessorIds.MESSAGE_PROCESSOR_ID)
        .processorName("message")
        .build();
  }

  @Override
  public MessageService get() {
    return this;
  }

  public Injector<ServerTransport> getClientApiTransportInjector() {
    return clientApiTransportInjector;
  }

  public ServiceGroupReference<Partition> getPartitionsGroupReference() {
    return partitionsGroupReference;
  }

  public Injector<StreamProcessorServiceFactory> getStreamProcessorServiceFactoryInjector() {
    return streamProcessorServiceFactoryInjector;
  }

  public Injector<ClientTransport> getManagementApiClientInjector() {
    return managementApiClientInjector;
  }

  public Injector<ClientTransport> getSubscriptionApiClientInjector() {
    return subscriptionApiClientInjector;
  }

  public Injector<TopologyManager> getTopologyManagerInjector() {
    return topologyManagerInjector;
  }
}
