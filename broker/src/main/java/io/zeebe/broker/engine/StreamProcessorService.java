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
import io.zeebe.broker.system.management.LeaderManagementRequestHandler;
import io.zeebe.broker.system.management.deployment.PushDeploymentRequestHandler;
import io.zeebe.broker.transport.commandapi.CommandApiService;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.engine.processor.ProcessingContext;
import io.zeebe.engine.processor.StreamProcessor;
import io.zeebe.engine.processor.TypedRecordProcessors;
import io.zeebe.engine.processor.workflow.EngineProcessors;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandSender;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.ActorControl;

public class StreamProcessorService implements Service<StreamProcessor> {

  private final Injector<CommandApiService> commandApiServiceInjector = new Injector<>();
  private final Injector<TopologyManager> topologyManagerInjector = new Injector<>();
  private final Injector<Atomix> atomixInjector = new Injector<>();
  private final Injector<LeaderManagementRequestHandler> leaderManagementRequestHandlerInjector =
      new Injector<>();
  private final Injector<Partition> partitionInjector = new Injector<>();
  private final Injector<Dispatcher> logStreamWriteBufferInjector = new Injector<>();

  private final ClusterCfg clusterCfg;
  private TopologyManager topologyManager;
  private Atomix atomix;
  private StreamProcessor streamProcessor;

  public StreamProcessorService(BrokerCfg brokerCfg) {
    clusterCfg = brokerCfg.getCluster();
  }

  @Override
  public void start(final ServiceStartContext serviceContext) {
    final CommandApiService commandApiService = commandApiServiceInjector.getValue();
    this.topologyManager = topologyManagerInjector.getValue();
    this.atomix = atomixInjector.getValue();
    final Partition partition = partitionInjector.getValue();

    final LogStream logStream = partition.getLogStream();
    streamProcessor =
        StreamProcessor.builder()
            .logStream(logStream)
            // for the reader
            .logStorage(logStream.getLogStorage())
            // for the writer
            .writeBuffer(logStreamWriteBufferInjector.getValue())
            .actorScheduler(serviceContext.getScheduler())
            .zeebeDb(partition.getZeebeDb())
            .commandResponseWriter(commandApiService.newCommandResponseWriter())
            .onProcessedListener(
                commandApiService.getOnProcessedListener(partition.getPartitionId()))
            .streamProcessorFactory(
                (processingContext) -> {
                  final ActorControl actor = processingContext.getActor();
                  final ZeebeState zeebeState = processingContext.getZeebeState();
                  return createTypedStreamProcessor(actor, zeebeState, processingContext);
                })
            .build();

    serviceContext.async(streamProcessor.openAsync(), true);
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    stopContext.async(streamProcessor.closeAsync());
  }

  @Override
  public StreamProcessor get() {
    return streamProcessor;
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

  public Injector<CommandApiService> getCommandApiServiceInjector() {
    return commandApiServiceInjector;
  }

  public Injector<Partition> getPartitionInjector() {
    return partitionInjector;
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

  public Injector<Dispatcher> getLogStreamWriteBufferInjector() {
    return logStreamWriteBufferInjector;
  }
}
