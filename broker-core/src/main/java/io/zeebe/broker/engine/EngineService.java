/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.engine;

import io.atomix.core.Atomix;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.clustering.base.topology.TopologyPartitionListenerImpl;
import io.zeebe.broker.engine.impl.DeploymentDistributorImpl;
import io.zeebe.broker.engine.impl.LongPollingJobNotification;
import io.zeebe.broker.engine.impl.PartitionCommandSenderImpl;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.ClusterCfg;
import io.zeebe.broker.system.configuration.DataCfg;
import io.zeebe.broker.system.management.LeaderManagementRequestHandler;
import io.zeebe.broker.system.management.deployment.PushDeploymentRequestHandler;
import io.zeebe.broker.transport.commandapi.CommandResponseWriterImpl;
import io.zeebe.engine.processor.AsyncSnapshotingDirectorService;
import io.zeebe.engine.processor.ProcessingContext;
import io.zeebe.engine.processor.StreamProcessor;
import io.zeebe.engine.processor.StreamProcessorServiceNames;
import io.zeebe.engine.processor.TypedRecordProcessors;
import io.zeebe.engine.processor.workflow.EngineProcessors;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandSender;
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

  private final Injector<ServerTransport> commandApiTransportInjector = new Injector<>();
  private final Injector<TopologyManager> topologyManagerInjector = new Injector<>();
  private final Injector<Atomix> atomixInjector = new Injector<>();
  private final Injector<LeaderManagementRequestHandler> leaderManagementRequestHandlerInjector =
      new Injector<>();

  private final ClusterCfg clusterCfg;
  private final ServiceContainer serviceContainer;
  private final Duration snapshotPeriod;
  private ServiceStartContext serviceContext;

  private ServerTransport commandApiTransport;
  private TopologyManager topologyManager;
  private Atomix atomix;
  private final ServiceGroupReference<Partition> partitionsGroupReference =
      ServiceGroupReference.<Partition>create().onAdd(this::startEngineForPartition).build();

  public EngineService(ServiceContainer serviceContainer, BrokerCfg brokerCfg) {
    clusterCfg = brokerCfg.getCluster();

    this.serviceContainer = serviceContainer;
    final DataCfg dataCfg = brokerCfg.getData();
    this.snapshotPeriod = DurationUtil.parse(dataCfg.getSnapshotPeriod());
  }

  @Override
  public void start(final ServiceStartContext serviceContext) {
    this.serviceContext = serviceContext;
    this.commandApiTransport = commandApiTransportInjector.getValue();
    this.topologyManager = topologyManagerInjector.getValue();
    this.atomix = atomixInjector.getValue();
  }

  @Override
  public EngineService get() {
    return this;
  }

  public void startEngineForPartition(
      final ServiceName<Partition> partitionServiceName, final Partition partition) {

    final LogStream logStream = partition.getLogStream();
    StreamProcessor.builder()
        .logStream(logStream)
        .actorScheduler(serviceContext.getScheduler())
        .additionalDependencies(partitionServiceName)
        .additionalDependencies(serviceContext.getServiceName())
        .zeebeDb(partition.getZeebeDb())
        .serviceContainer(serviceContainer)
        .commandResponseWriter(new CommandResponseWriterImpl(commandApiTransport.getOutput()))
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
            partition.getLogStream(), partition.getSnapshotController(), snapshotPeriod);

    final ServiceName<AsyncSnapshotingDirectorService> snapshotDirectorServiceName =
        StreamProcessorServiceNames.asyncSnapshotingDirectorService(logName);
    final ServiceName<StreamProcessor> streamProcessorControllerServiceName =
        StreamProcessorServiceNames.streamProcessorService(logName);

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

    final PartitionCommandSenderImpl partitionCommandSender =
        new PartitionCommandSenderImpl(atomix, topologyManager, actor);
    final SubscriptionCommandSender subscriptionCommandSender =
        new SubscriptionCommandSender(stream.getPartitionId(), partitionCommandSender);

    final PushDeploymentRequestHandler deploymentRequestHandler =
        leaderManagementRequestHandlerInjector.getValue().getPushDeploymentRequestHandler();

    final LongPollingJobNotification jobsAvailableNotification =
        new LongPollingJobNotification(atomix.getEventService());

    return EngineProcessors.createEngineProcessors(
        processingContext,
        clusterCfg.getPartitionsCount(),
        subscriptionCommandSender,
        deploymentDistributor,
        deploymentRequestHandler,
        jobsAvailableNotification::onJobsAvailable);
  }

  public Injector<ServerTransport> getCommandApiTransportInjector() {
    return commandApiTransportInjector;
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

  public Injector<LeaderManagementRequestHandler> getLeaderManagementRequestInjector() {
    return leaderManagementRequestHandlerInjector;
  }
}
