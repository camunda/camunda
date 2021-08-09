/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.cluster.messaging.ClusterEventService;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.partition.RaftPartitionGroup;
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.clustering.ClusterServices;
import io.camunda.zeebe.broker.engine.impl.DeploymentDistributorImpl;
import io.camunda.zeebe.broker.engine.impl.LongPollingJobNotification;
import io.camunda.zeebe.broker.engine.impl.PartitionCommandSenderImpl;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.partitioning.topology.TopologyPartitionListener;
import io.camunda.zeebe.broker.partitioning.topology.TopologyPartitionListenerImpl;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.management.deployment.PushDeploymentRequestHandler;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.broker.system.partitions.PartitionStartupAndTransitionContextImpl;
import io.camunda.zeebe.broker.system.partitions.PartitionStartupStep;
import io.camunda.zeebe.broker.system.partitions.PartitionStep;
import io.camunda.zeebe.broker.system.partitions.PartitionStepMigrationHelper;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.broker.system.partitions.TypedRecordProcessorsFactory;
import io.camunda.zeebe.broker.system.partitions.ZeebePartition;
import io.camunda.zeebe.broker.system.partitions.impl.AtomixPartitionMessagingService;
import io.camunda.zeebe.broker.system.partitions.impl.PartitionProcessingState;
import io.camunda.zeebe.broker.system.partitions.impl.PartitionTransitionImpl;
import io.camunda.zeebe.broker.system.partitions.impl.steps.ExporterDirectorPartitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.LogDeletionPartitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.LogStoragePartitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.LogStreamPartitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.RocksDbMetricExporterPartitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.SnapshotDirectorPartitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.SnapshotReplicationPartitionStartupStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.StateControllerPartitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.StoragePartitionTransitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.StreamProcessorPartitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.ZeebeDbPartitionStep;
import io.camunda.zeebe.broker.transport.commandapi.CommandApiService;
import io.camunda.zeebe.engine.processing.EngineProcessors;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.ProcessingContext;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreFactory;
import io.camunda.zeebe.util.sched.ActorControl;
import io.camunda.zeebe.util.sched.ActorSchedulingService;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

final class PartitionFactory {
  // preparation for future steps
  // will be executed in the order they are defined in this list
  private static final List<PartitionStartupStep> STARTUP_STEPS =
      List.of(new SnapshotReplicationPartitionStartupStep());

  // will probably be executed in parallel
  private static final List<PartitionTransitionStep> TRANSITION_STEPS =
      List.of(new StoragePartitionTransitionStep());
  // preparation for future step

  private static final List<PartitionStep> LEADER_STEPS =
      List.of(
          PartitionStepMigrationHelper.fromStartupStep(
              new SnapshotReplicationPartitionStartupStep()),
          new StateControllerPartitionStep(),
          PartitionStepMigrationHelper.fromTransitionStep(new StoragePartitionTransitionStep()),
          new LogDeletionPartitionStep(),
          new LogStoragePartitionStep(Role.LEADER),
          new LogStreamPartitionStep(),
          new ZeebeDbPartitionStep(),
          new StreamProcessorPartitionStep(Role.LEADER),
          new SnapshotDirectorPartitionStep(),
          new RocksDbMetricExporterPartitionStep(),
          new ExporterDirectorPartitionStep());
  private static final List<PartitionStep> FOLLOWER_STEPS =
      List.of(
          PartitionStepMigrationHelper.fromStartupStep(
              new SnapshotReplicationPartitionStartupStep()),
          new StateControllerPartitionStep(),
          PartitionStepMigrationHelper.fromTransitionStep(new StoragePartitionTransitionStep()),
          new LogDeletionPartitionStep());

  private final ActorSchedulingService actorSchedulingService;
  private final BrokerCfg brokerCfg;
  private final BrokerInfo localBroker;
  private final PushDeploymentRequestHandler deploymentRequestHandler;
  private final CommandApiService commandHApiService;
  private final FileBasedSnapshotStoreFactory snapshotStoreFactory;
  private final ClusterServices clusterServices;
  private final ExporterRepository exporterRepository;
  private final BrokerHealthCheckService healthCheckService;

