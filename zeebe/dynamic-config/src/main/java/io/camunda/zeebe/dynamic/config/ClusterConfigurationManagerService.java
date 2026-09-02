/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationInitializer.FileInitializer;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationInitializer.GossipInitializer;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationInitializer.InitializerError.PersistedConfigurationIsBroken;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationInitializer.StaticInitializer;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationInitializer.SyncInitializer;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationManager.InconsistentConfigurationListener;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationCoordinatorSupplier;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestsHandler;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestServer;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestValidator;
import io.camunda.zeebe.dynamic.config.changes.ClusterChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinatorImpl;
import io.camunda.zeebe.dynamic.config.changes.GlobalConfigurationChangeAppliersImpl;
import io.camunda.zeebe.dynamic.config.changes.ModeChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.NoopClusterMembershipChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeAppliersImpl;
import io.camunda.zeebe.dynamic.config.changes.PartitionScalingChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.RestoreChangeExecutor;
import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossiper;
import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossiperConfig;
import io.camunda.zeebe.dynamic.config.metrics.TopologyManagerMetrics;
import io.camunda.zeebe.dynamic.config.metrics.TopologyMetrics;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.util.RequestValidatorRegistry;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.AsyncClosable;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.FileUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public final class ClusterConfigurationManagerService
    implements ClusterConfigurationUpdateNotifier, AsyncClosable {
  public static final String TOPOLOGY_FILE_NAME = ".topology.meta";

  /**
   * Static feature flag indicating that the manager operates on the multi-partition-group model.
   * Kept as a named constant for callers outside this class that still branch on it explicitly
   * (e.g. {@code BrokerTopologyManagerImpl}, {@code GatewayClusterConfigurationService}); within
   * this class and {@link ClusterConfigurationManagerImpl}, the legacy single-group code path has
   * been removed and the new model always runs.
   */
  public static final boolean USE_NEW_CONFIG = true;

  private final ClusterConfigurationManagerImpl clusterConfigurationManager;
  private final ClusterConfigurationGossiper clusterConfigurationGossiper;
  private final PersistedCurrentClusterConfiguration persistedCurrentClusterConfiguration;
  private final Path configurationFile;
  private final ConfigurationChangeCoordinator configurationChangeCoordinator;
  private final ClusterConfigurationRequestServer configurationRequestServer;
  private final Actor gossipActor;
  private final Actor managerActor;
  private final ClusterConfigurationGossiperConfig gossiperConfig;
  private final ClusterChangeExecutor clusterChangeExecutor;
  private final TopologyMetrics topologyMetrics;
  private final TopologyManagerMetrics topologyManagerMetrics;
  private final ClusterMembershipService membershipService;
  private final MemberId localMemberId;
  private final RequestValidatorRegistry validators = new RequestValidatorRegistry();
  private final Map<String, ModeChangeExecutor> modeChangeExecutorPerTenant = new HashMap<>();

  public ClusterConfigurationManagerService(
      final Path dataRootDirectory,
      final ClusterCommunicationService communicationService,
      final ClusterMembershipService memberShipService,
      final ClusterConfigurationGossiperConfig config,
      final ClusterChangeExecutor clusterChangeExecutor,
      final MeterRegistry meterRegistry) {
    gossiperConfig = config;
    this.clusterChangeExecutor = clusterChangeExecutor;
    topologyMetrics = new TopologyMetrics(meterRegistry);
    topologyManagerMetrics = new TopologyManagerMetrics(meterRegistry);
    try {
      FileUtil.ensureDirectoryExists(dataRootDirectory);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to create data directory", e);
    }

    membershipService = memberShipService;
    localMemberId = memberShipService.getLocalMember().id();
    configurationFile = dataRootDirectory.resolve(TOPOLOGY_FILE_NAME);
    gossipActor = Actor.newActor().name("ClusterConfigGossip").build();
    managerActor = Actor.newActor().name("ClusterConfigManager").build();

    persistedCurrentClusterConfiguration =
        PersistedCurrentClusterConfiguration.ofFile(configurationFile, new ProtoBufSerializer());
    clusterConfigurationManager =
        new ClusterConfigurationManagerImpl(
            managerActor,
            localMemberId,
            persistedCurrentClusterConfiguration,
            topologyManagerMetrics);

    clusterConfigurationGossiper =
        new ClusterConfigurationGossiper(
            gossipActor,
            communicationService,
            memberShipService,
            new ProtoBufSerializer(),
            config,
            ignored -> {},
            clusterConfigurationManager::onGossipReceivedCurrent,
            topologyMetrics);
    configurationChangeCoordinator =
        new ConfigurationChangeCoordinatorImpl(
            clusterConfigurationManager, localMemberId, managerActor);
    configurationRequestServer =
        new ClusterConfigurationRequestServer(
            communicationService,
            new ProtoBufSerializer(),
            new ClusterConfigurationManagementRequestsHandler(
                configurationChangeCoordinator, localMemberId, managerActor, validators));

    clusterConfigurationManager.setCurrentConfigurationGossiper(
        clusterConfigurationGossiper::updateCurrentClusterConfiguration);
  }

  /**
   * Recovers a broken file via sync, then waits on gossip from the coordinator; the
   * coordinator-only initializers below are still defined here (not skipped) because the actual
   * coordinator, once the configuration is initialized, might differ from what {@code
   * staticConfiguration} assumes — see {@link ClusterConfigurationModifier.CoordinatorOnly}'s
   * self-filtering.
   */
  private ClusterConfigurationInitializer<CurrentClusterConfiguration>
      getCurrentClusterConfigurationNonCoordinatorInitializer(
          final StaticConfiguration staticConfiguration,
          final Map<PartitionId, ExportingState> legacyExportingStates) {
    final Supplier<List<MemberId>> otherKnownMembers = initializationMembers(staticConfiguration);
    return FileInitializer.fromPersistedConfiguration(configurationFile, new ProtoBufSerializer())
        .recover(
            PersistedConfigurationIsBroken.class,
            new SyncInitializer<>(
                gossiperConfig.syncInitializerDelay(),
                clusterConfigurationGossiper,
                otherKnownMembers,
                managerActor,
                clusterConfigurationGossiper::queryCurrentClusterConfiguration,
                gossiperConfig.bootstrapTimeout(),
                CurrentClusterConfiguration.uninitialized()))
        .orThen(
            new GossipInitializer<>(
                clusterConfigurationGossiper,
                persistedCurrentClusterConfiguration::getConfiguration,
                clusterConfigurationGossiper::updateCurrentClusterConfiguration,
                managerActor,
                CurrentClusterConfiguration.uninitialized()))
        .andThen(exporterStateModifier(staticConfiguration, false))
        .andThen(
            PartitionDistributorInitializer
                .currentClusterConfigurationPartitionDistributorInitializer(staticConfiguration))
        .andThen(
            new PartitionGroupExportingStateInitializer(
                legacyExportingStates, staticConfiguration.localMemberId()))
        .andThen(new PhysicalTenantAvailabilityInitializer(staticConfiguration))
        .andThen(new PhysicalTenantProvisioningInitializer(staticConfiguration));
  }

  /**
   * Mirrors {@link #getCurrentClusterConfigurationNonCoordinatorInitializer}'s shape: sync from
   * other members, and — unlike the non-coordinator chain — self-generate from static configuration
   * as a last resort if sync times out uninitialized.
   */
  private ClusterConfigurationInitializer<CurrentClusterConfiguration>
      getCurrentClusterConfigurationCoordinatorInitializer(
          final StaticConfiguration staticConfiguration,
          final Map<PartitionId, ExportingState> legacyExportingStates) {
    final Supplier<List<MemberId>> otherKnownMembers = initializationMembers(staticConfiguration);
    return FileInitializer.fromPersistedConfiguration(configurationFile, new ProtoBufSerializer())
        .orThen(
            new SyncInitializer<>(
                gossiperConfig.syncInitializerDelay(),
                clusterConfigurationGossiper,
                otherKnownMembers,
                managerActor,
                clusterConfigurationGossiper::queryCurrentClusterConfiguration,
                gossiperConfig.bootstrapTimeout(),
                CurrentClusterConfiguration.uninitialized()))
        .orThen(new StaticInitializer<>(staticConfiguration::generateCurrentClusterConfiguration))
        .andThen(exporterStateModifier(staticConfiguration, true))
        .andThen(
            PartitionDistributorInitializer
                .currentClusterConfigurationPartitionDistributorInitializer(staticConfiguration))
        .andThen(
            new PartitionGroupExportingStateInitializer(
                legacyExportingStates, staticConfiguration.localMemberId()))
        .andThen(new PhysicalTenantAvailabilityInitializer(staticConfiguration))
        .andThen(new PhysicalTenantProvisioningInitializer(staticConfiguration));
  }

  private static PartitionGroupExporterStateInitializer exporterStateModifier(
      final StaticConfiguration staticConfiguration, final boolean isCoordinator) {
    return new PartitionGroupExporterStateInitializer(
        staticConfiguration.partitionConfigPerPhysicalTenant().entrySet().stream()
            .collect(
                Collectors.toMap(
                    Entry::getKey, e -> e.getValue().exporting().exporters().keySet())),
        staticConfiguration.localMemberId(),
        isCoordinator);
  }

  private Supplier<List<MemberId>> initializationMembers(
      final StaticConfiguration staticConfiguration) {
    return () ->
        Stream.concat(
                staticConfiguration.clusterMembers().stream(),
                membershipService.getMembers().stream().map(member -> member.id()))
            .filter(memberId -> !memberId.equals(localMemberId))
            .distinct()
            .toList();
  }

  /** Starts ClusterConfigurationManager which initializes ClusterConfiguration */
  public ActorFuture<Void> start(
      final ActorSchedulingService actorSchedulingService,
      final StaticConfiguration staticConfiguration,
      final Map<PartitionId, ExportingState> legacyExportingStates) {
    return startGossiper(actorSchedulingService)
        .andThen(
            () ->
                startClusterTopologyServices(
                    actorSchedulingService, staticConfiguration, legacyExportingStates),
            Runnable::run);
  }

  private ActorFuture<Void> startGossiper(final ActorSchedulingService actorSchedulingService) {
    return actorSchedulingService
        .submitActor(gossipActor)
        .andThen(clusterConfigurationGossiper::start, Runnable::run);
  }

  private CompletableActorFuture<Void> startClusterTopologyServices(
      final ActorSchedulingService actorSchedulingService,
      final StaticConfiguration staticConfiguration,
      final Map<PartitionId, ExportingState> legacyExportingStates) {
    final var result = new CompletableActorFuture<Void>();

    configurationRequestServer.start();

    // Start gossiper first so that when ClusterConfigurationManager initializes the configuration,
    // it can immediately gossip it.
    actorSchedulingService
        .submitActor(managerActor)
        .onComplete(
            (ok, error) -> {
              if (error != null) {
                result.completeExceptionally(error);
                return;
              }
              // Registered here rather than in the constructor: registerGlobalChangeAppliers goes
              // through managerActor's executor.
              clusterConfigurationManager.registerGlobalChangeAppliers(
                  new GlobalConfigurationChangeAppliersImpl(
                      new NoopClusterMembershipChangeExecutor(), clusterChangeExecutor));
              final var coordinatorMemberId =
                  ClusterConfigurationCoordinatorSupplier.ofMembers(
                          staticConfiguration.clusterMembers())
                      .getDefaultCoordinator();
              final var isCoordinator = coordinatorMemberId.equals(localMemberId);
              final ClusterConfigurationInitializer<CurrentClusterConfiguration>
                  currentClusterConfigurationInitializer =
                      isCoordinator
                          ? getCurrentClusterConfigurationCoordinatorInitializer(
                              staticConfiguration, legacyExportingStates)
                          : getCurrentClusterConfigurationNonCoordinatorInitializer(
                              staticConfiguration, legacyExportingStates);
              clusterConfigurationManager
                  .start(currentClusterConfigurationInitializer)
                  .onComplete(result);
            });
    return result;
  }

  public ActorFuture<CurrentClusterConfiguration> getClusterConfiguration() {
    return clusterConfigurationManager.getMultiConfiguration();
  }

  public Optional<ConfigurationChangeCoordinator> getTopologyChangeCoordinator() {
    return Optional.ofNullable(configurationChangeCoordinator);
  }

  @Override
  public ActorFuture<Void> closeAsync() {
    if (configurationRequestServer != null) {
      configurationRequestServer.close();
    }
    clusterConfigurationGossiper.close();
    clusterConfigurationManager.close();
    return managerActor.closeAsync().andThen(gossipActor::closeAsync, Runnable::run);
  }

  public void registerPartitionChangeExecutors(
      final String groupId,
      final PartitionChangeExecutor partitionChangeExecutor,
      final PartitionScalingChangeExecutor partitionScalingChangeExecutor) {

    registerPartitionChangeExecutors(
        groupId,
        partitionChangeExecutor,
        partitionScalingChangeExecutor,
        new RestoreChangeExecutor.DeniedRestoreChangeExecutor());
  }

  public void registerPartitionChangeExecutors(
      final String groupId,
      final PartitionChangeExecutor partitionChangeExecutor,
      final PartitionScalingChangeExecutor partitionScalingChangeExecutor,
      final RestoreChangeExecutor restoreChangeExecutor) {
    managerActor.run(
        () -> {
          final ModeChangeExecutor modeChangeExecutor = modeChangeExecutorPerTenant.get(groupId);
          Objects.requireNonNull(
              modeChangeExecutor,
              "ModeChangeExecutor not set before registering topology appliers.");

          clusterConfigurationManager.registerPartitionGroupChangeAppliers(
              groupId,
              new PartitionGroupConfigurationChangeAppliersImpl(
                  partitionChangeExecutor,
                  partitionScalingChangeExecutor,
                  modeChangeExecutor,
                  restoreChangeExecutor));
        });
  }

  public void removePartitionChangeExecutor(final String groupId) {
    clusterConfigurationManager.removePartitionGroupChangeAppliers(groupId);
  }

  public void registerModeChangeExecutor(
      final String groupId, final ModeChangeExecutor modeChangeExecutor) {
    managerActor.run(() -> modeChangeExecutorPerTenant.put(groupId, modeChangeExecutor));
  }

  public void removeModeChangeExecutor(final String groupId) {
    managerActor.run(() -> modeChangeExecutorPerTenant.remove(groupId));
  }

  public void registerTopologyChangedListener(final InconsistentConfigurationListener listener) {
    clusterConfigurationManager.registerTopologyChangedListener(listener);
  }

  public void removeTopologyChangedListener() {
    clusterConfigurationManager.removeTopologyChangedListener();
  }

  public void registerRequestValidator(
      final @Nullable String physicalTenantId,
      final ClusterConfigurationRequestValidator<?, ?> validator) {
    managerActor.run(() -> validators.registerValidator(physicalTenantId, validator));
  }

  public void removeRequestValidator(
      final @Nullable String physicalTenantId,
      final Class<? extends ClusterConfigurationManagementRequest> requestType) {
    managerActor.run(() -> validators.deregisterValidator(physicalTenantId, requestType));
  }

  @Override
  public void addUpdateListener(final ClusterConfigurationUpdateListener listener) {
    clusterConfigurationGossiper.addUpdateListener(listener);
  }

  @Override
  public void removeUpdateListener(final ClusterConfigurationUpdateListener listener) {
    clusterConfigurationGossiper.removeUpdateListener(listener);
  }
}
