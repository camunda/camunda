/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.cluster.messaging.ClusterEventService;
import io.atomix.primitive.partition.ManagedPartitionGroup;
import io.atomix.primitive.partition.ManagedPartitionService;
import io.atomix.primitive.partition.impl.DefaultPartitionService;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.partition.RaftPartitionGroup;
import io.atomix.raft.partition.RaftPartitionGroup.Builder;
import io.atomix.utils.concurrent.Futures;
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.clustering.ClusterServices;
import io.camunda.zeebe.broker.engine.impl.DeploymentDistributorImpl;
import io.camunda.zeebe.broker.engine.impl.LongPollingJobNotification;
import io.camunda.zeebe.broker.engine.impl.PartitionCommandSenderImpl;
import io.camunda.zeebe.broker.exporter.jar.ExporterJarLoadException;
import io.camunda.zeebe.broker.exporter.repo.ExporterLoadException;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManager;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.broker.partitioning.topology.TopologyPartitionListener;
import io.camunda.zeebe.broker.partitioning.topology.TopologyPartitionListenerImpl;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ClusterCfg;
import io.camunda.zeebe.broker.system.configuration.DataCfg;
import io.camunda.zeebe.broker.system.configuration.NetworkCfg;
import io.camunda.zeebe.broker.system.management.deployment.PushDeploymentRequestHandler;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageListener;
import io.camunda.zeebe.broker.system.partitions.PartitionBoostrapAndTransitionContextImpl;
import io.camunda.zeebe.broker.system.partitions.PartitionBootstrapStep;
import io.camunda.zeebe.broker.system.partitions.PartitionHealthBroadcaster;
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
import io.camunda.zeebe.broker.system.partitions.impl.steps.LogStreamPartitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.RocksDbMetricExporterPartitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.SnapshotDirectorPartitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.SnapshotReplicationPartitionBootstrapStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.StateControllerPartitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.StoragePartitionTransitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.StreamProcessorPartitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.steps.ZeebeDbPartitionStep;
import io.camunda.zeebe.broker.transport.commandapi.CommandApiService;
import io.camunda.zeebe.engine.processing.EngineProcessors;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.ProcessingContext;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.CommandResponseWriter;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.logstreams.impl.log.ZeebeEntryValidator;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStoreFactory;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreFactory;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.health.HealthStatus;
import io.camunda.zeebe.util.sched.ActorControl;
import io.camunda.zeebe.util.sched.ActorSchedulingService;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PartitionManagerImpl
    implements PartitionManager, TopologyManager, PartitionListener {

  public static final String GROUP_NAME = "raft-partition";

  private static final Logger LOGGER =
      LoggerFactory.getLogger("io.camunda.zeebe.broker.partitioning");

  // preparation for future steps
  // will be executed in the order they are defined in this list
  private static final List<PartitionBootstrapStep> BOOTSTRAP_STEPS =
      List.of(new SnapshotReplicationPartitionBootstrapStep());

  // will probably be executed in parallel
  private static final List<PartitionTransitionStep> TRANSITION_STEPS =
      List.of(new StoragePartitionTransitionStep());
  // preparation for future step

  private static final List<PartitionStep> LEADER_STEPS =
      List.of(
          PartitionStepMigrationHelper.fromBootstrapStep(
              new SnapshotReplicationPartitionBootstrapStep()),
          new StateControllerPartitionStep(),
          PartitionStepMigrationHelper.fromTransitionStep(new StoragePartitionTransitionStep()),
          new LogDeletionPartitionStep(),
          new LogStreamPartitionStep(),
          new ZeebeDbPartitionStep(),
          new StreamProcessorPartitionStep(),
          new SnapshotDirectorPartitionStep(),
          new RocksDbMetricExporterPartitionStep(),
          new ExporterDirectorPartitionStep());
  private static final List<PartitionStep> FOLLOWER_STEPS =
      List.of(
          PartitionStepMigrationHelper.fromBootstrapStep(
              new SnapshotReplicationPartitionBootstrapStep()),
          new StateControllerPartitionStep(),
          PartitionStepMigrationHelper.fromTransitionStep(new StoragePartitionTransitionStep()),
          new LogDeletionPartitionStep());

  protected volatile CompletableFuture<Void> closeFuture;
  private final BrokerHealthCheckService healthCheckService;
  private final ActorSchedulingService actorSchedulingService;
  private ManagedPartitionService partitionService;
  private ManagedPartitionGroup partitionGroup;
  private TopologyManagerImpl topologyManager;

  private final List<ZeebePartition> partitions = new ArrayList<>();
  private final Consumer<DiskSpaceUsageListener> diskSpaceUsageListenerRegistry;

  public PartitionManagerImpl(
      final ActorSchedulingService actorSchedulingService,
      final BrokerCfg brokerCfg,
      final BrokerInfo localBroker,
      final ClusterServices clusterServices,
      final BrokerHealthCheckService healthCheckService,
      final PushDeploymentRequestHandler deploymentRequestHandler,
      final Consumer<DiskSpaceUsageListener> diskSpaceUsageListenerRegistry,
      final List<PartitionListener> partitionListeners,
      final CommandApiService commandHApiService) {

    final var snapshotStoreFactory =
        new FileBasedSnapshotStoreFactory(actorSchedulingService, localBroker.getNodeId());

    final var partitionGroup = buildRaftPartitionGroup(brokerCfg, snapshotStoreFactory);

    this.actorSchedulingService = actorSchedulingService;
    this.healthCheckService = healthCheckService;
    this.diskSpaceUsageListenerRegistry = diskSpaceUsageListenerRegistry;

    this.partitionGroup = Objects.requireNonNull(partitionGroup);

    final var membershipService = clusterServices.getMembershipService();
    final var communicationService = clusterServices.getCommunicationService();
    final var eventService = clusterServices.getEventService();

    partitionService = buildPartitionService(membershipService, communicationService);

    topologyManager = new TopologyManagerImpl(membershipService, localBroker);

    final MemberId nodeId = membershipService.getLocalMember().id();

    final List<RaftPartition> owningPartitions =
        partitionGroup.getPartitionsWithMember(nodeId).stream()
            .map(RaftPartition.class::cast)
            .collect(Collectors.toList());

    final TypedRecordProcessorsFactory typedRecordProcessorsFactory =
        buildTypedRecordProcessorsFactory(
            localBroker, deploymentRequestHandler, communicationService, eventService);

    final var exporterRepository = buildExporterRepository(brokerCfg);

    for (final RaftPartition owningPartition : owningPartitions) {
      final var partitionId = owningPartition.id().id();

      final PartitionBoostrapAndTransitionContextImpl transitionContext =
          buildTransitionContext(
              actorSchedulingService,
              brokerCfg,
              localBroker,
              partitionListeners,
              () -> commandHApiService.newCommandResponseWriter(),
              () -> commandHApiService.getOnProcessedListener(partitionId),
              membershipService,
              communicationService,
              snapshotStoreFactory,
              typedRecordProcessorsFactory,
              exporterRepository,
              owningPartition,
              partitionId);
      final PartitionTransitionImpl transitionBehavior =
          new PartitionTransitionImpl(transitionContext, LEADER_STEPS, FOLLOWER_STEPS);
      final ZeebePartition zeebePartition =
          new ZeebePartition(transitionContext, transitionBehavior);
      partitions.add(zeebePartition);
    }
  }

  private static RaftPartitionGroup buildRaftPartitionGroup(
      final BrokerCfg configuration, final ReceivableSnapshotStoreFactory snapshotStoreFactory) {

    final DataCfg dataConfiguration = configuration.getData();
    final String rootDirectory = dataConfiguration.getDirectory();
    final var rootPath = Paths.get(rootDirectory);
    try {
      FileUtil.ensureDirectoryExists(rootPath);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to create data directory", e);
    }

    final var raftDataDirectory = rootPath.resolve(GROUP_NAME);

    try {
      FileUtil.ensureDirectoryExists(raftDataDirectory);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to create Raft data directory", e);
    }

    final ClusterCfg clusterCfg = configuration.getCluster();
    final var experimentalCfg = configuration.getExperimental();
    final DataCfg dataCfg = configuration.getData();
    final NetworkCfg networkCfg = configuration.getNetwork();

    final Builder partitionGroupBuilder =
        RaftPartitionGroup.builder(GROUP_NAME)
            .withNumPartitions(clusterCfg.getPartitionsCount())
            .withPartitionSize(clusterCfg.getReplicationFactor())
            .withMembers(getRaftGroupMembers(clusterCfg))
            .withDataDirectory(raftDataDirectory.toFile())
            .withSnapshotStoreFactory(snapshotStoreFactory)
            .withMaxAppendBatchSize((int) experimentalCfg.getMaxAppendBatchSizeInBytes())
            .withMaxAppendsPerFollower(experimentalCfg.getMaxAppendsPerFollower())
            .withEntryValidator(new ZeebeEntryValidator())
            .withFlushExplicitly(!experimentalCfg.isDisableExplicitRaftFlush())
            .withFreeDiskSpace(dataCfg.getFreeDiskSpaceReplicationWatermark())
            .withJournalIndexDensity(dataCfg.getLogIndexDensity())
            .withPriorityElection(experimentalCfg.isEnablePriorityElection());

    final int maxMessageSize = (int) networkCfg.getMaxMessageSizeInBytes();

    final var segmentSize = dataCfg.getLogSegmentSizeInBytes();
    if (segmentSize < maxMessageSize) {
      throw new IllegalArgumentException(
          String.format(
              "Expected the raft segment size greater than the max message size of %s, but was %s.",
              maxMessageSize, segmentSize));
    }

    partitionGroupBuilder.withSegmentSize(segmentSize);

    return partitionGroupBuilder.build();
  }

  private static List<String> getRaftGroupMembers(final ClusterCfg clusterCfg) {
    final int clusterSize = clusterCfg.getClusterSize();
    // node ids are always 0 to clusterSize - 1
    final List<String> members = new ArrayList<>();
    for (int i = 0; i < clusterSize; i++) {
      members.add(Integer.toString(i));
    }
    return members;
  }

  private PartitionBoostrapAndTransitionContextImpl buildTransitionContext(
      final ActorSchedulingService actorSchedulingService,
      final BrokerCfg brokerCfg,
      final BrokerInfo localBroker,
      final List<PartitionListener> partitionListeners,
      final Supplier<CommandResponseWriter> commandResponseWriterSupplier,
      final Supplier<Consumer<TypedRecord<?>>> onProcessedListenerSupplier,
      final ClusterMembershipService membershipService,
      final ClusterCommunicationService communicationService,
      final FileBasedSnapshotStoreFactory snapshotStoreFactory,
      final TypedRecordProcessorsFactory typedRecordProcessorsFactory,
      final ExporterRepository exporterRepository,
      final RaftPartition owningPartition,
      final Integer partitionId) {
    final PartitionBoostrapAndTransitionContextImpl transitionContext =
        new PartitionBoostrapAndTransitionContextImpl(
            localBroker.getNodeId(),
            owningPartition,
            partitionListeners,
            buildPartitionMessagingService(
                membershipService, communicationService, owningPartition),
            actorSchedulingService,
            brokerCfg,
            commandResponseWriterSupplier,
            onProcessedListenerSupplier,
            snapshotStoreFactory.getConstructableSnapshotStore(partitionId),
            snapshotStoreFactory.getReceivableSnapshotStore(partitionId),
            typedRecordProcessorsFactory,
            exporterRepository,
            new PartitionProcessingState(owningPartition));
    return transitionContext;
  }

  private TypedRecordProcessorsFactory buildTypedRecordProcessorsFactory(
      final BrokerInfo localBroker,
      final PushDeploymentRequestHandler deploymentRequestHandler,
      final ClusterCommunicationService communicationService,
      final ClusterEventService eventService) {
    final var typedRecordProcessorsFactory =
        createFactory(
            this::addTopologyPartitionListener,
            localBroker,
            communicationService,
            eventService,
            deploymentRequestHandler);
    return typedRecordProcessorsFactory;
  }

  private AtomixPartitionMessagingService buildPartitionMessagingService(
      final ClusterMembershipService membershipService,
      final ClusterCommunicationService communicationService,
      final RaftPartition owningPartition) {
    final var messagingService =
        new AtomixPartitionMessagingService(
            communicationService, membershipService, owningPartition.members());
    return messagingService;
  }

  @Override
  public ManagedPartitionGroup getPartitionGroup() {
    return partitionGroup;
  }

  public CompletableFuture<Void> start() {
    if (closeFuture != null) {
      return Futures.exceptionalFuture(
          new IllegalStateException(
              "PartitionManager " + (closeFuture.isDone() ? "shutdown" : "shutting down")));
    }

    actorSchedulingService.submitActor(topologyManager);

    return partitionService
        .start()
        .thenApply(
            ps -> {
              final var futures =
                  partitions.stream()
                      .map(partition -> CompletableFuture.runAsync(() -> startPartition(partition)))
                      .collect(Collectors.toList());

              CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]))
                  .join();
              return null;
            });
  }

  private void startPartition(final ZeebePartition zeebePartition) {
    actorSchedulingService.submitActor(zeebePartition).join();
    zeebePartition.addFailureListener(
        new PartitionHealthBroadcaster(zeebePartition.getPartitionId(), this::onHealthChanged));
    healthCheckService.registerMonitoredPartition(zeebePartition.getPartitionId(), zeebePartition);
    diskSpaceUsageListenerRegistry.accept(zeebePartition);
  }

  public CompletableFuture<Void> stop() {
    if (closeFuture == null) {
      closeFuture =
          CompletableFuture.runAsync(this::stopPartitions)
              .whenComplete(
                  (nil, error) -> {
                    logErrorIfApplicable(error);
                    partitionService.stop().join();
                  })
              .whenComplete(
                  (nil, error) -> {
                    logErrorIfApplicable(error);
                    partitionGroup = null;
                    partitionService = null;
                    topologyManager.close();
                    topologyManager = null;
                  });
    }

    return closeFuture;
  }

  private void logErrorIfApplicable(final Throwable error) {
    if (error != null) {
      LOGGER.error(error.getMessage(), error);
    }
  }

  private void stopPartitions() {
    final var futures =
        partitions.stream()
            .map(partition -> CompletableFuture.runAsync(() -> stopPartition(partition)))
            .collect(Collectors.toList());

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).join();
  }

  private void stopPartition(final ZeebePartition partition) {
    healthCheckService.removeMonitoredPartition(partition.getPartitionId());
    partition.close();
  }

  /** Builds a partition service. */
  private ManagedPartitionService buildPartitionService(
      final ClusterMembershipService clusterMembershipService,
      final ClusterCommunicationService messagingService) {

    return new DefaultPartitionService(clusterMembershipService, messagingService, partitionGroup);
  }

  @Override
  public String toString() {
    return "PartitionManagerImpl{" + "partitionGroup=" + partitionGroup + '}';
  }

  @Override
  public ActorFuture<Void> onBecomingFollower(final int partitionId, final long term) {
    return topologyManager.onBecomingFollower(partitionId, term);
  }

  @Override
  public ActorFuture<Void> onBecomingLeader(
      final int partitionId, final long term, final LogStream logStream) {
    return topologyManager.onBecomingLeader(partitionId, term, logStream);
  }

  @Override
  public ActorFuture<Void> onBecomingInactive(final int partitionId, final long term) {
    return topologyManager.onBecomingInactive(partitionId, term);
  }

  public void onHealthChanged(final int i, final HealthStatus healthStatus) {
    topologyManager.onHealthChanged(i, healthStatus);
  }

  @Override
  public void removeTopologyPartitionListener(final TopologyPartitionListener listener) {
    topologyManager.removeTopologyPartitionListener(listener);
  }

  @Override
  public void addTopologyPartitionListener(final TopologyPartitionListener listener) {
    topologyManager.addTopologyPartitionListener(listener);
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

  private ExporterRepository buildExporterRepository(final BrokerCfg cfg) {
    final ExporterRepository exporterRepository = new ExporterRepository();
    final var exporterEntries = cfg.getExporters().entrySet();

    // load and validate exporters
    for (final var exporterEntry : exporterEntries) {
      final var id = exporterEntry.getKey();
      final var exporterCfg = exporterEntry.getValue();
      try {
        exporterRepository.load(id, exporterCfg);
      } catch (final ExporterLoadException | ExporterJarLoadException e) {
        throw new IllegalStateException(
            "Failed to load exporter with configuration: " + exporterCfg, e);
      }
    }

    return exporterRepository;
  }
}
