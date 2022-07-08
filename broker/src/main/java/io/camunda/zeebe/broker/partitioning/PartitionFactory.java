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
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.partition.RaftPartitionGroup;
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.clustering.ClusterServices;
import io.camunda.zeebe.broker.engine.impl.DeploymentDistributorImpl;
import io.camunda.zeebe.broker.engine.impl.LongPollingJobNotification;
import io.camunda.zeebe.broker.engine.impl.PartitionCommandSenderImpl;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.logstreams.state.StatePositionSupplier;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManager;
import io.camunda.zeebe.broker.partitioning.topology.TopologyPartitionListenerImpl;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.management.deployment.PushDeploymentRequestHandler;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageMonitor;
import io.camunda.zeebe.broker.system.partitions.PartitionStartupAndTransitionContextImpl;
import io.camunda.zeebe.broker.system.partitions.PartitionStartupContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransition;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.broker.system.partitions.StateController;
import io.camunda.zeebe.broker.system.partitions.TypedRecordProcessorsFactory;
import io.camunda.zeebe.broker.system.partitions.ZeebePartition;
import io.camunda.zeebe.broker.system.partitions.impl.AtomixPartitionMessagingService;
import io.camunda.zeebe.broker.system.partitions.impl.AtomixRecordEntrySupplierImpl;
import io.camunda.zeebe.broker.system.partitions.impl.PartitionProcessingState;
import io.camunda.zeebe.broker.system.partitions.impl.PartitionTransitionImpl;
import io.camunda.zeebe.broker.system.partitions.impl.StateControllerImpl;
import io.camunda.zeebe.broker.system.partitions.impl.steps.ExporterDirectorPartitionTransitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.LogDeletionPartitionStartupStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.LogStoragePartitionTransitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.LogStreamPartitionTransitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.QueryServicePartitionTransitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.RockDbMetricExporterPartitionStartupStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.SnapshotDirectorPartitionTransitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.StreamProcessorTransitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.ZeebeDbPartitionTransitionStep;
import io.camunda.zeebe.broker.transport.commandapi.CommandApiService;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.engine.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.engine.processing.EngineProcessors;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.startup.StartupStep;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreFactory;
import io.camunda.zeebe.util.FeatureFlags;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

final class PartitionFactory {

  private static final List<StartupStep<PartitionStartupContext>> STARTUP_STEPS =
      List.of(
          new LogDeletionPartitionStartupStep(), new RockDbMetricExporterPartitionStartupStep());

  private static final List<PartitionTransitionStep> TRANSITION_STEPS =
      List.of(
          new LogStoragePartitionTransitionStep(),
          new LogStreamPartitionTransitionStep(),
          new ZeebeDbPartitionTransitionStep(),
          new QueryServicePartitionTransitionStep(),
          new StreamProcessorTransitionStep(),
          new SnapshotDirectorPartitionTransitionStep(),
          new ExporterDirectorPartitionTransitionStep());

  private final ActorSchedulingService actorSchedulingService;
  private final BrokerCfg brokerCfg;
  private final BrokerInfo localBroker;
  private final PushDeploymentRequestHandler deploymentRequestHandler;
  private final CommandApiService commandApiService;
  private final FileBasedSnapshotStoreFactory snapshotStoreFactory;
  private final ClusterServices clusterServices;
  private final ExporterRepository exporterRepository;
  private final BrokerHealthCheckService healthCheckService;
  private final DiskSpaceUsageMonitor diskSpaceUsageMonitor;

  PartitionFactory(
      final ActorSchedulingService actorSchedulingService,
      final BrokerCfg brokerCfg,
      final BrokerInfo localBroker,
      final PushDeploymentRequestHandler deploymentRequestHandler,
      final CommandApiService commandApiService,
      final FileBasedSnapshotStoreFactory snapshotStoreFactory,
      final ClusterServices clusterServices,
      final ExporterRepository exporterRepository,
      final BrokerHealthCheckService healthCheckService,
      final DiskSpaceUsageMonitor diskSpaceUsageMonitor) {
    this.actorSchedulingService = actorSchedulingService;
    this.brokerCfg = brokerCfg;
    this.localBroker = localBroker;
    this.deploymentRequestHandler = deploymentRequestHandler;
    this.commandApiService = commandApiService;
    this.snapshotStoreFactory = snapshotStoreFactory;
    this.clusterServices = clusterServices;
    this.exporterRepository = exporterRepository;
    this.healthCheckService = healthCheckService;
    this.diskSpaceUsageMonitor = diskSpaceUsageMonitor;
  }

