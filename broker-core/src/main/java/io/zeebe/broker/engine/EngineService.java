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
package io.zeebe.broker.engine;

import io.atomix.core.Atomix;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.clustering.base.topology.TopologyPartitionListenerImpl;
import io.zeebe.broker.engine.impl.DeploymentDistributorImpl;
import io.zeebe.broker.engine.impl.SubscriptionCommandSenderImpl;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.ClusterCfg;
import io.zeebe.broker.system.configuration.DataCfg;
import io.zeebe.broker.transport.clientapi.CommandResponseWriterImpl;
import io.zeebe.engine.processor.AsyncSnapshotingDirectorService;
import io.zeebe.engine.processor.ProcessingContext;
import io.zeebe.engine.processor.StreamProcessor;
import io.zeebe.engine.processor.StreamProcessorServiceNames;
import io.zeebe.engine.processor.TypedRecordProcessors;
import io.zeebe.engine.processor.workflow.EngineProcessors;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.transport.ServerTransport;
import io.zeebe.util.DurationUtil;
import io.zeebe.util.sched.ActorControl;
import java.time.Duration;

public class EngineService implements Service<EngineService> {

  public static final String PROCESSOR_NAME = "zb-stream-processor";

  private final Injector<ServerTransport> clientApiTransportInjector = new Injector<>();
  private final Injector<TopologyManager> topologyManagerInjector = new Injector<>();
  private final Injector<Atomix> atomixInjector = new Injector<>();

  private final ClusterCfg clusterCfg;
  private final ServiceContainer serviceContainer;
  private final Duration snapshotPeriod;
  private final int maxSnapshots;
  private ServiceStartContext serviceContext;

  private ServerTransport clientApiTransport;
  private TopologyManager topologyManager;
  private Atomix atomix;
  private final ServiceGroupReference<Partition> partitionsGroupReference =
      ServiceGroupReference.<Partition>create().onAdd(this::startEngineForPartition).build();

  public EngineService(ServiceContainer serviceContainer, BrokerCfg brokerCfg) {
    clusterCfg = brokerCfg.getCluster();

    this.serviceContainer = serviceContainer;
    final DataCfg dataCfg = brokerCfg.getData();
    this.snapshotPeriod = DurationUtil.parse(dataCfg.getSnapshotPeriod());
    this.maxSnapshots = dataCfg.getMaxSnapshots();
  }

  @Override
  public void start(final ServiceStartContext serviceContext) {
    this.serviceContext = serviceContext;
    this.clientApiTransport = clientApiTransportInjector.getValue();
    this.topologyManager = topologyManagerInjector.getValue();
    this.atomix = atomixInjector.getValue();
  }

  public void startEngineForPartition(
      final ServiceName<Partition> partitionServiceName, final Partition partition) {
    final int partitionId = partition.getPartitionId();

    final LogStream logStream = partition.getLogStream();
    StreamProcessor.builder(partitionId, PROCESSOR_NAME)
        .logStream(logStream)
        .actorScheduler(serviceContext.getScheduler())
        .additionalDependencies(partitionServiceName)
        .zeebeDb(partition.getZeebeDb())
        .serviceContainer(serviceContainer)
        .commandResponseWriter(new CommandResponseWriterImpl(clientApiTransport.getOutput()))
        .streamProcessorFactory(
            (processingContext) -> {
              final ActorControl actor = processingContext.getActor();
              final ZeebeState zeebeState = processingContext.getZeebeState();
              return createTypedStreamProcessor(actor, zeebeState, processingContext);
            })
        .build();

    createAsyncSnapshotDirectorService(partition);
  }

  private void createAsyncSnapshotDirectorService(final Partition partition) {
    final String logName = partition.getLogStream().getLogName();

    final AsyncSnapshotingDirectorService snapshotDirectorService =
        new AsyncSnapshotingDirectorService(
            partition.getPartitionId(),
            partition.getLogStream(),
            partition.getSnapshotController(),
            snapshotPeriod,
            maxSnapshots);

    final ServiceName<AsyncSnapshotingDirectorService> snapshotDirectorServiceName =
        StreamProcessorServiceNames.asyncSnapshotingDirectorService(logName, PROCESSOR_NAME);
    final ServiceName<StreamProcessor> streamProcessorControllerServiceName =
        StreamProcessorServiceNames.streamProcessorService(logName, EngineService.PROCESSOR_NAME);

    serviceContext
        .createService(snapshotDirectorServiceName, snapshotDirectorService)
        .dependency(
            streamProcessorControllerServiceName,
            snapshotDirectorService.getStreamProcessorInjector())
        .install();
  }

  public TypedRecordProcessors createTypedStreamProcessor(
      ActorControl actor, ZeebeState zeebeState, ProcessingContext processingContext) {
    final LogStream stream = processingContext.getLogStream();

    final TopologyPartitionListenerImpl partitionListener =
        new TopologyPartitionListenerImpl(actor);
    topologyManager.addTopologyPartitionListener(partitionListener);

    final DeploymentDistributorImpl deploymentDistributor =
        new DeploymentDistributorImpl(
            clusterCfg, atomix, partitionListener, zeebeState.getDeploymentState(), actor);

    final SubscriptionCommandSenderImpl subscriptionCommandSender =
        new SubscriptionCommandSenderImpl(atomix);
    subscriptionCommandSender.init(topologyManager, actor, stream);

    return EngineProcessors.createEngineProcessors(
        processingContext,
        clusterCfg.getPartitionsCount(),
        subscriptionCommandSender,
        deploymentDistributor);
  }

  @Override
  public EngineService get() {
    return this;
  }

  public Injector<ServerTransport> getClientApiTransportInjector() {
    return clientApiTransportInjector;
  }

  public ServiceGroupReference<Partition> getPartitionsGroupReference() {
    return partitionsGroupReference;
  }

  public Injector<TopologyManager> getTopologyManagerInjector() {
    return topologyManagerInjector;
  }

  public Injector<Atomix> getAtomixInjector() {
    return atomixInjector;
  }
}
