/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.startup;

import io.atomix.raft.partition.RaftPartition;
import io.camunda.search.clients.SecondaryDbQueryService;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.PartitionRaftListener;
import io.camunda.zeebe.broker.clustering.ClusterServices;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.logstreams.state.StatePositionSupplier;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
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
import io.camunda.zeebe.broker.system.partitions.impl.steps.AdminApiRequestHandlerStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.BackupApiRequestHandlerStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.BackupServiceTransitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.BackupStoreTransitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.ExporterDirectorPartitionTransitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.InterPartitionCommandServiceStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.LogStoragePartitionTransitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.LogStreamPartitionTransitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.MetricsStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.MigrationTransitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.QueryServicePartitionTransitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.SnapshotDirectorPartitionTransitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.StreamProcessorTransitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.ZeebeDbPartitionTransitionStep;
import io.camunda.zeebe.broker.transport.commandapi.CommandApiService;
import io.camunda.zeebe.broker.transport.commandapi.CommandApiServiceTransitionStep;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.engine.processing.EngineProcessors;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.startup.StartupStep;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.util.FeatureFlags;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public final class ZeebePartitionFactory {

  private static final List<StartupStep<PartitionStartupContext>> STARTUP_STEPS = List.of();

  private static final List<PartitionTransitionStep> TRANSITION_STEPS =
      List.of(
          new MetricsStep(),
          new LogStoragePartitionTransitionStep(),
          new LogStreamPartitionTransitionStep(),
          new ZeebeDbPartitionTransitionStep(),
          new MigrationTransitionStep(),
          new QueryServicePartitionTransitionStep(),
          new BackupStoreTransitionStep(),
          new BackupServiceTransitionStep(),
          new InterPartitionCommandServiceStep(),
          new StreamProcessorTransitionStep(),
          new CommandApiServiceTransitionStep(),
          new SnapshotDirectorPartitionTransitionStep(),
          new ExporterDirectorPartitionTransitionStep(),
          new BackupApiRequestHandlerStep(),
          new AdminApiRequestHandlerStep());

  private final ActorSchedulingService actorSchedulingService;
  private final BrokerCfg brokerCfg;
  private final BrokerInfo localBroker;
  private final CommandApiService commandApiService;
  private final ClusterServices clusterServices;
  private final ExporterRepository exporterRepository;
  private final DiskSpaceUsageMonitor diskSpaceUsageMonitor;
  private final AtomixServerTransport gatewayBrokerTransport;
  private final JobStreamer jobStreamer;
  private final List<PartitionListener> partitionListeners;
  private final TopologyManagerImpl topologyManager;
  private final FeatureFlags featureFlags;
  private final List<PartitionRaftListener> partitionRaftListeners;
  private final SecurityConfiguration securityConfig;
  private final SecondaryDbQueryService secondaryDbQueryService;

  public ZeebePartitionFactory(
      final ActorSchedulingService actorSchedulingService,
      final BrokerCfg brokerCfg,
      final BrokerInfo localBroker,
      final CommandApiService commandApiService,
      final ClusterServices clusterServices,
      final ExporterRepository exporterRepository,
      final DiskSpaceUsageMonitor diskSpaceUsageMonitor,
      final AtomixServerTransport gatewayBrokerTransport,
      final JobStreamer jobStreamer,
      final List<PartitionListener> partitionListeners,
      final List<PartitionRaftListener> partitionRaftListeners,
      final TopologyManagerImpl topologyManager,
      final FeatureFlags featureFlags,
      final SecurityConfiguration securityConfig,
      final SecondaryDbQueryService secondaryDbQueryService) {
    this.actorSchedulingService = actorSchedulingService;
    this.brokerCfg = brokerCfg;
    this.localBroker = localBroker;
    this.commandApiService = commandApiService;
    this.clusterServices = clusterServices;
    this.exporterRepository = exporterRepository;
    this.diskSpaceUsageMonitor = diskSpaceUsageMonitor;
    this.gatewayBrokerTransport = gatewayBrokerTransport;
    this.jobStreamer = jobStreamer;
    this.partitionListeners = partitionListeners;
    this.partitionRaftListeners = partitionRaftListeners;
    this.topologyManager = topologyManager;
    this.featureFlags = featureFlags;
    this.securityConfig = securityConfig;
    this.secondaryDbQueryService = secondaryDbQueryService;
  }

  public ZeebePartition constructPartition(
      final RaftPartition raftPartition,
      final FileBasedSnapshotStore snapshotStore,
      final DynamicPartitionConfig initialPartitionConfig,
      final BrokerHealthCheckService brokerHealthCheckService,
      final MeterRegistry partitionMeterRegistry) {
    final var communicationService = clusterServices.getCommunicationService();
    final var membershipService = clusterServices.getMembershipService();
    final var typedRecordProcessorsFactory = createFactory(localBroker, featureFlags);

    final StateController stateController =
        createStateController(raftPartition, snapshotStore, snapshotStore, partitionMeterRegistry);

    final var context =
        new PartitionStartupAndTransitionContextImpl(
            localBroker.getNodeId(),
            localBroker.getPartitionsCount(),
            communicationService,
            raftPartition,
            partitionListeners,
            partitionRaftListeners,
            new AtomixPartitionMessagingService(
                communicationService, membershipService, raftPartition::members),
            actorSchedulingService,
            brokerCfg,
            commandApiService,
            snapshotStore,
            stateController,
            typedRecordProcessorsFactory,
            exporterRepository,
            new PartitionProcessingState(raftPartition),
            diskSpaceUsageMonitor,
            gatewayBrokerTransport,
            topologyManager,
            brokerHealthCheckService,
            securityConfig,
            partitionMeterRegistry);
    context.setDynamicPartitionConfig(initialPartitionConfig);

    final PartitionTransition newTransitionBehavior = new PartitionTransitionImpl(TRANSITION_STEPS);

    return new ZeebePartition(context, newTransitionBehavior, STARTUP_STEPS);
  }

  private StateController createStateController(
      final RaftPartition raftPartition,
      final ConstructableSnapshotStore snapshotStore,
      final ConcurrencyControl concurrencyControl,
      final MeterRegistry partitionMeterRegistry) {
    final Path runtimeDirectory;
    final var partitionId = raftPartition.id().id();
    if (brokerCfg.getData().useSeparateRuntimeDirectory()) {
      final Path rootRuntimeDirectory = Paths.get(brokerCfg.getData().getRuntimeDirectory());
      try {
        FileUtil.ensureDirectoryExists(rootRuntimeDirectory);
      } catch (final IOException e) {
        throw new UncheckedIOException(
            "Runtime directory %s does not exist".formatted(rootRuntimeDirectory), e);
      }
      runtimeDirectory = rootRuntimeDirectory.resolve(String.valueOf(partitionId));
    } else {
      runtimeDirectory = raftPartition.dataDirectory().toPath().resolve("runtime");
    }
    final var databaseCfg = brokerCfg.getExperimental().getRocksdb();
    final var consistencyChecks = brokerCfg.getExperimental().getConsistencyChecks();
    return new StateControllerImpl(
        new ZeebeRocksDbFactory<>(
            databaseCfg.createRocksDbConfiguration(),
            consistencyChecks.getSettings(),
            new AccessMetricsConfiguration(databaseCfg.getAccessMetrics(), partitionId),
            () -> MicrometerUtil.wrap(partitionMeterRegistry, PartitionKeyNames.tags(partitionId))),
        snapshotStore,
        runtimeDirectory,
        new AtomixRecordEntrySupplierImpl(raftPartition.getServer()),
        StatePositionSupplier::getHighestExportedPosition,
        concurrencyControl);
  }

  private TypedRecordProcessorsFactory createFactory(
      final BrokerInfo localBroker, final FeatureFlags featureFlags) {
    return recordProcessorContext -> {
      final InterPartitionCommandSender partitionCommandSender =
          recordProcessorContext.getPartitionCommandSender();
      final SubscriptionCommandSender subscriptionCommandSender =
          new SubscriptionCommandSender(
              recordProcessorContext.getPartitionId(), partitionCommandSender);

      return EngineProcessors.createEngineProcessors(
          recordProcessorContext,
          localBroker.getPartitionsCount(),
          subscriptionCommandSender,
          partitionCommandSender,
          featureFlags,
          jobStreamer,
          secondaryDbQueryService);
    };
  }
}
