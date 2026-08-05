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
import io.camunda.cluster.PhysicalTenantIds;
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
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeAppliersImpl;
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
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.util.RequestValidatorRegistry;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.AsyncClosable;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.VisibleForTesting;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public final class ClusterConfigurationManagerService
    implements ClusterConfigurationUpdateNotifier, AsyncClosable {
  public static final String TOPOLOGY_FILE_NAME = ".topology.meta";

  /**
   * Static feature flag switching the manager between the legacy single-group model and the new
   * multi-partition-group model. Kept {@code false} in production for now; the new path is
   * exercised via the {@code useNewConfig} constructor parameter (see {@link #USE_NEW_CONFIG}).
   * When {@code false}, the legacy code path runs completely unchanged.
   */
  public static final boolean USE_NEW_CONFIG = false;

  private final ClusterConfigurationManagerImpl clusterConfigurationManager;
  private final ClusterConfigurationGossiper clusterConfigurationGossiper;
  // Exactly one of persistedClusterConfiguration (legacy model) /
  // persistedCurrentClusterConfiguration
  // (new model) is set, matching ClusterConfigurationManagerImpl.USE_NEW_CONFIG. Both classes read
  // the same on-disk file, distinguished by an internal header version, so only one may ever open
  // it.
  private final @Nullable PersistedClusterConfiguration persistedClusterConfiguration;
  private final @Nullable PersistedCurrentClusterConfiguration persistedCurrentClusterConfiguration;
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
  private final boolean useNewConfig;
  private final Map<String, ModeChangeExecutor> modeChangeExecutorPerTenant = new HashMap<>();
  private Set<String> knownGroups = Set.of();

  public ClusterConfigurationManagerService(
      final Path dataRootDirectory,
      final ClusterCommunicationService communicationService,
      final ClusterMembershipService memberShipService,
      final ClusterConfigurationGossiperConfig config,
      final ClusterChangeExecutor clusterChangeExecutor,
      final MeterRegistry meterRegistry) {
    this(
        dataRootDirectory,
        communicationService,
        memberShipService,
        config,
        clusterChangeExecutor,
        meterRegistry,
        USE_NEW_CONFIG);
  }

  /**
   * @param useNewConfig overrides {@link ClusterConfigurationManagerService#USE_NEW_CONFIG} for
   *     testing; production code always goes through the constructor above, which uses the
   *     compile-time flag.
   */
  @VisibleForTesting
  ClusterConfigurationManagerService(
      final Path dataRootDirectory,
      final ClusterCommunicationService communicationService,
      final ClusterMembershipService memberShipService,
      final ClusterConfigurationGossiperConfig config,
      final ClusterChangeExecutor clusterChangeExecutor,
      final MeterRegistry meterRegistry,
      final boolean useNewConfig) {
    this.useNewConfig = useNewConfig;
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

    if (useNewConfig) {
      persistedClusterConfiguration = null;
      persistedCurrentClusterConfiguration =
          PersistedCurrentClusterConfiguration.ofFile(configurationFile, new ProtoBufSerializer());
      clusterConfigurationManager =
          new ClusterConfigurationManagerImpl(
              managerActor,
              localMemberId,
              persistedCurrentClusterConfiguration,
              topologyManagerMetrics);
    } else {
      persistedCurrentClusterConfiguration = null;
      persistedClusterConfiguration =
          PersistedClusterConfiguration.ofFile(configurationFile, new ProtoBufSerializer());
      clusterConfigurationManager =
          new ClusterConfigurationManagerImpl(
              managerActor, localMemberId, persistedClusterConfiguration, topologyManagerMetrics);
    }

    clusterConfigurationGossiper =
        new ClusterConfigurationGossiper(
            gossipActor,
            communicationService,
            memberShipService,
            new ProtoBufSerializer(),
            config,
            // Dead on the new model: setCurrentConfigurationUpdateHandler (below) makes the
            // gossiper prefer the new-model handler, so this legacy one is never invoked.
            useNewConfig ? ignored -> {} : clusterConfigurationManager::onGossipReceived,
            useNewConfig ? clusterConfigurationManager::onGossipReceivedCurrent : null,
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

    if (useNewConfig) {
      clusterConfigurationManager.setCurrentConfigurationGossiper(
          clusterConfigurationGossiper::updateCurrentClusterConfiguration);
    } else {
      clusterConfigurationManager.setConfigurationGossiper(
          clusterConfigurationGossiper::updateClusterConfiguration);
    }
  }

  private ClusterConfigurationInitializer<ClusterConfiguration> getNonCoordinatorInitializer(
      final StaticConfiguration staticConfiguration,
      final Map<PartitionId, ExportingState> legacyExportingStates) {
    final Supplier<List<MemberId>> otherKnownMembers = initializationMembers(staticConfiguration);
    return FileInitializer.legacyFileInitializer(configurationFile, new ProtoBufSerializer())
        // Recover via sync to ensure that we don't gossip an uninitialized configuration.
        // This is important so that we don't silently revert to uninitialized configuration
        // when
        // multiple members have a broken configuration file at the same time, for example
        // because
        // of a serialization bug.
        .recover(
            PersistedConfigurationIsBroken.class,
            new SyncInitializer<>(
                gossiperConfig.syncInitializerDelay(),
                clusterConfigurationGossiper,
                otherKnownMembers,
                managerActor,
                clusterConfigurationGossiper::queryClusterConfiguration,
                gossiperConfig.bootstrapTimeout(),
                ClusterConfiguration.uninitialized()))
        .orThen(
            new GossipInitializer<>(
                clusterConfigurationGossiper,
                persistedClusterConfiguration::getConfiguration,
                clusterConfigurationGossiper::updateClusterConfiguration,
                managerActor,
                ClusterConfiguration.uninitialized()))
        .andThen(
            new ExporterStateInitializer(
                staticConfiguration.partitionConfig().exporting().exporters().keySet(),
                staticConfiguration.localMemberId(),
                managerActor,
                false))
        .andThen(new RoutingStateInitializer(staticConfiguration.partitionCount()))
        // Must be initialized by the coordinator only. However, we still define it here because
        // the
        // actual coordinator might be different from what is provided in the static
        // configuration.
        // These initializers will be skipped if they are not running on the latest coordinator
        // based on the initialized configuration.
        .andThen(
            PartitionDistributorInitializer.legacyPartitionDistributorInitializer(
                staticConfiguration))
        .andThen(new ClusterIdInitializer(staticConfiguration.clusterId(), localMemberId));
  }

  private ClusterConfigurationInitializer<ClusterConfiguration> getCoordinatorInitializer(
      final StaticConfiguration staticConfiguration,
      final Map<PartitionId, ExportingState> legacyExportingStates) {
    final Supplier<List<MemberId>> otherKnownMembers = initializationMembers(staticConfiguration);
    return FileInitializer.legacyFileInitializer(configurationFile, new ProtoBufSerializer())
        .orThen(
            new SyncInitializer<>(
                gossiperConfig.syncInitializerDelay(),
                clusterConfigurationGossiper,
                otherKnownMembers,
                managerActor,
                clusterConfigurationGossiper::queryClusterConfiguration,
                gossiperConfig.bootstrapTimeout(),
                ClusterConfiguration.uninitialized()))
        .orThen(new StaticInitializer<>(staticConfiguration::generateTopology))
        .andThen(
            new ExporterStateInitializer(
                staticConfiguration.partitionConfig().exporting().exporters().keySet(),
                staticConfiguration.localMemberId(),
                managerActor,
                true))
        .andThen(new RoutingStateInitializer(staticConfiguration.partitionCount()))
        // Must be initialized by the coordinator only
        .andThen(
            PartitionDistributorInitializer.legacyPartitionDistributorInitializer(
                staticConfiguration))
        .andThen(new ClusterIdInitializer(staticConfiguration.clusterId(), localMemberId));
  }

  /**
   * New-model counterpart of {@link #getNonCoordinatorInitializer}. Mirrors its shape exactly:
   * recover a broken file via sync, then wait on gossip from the coordinator; the coordinator-only
   * initializers below are still defined here (not skipped) because the actual coordinator, once
   * the configuration is initialized, might differ from what {@code staticConfiguration} assumes —
   * see {@link ClusterConfigurationModifier.CoordinatorOnly}'s self-filtering.
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
        .andThen(
            new PartitionGroupExporterStateInitializer(
                staticConfiguration.partitionConfig().exporting().exporters().keySet(),
                staticConfiguration.localMemberId()))
        .andThen(
            PartitionDistributorInitializer
                .currentClusterConfigurationPartitionDistributorInitializer(staticConfiguration))
        .andThen(
            new PartitionGroupExportingStateInitializer(
                legacyExportingStates, staticConfiguration.localMemberId()));
  }

  /**
   * New-model counterpart of {@link #getCoordinatorInitializer}. Mirrors its shape exactly: sync
   * from other members, and — unlike the non-coordinator chain — self-generate from static
   * configuration as a last resort if sync times out uninitialized.
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
        .andThen(
            new PartitionGroupExporterStateInitializer(
                staticConfiguration.partitionConfig().exporting().exporters().keySet(),
                staticConfiguration.localMemberId()))
        .andThen(
            PartitionDistributorInitializer
                .currentClusterConfigurationPartitionDistributorInitializer(staticConfiguration))
        .andThen(
            new PartitionGroupExportingStateInitializer(
                legacyExportingStates, staticConfiguration.localMemberId()));
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

    knownGroups =
        staticConfiguration.partitionIds().stream()
            .map(PartitionId::group)
            .collect(Collectors.toSet());

    configurationRequestServer.start();

    // Start gossiper first so that when ClusterConfigurationManager initializes the configuration,
    // it can immediately gossip it.
    actorSchedulingService
        .submitActor(managerActor)
        .onComplete(
            (ok, error) -> {
              if (error != null) {
                result.completeExceptionally(error);
              } else if (useNewConfig) {
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
              } else {
                final var coordinatorMemberId =
                    ClusterConfigurationCoordinatorSupplier.ofMembers(
                            staticConfiguration.clusterMembers())
                        .getDefaultCoordinator();
                final var isCoordinator = coordinatorMemberId.equals(localMemberId);
                final ClusterConfigurationInitializer<ClusterConfiguration>
                    clusterConfigurationInitializer =
                        isCoordinator
                            ? getCoordinatorInitializer(staticConfiguration, legacyExportingStates)
                            : getNonCoordinatorInitializer(
                                staticConfiguration, legacyExportingStates);
                clusterConfigurationManager
                    .start(clusterConfigurationInitializer)
                    .onComplete(result);
              }
            });
    return result;
  }

  public ActorFuture<ClusterConfiguration> getClusterTopology() {
    return clusterConfigurationManager.getClusterConfiguration();
  }

  public ActorFuture<CurrentClusterConfiguration> getClusterConfiguration() {
    if (useNewConfig) {
      return clusterConfigurationManager.getMultiConfiguration();
    } else {
      return clusterConfigurationManager
          .getClusterConfiguration()
          .thenApply(
              legacy -> {
                if (legacy != null) {
                  // the callers rely on having all physical tenants in the configuration. Until the
                  // feature flag is enabled, use a fake config made from default physical tenant.
                  // This aligns with the existing behavior in the broker where it derived the
                  // config from default tenant.
                  final var defaultGroupConfig = CurrentClusterConfiguration.fromLegacy(legacy);
                  final Map<String, PartitionGroupConfiguration> groups =
                      knownGroups.stream()
                          .collect(
                              Collectors.toMap(
                                  id -> id,
                                  id ->
                                      Objects.requireNonNull(
                                          defaultGroupConfig.partitionGroup(
                                              PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID))));
                  return new CurrentClusterConfiguration(
                      defaultGroupConfig.version(),
                      defaultGroupConfig.globalConfiguration(),
                      groups,
                      defaultGroupConfig.phasedChangeState());
                } else {
                  return null;
                }
              });
    }
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
          if (!useNewConfig && !PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID.equals(groupId)) {
            return; // noop
          }

          final ModeChangeExecutor modeChangeExecutor = modeChangeExecutorPerTenant.get(groupId);
          Objects.requireNonNull(
              modeChangeExecutor,
              "ModeChangeExecutor not set before registering topology appliers.");

          if (!useNewConfig) {
            clusterConfigurationManager.registerTopologyChangeAppliers(
                new ConfigurationChangeAppliersImpl(
                    partitionChangeExecutor,
                    new NoopClusterMembershipChangeExecutor(),
                    partitionScalingChangeExecutor,
                    clusterChangeExecutor,
                    modeChangeExecutor,
                    restoreChangeExecutor));
          } else {
            clusterConfigurationManager.registerPartitionGroupChangeAppliers(
                groupId,
                new PartitionGroupConfigurationChangeAppliersImpl(
                    partitionChangeExecutor,
                    partitionScalingChangeExecutor,
                    clusterChangeExecutor,
                    modeChangeExecutor,
                    restoreChangeExecutor));
          }
        });
  }

  public void removePartitionChangeExecutor(final String groupId) {
    if (!useNewConfig && !PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID.equals(groupId)) {
      return; // noop
    }
    if (!useNewConfig) {
      clusterConfigurationManager.removeTopologyChangeAppliers();
    } else {
      clusterConfigurationManager.removePartitionGroupChangeAppliers(groupId);
    }
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