  List<ZeebePartition> constructPartitions(
      final RaftPartitionGroup partitionGroup,
      final List<PartitionListener> partitionListeners,
      final TopologyManager topologyManager,
      final FeatureFlags featureFlags) {
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
            topologyManager,
            localBroker,
            communicationService,
            eventService,
            deploymentRequestHandler,
            featureFlags);

    for (final RaftPartition owningPartition : owningPartitions) {
      final var partitionId = owningPartition.id().id();

      final ConstructableSnapshotStore constructableSnapshotStore =
          snapshotStoreFactory.getConstructableSnapshotStore(partitionId);
      final StateController stateController =
          createStateController(
              owningPartition,
              constructableSnapshotStore,
              snapshotStoreFactory.getSnapshotStoreConcurrencyControl(partitionId));

      final PartitionStartupAndTransitionContextImpl partitionStartupAndTransitionContext =
          new PartitionStartupAndTransitionContextImpl(
              localBroker.getNodeId(),
              owningPartition,
              partitionListeners,
              new AtomixPartitionMessagingService(
                  communicationService, membershipService, owningPartition.members()),
              actorSchedulingService,
              brokerCfg,
              commandApiService::newCommandResponseWriter,
              () -> commandApiService.getOnProcessedListener(partitionId),
              constructableSnapshotStore,
              stateController,
              typedRecordProcessorsFactory,
              exporterRepository,
              new PartitionProcessingState(owningPartition),
              diskSpaceUsageMonitor);

      final PartitionTransition newTransitionBehavior =
          new PartitionTransitionImpl(TRANSITION_STEPS);

      final ZeebePartition zeebePartition =
          new ZeebePartition(
              partitionStartupAndTransitionContext, newTransitionBehavior, STARTUP_STEPS);

      healthCheckService.registerMonitoredPartition(
          zeebePartition.getPartitionId(), zeebePartition);
      partitions.add(zeebePartition);
    }

    return partitions;
  }

  private StateController createStateController(
      final RaftPartition raftPartition,
      final ConstructableSnapshotStore snapshotStore,
      final ConcurrencyControl concurrencyControl) {
    final var runtimeDirectory = raftPartition.dataDirectory().toPath().resolve("runtime");
    final var databaseCfg = brokerCfg.getExperimental().getRocksdb();
    final var consistencyChecks = brokerCfg.getExperimental().getConsistencyChecks();

    return new StateControllerImpl(
        new ZeebeRocksDbFactory<>(
            databaseCfg.createRocksDbConfiguration(), consistencyChecks.getSettings()),
        snapshotStore,
        runtimeDirectory,
        new AtomixRecordEntrySupplierImpl(raftPartition.getServer()),
        StatePositionSupplier::getHighestExportedPosition,
        concurrencyControl);
  }

  private TypedRecordProcessorsFactory createFactory(
      final TopologyManager topologyManager,
      final BrokerInfo localBroker,
      final ClusterCommunicationService communicationService,
      final ClusterEventService eventService,
      final PushDeploymentRequestHandler deploymentRequestHandler,
      final FeatureFlags featureFlags) {
    return (recordProcessorContext) -> {
      final var actor = recordProcessorContext.getActor();

      final TopologyPartitionListenerImpl partitionListener =
          new TopologyPartitionListenerImpl(actor);
      topologyManager.addTopologyPartitionListener(partitionListener);

      final DeploymentDistributorImpl deploymentDistributor =
          new DeploymentDistributorImpl(
              communicationService, eventService, partitionListener, actor);

      final PartitionCommandSenderImpl partitionCommandSender =
          new PartitionCommandSenderImpl(communicationService, partitionListener);
      final SubscriptionCommandSender subscriptionCommandSender =
          new SubscriptionCommandSender(
              recordProcessorContext.getPartitionId(), partitionCommandSender);

      final LongPollingJobNotification jobsAvailableNotification =
          new LongPollingJobNotification(eventService);

      final var processor =
          EngineProcessors.createEngineProcessors(
              recordProcessorContext,
              localBroker.getPartitionsCount(),
              subscriptionCommandSender,
              deploymentDistributor,
              deploymentRequestHandler,
              jobsAvailableNotification::onJobsAvailable,
              featureFlags);

      return processor.withListener(
          new StreamProcessorLifecycleAware() {
            @Override
            public void onClose() {
              topologyManager.removeTopologyPartitionListener(partitionListener);
            }
          });
    };
  }
}