  PartitionFactory(
      final ActorSchedulingService actorSchedulingService,
      final BrokerCfg brokerCfg,
      final BrokerInfo localBroker,
      final PushDeploymentRequestHandler deploymentRequestHandler,
      final CommandApiService commandHApiService,
      final FileBasedSnapshotStoreFactory snapshotStoreFactory,
      final ClusterServices clusterServices,
      final ExporterRepository exporterRepository,
      final BrokerHealthCheckService healthCheckService) {
    this.actorSchedulingService = actorSchedulingService;
    this.brokerCfg = brokerCfg;
    this.localBroker = localBroker;
    this.deploymentRequestHandler = deploymentRequestHandler;
    this.commandHApiService = commandHApiService;
    this.snapshotStoreFactory = snapshotStoreFactory;
    this.clusterServices = clusterServices;
    this.exporterRepository = exporterRepository;
    this.healthCheckService = healthCheckService;
  }

  List<ZeebePartition> constructPartitions(
      final RaftPartitionGroup partitionGroup,
      final List<PartitionListener> partitionListeners,
      final Consumer<TopologyPartitionListener> partitionListenerConsumer) {
    final var partitions = new ArrayList<ZeebePartition>();
    final var communicationService = clusterServices.getCommunicationService();
    final var eventService = clusterServices.getEventService();
    final var membershipService = clusterServices.getMembershipService();

    final MemberId nodeId = membershipService.getLocalMember().id();

    final List<RaftPartition> owningPartitions =
        partitionGroup.getPartitionsWithMember(nodeId).stream()
            .map(RaftPartition.class::cast)
            .collect(Collectors.toList());

    final var typedRecordProcessorsFactory =
        createFactory(
            partitionListenerConsumer,
            localBroker,
            communicationService,
            eventService,
            deploymentRequestHandler);

    for (final RaftPartition owningPartition : owningPartitions) {
      final var partitionId = owningPartition.id().id();

      final PartitionStartupAndTransitionContextImpl transitionContext =
          new PartitionStartupAndTransitionContextImpl(
              localBroker.getNodeId(),
              owningPartition,
              partitionListeners,
              new AtomixPartitionMessagingService(
                  communicationService, membershipService, owningPartition.members()),
              actorSchedulingService,
              brokerCfg,
              () -> commandHApiService.newCommandResponseWriter(),
              () -> commandHApiService.getOnProcessedListener(partitionId),
              snapshotStoreFactory.getConstructableSnapshotStore(partitionId),
              snapshotStoreFactory.getReceivableSnapshotStore(partitionId),
              typedRecordProcessorsFactory,
              exporterRepository,
              new PartitionProcessingState(owningPartition));

      final PartitionTransitionImpl transitionBehavior =
          new PartitionTransitionImpl(transitionContext, LEADER_STEPS, FOLLOWER_STEPS);

      final ZeebePartition zeebePartition =
          new ZeebePartition(transitionContext, transitionBehavior);

      healthCheckService.registerMonitoredPartition(
          zeebePartition.getPartitionId(), zeebePartition);
      partitions.add(zeebePartition);
    }

    return partitions;
  }

  private TypedRecordProcessorsFactory createFactory(
      final Consumer<TopologyPartitionListener> partitionListenerConsumer,
      final BrokerInfo localBroker,
      final ClusterCommunicationService communicationService,
      final ClusterEventService eventService,
      final PushDeploymentRequestHandler deploymentRequestHandler) {
    return (ActorControl actor,
        MutableZeebeState zeebeState,
        ProcessingContext processingContext) -> {
      final LogStream stream = processingContext.getLogStream();

      final TopologyPartitionListenerImpl partitionListener =
          new TopologyPartitionListenerImpl(actor);
      partitionListenerConsumer.accept(partitionListener);

      final DeploymentDistributorImpl deploymentDistributor =
          new DeploymentDistributorImpl(
              communicationService, eventService, partitionListener, actor);

      final PartitionCommandSenderImpl partitionCommandSender =
          new PartitionCommandSenderImpl(communicationService, partitionListenerConsumer, actor);
      final SubscriptionCommandSender subscriptionCommandSender =
          new SubscriptionCommandSender(stream.getPartitionId(), partitionCommandSender);

      final LongPollingJobNotification jobsAvailableNotification =
          new LongPollingJobNotification(eventService);

      return EngineProcessors.createEngineProcessors(
          processingContext,
          localBroker.getPartitionsCount(),
          subscriptionCommandSender,
          deploymentDistributor,
          deploymentRequestHandler,
          jobsAvailableNotification::onJobsAvailable);
    };
  }
}
