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
import io.camunda.zeebe.dynamic.config.ClusterConfigurationInitializer.FileInitializer;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationInitializer.GossipInitializer;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationInitializer.InitializerError.PersistedConfigurationIsBroken;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationInitializer.StaticInitializer;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationInitializer.SyncInitializer;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationManager.InconsistentConfigurationListener;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationCoordinatorSupplier;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestsHandler;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestServer;
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
import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossiper;
import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossiperConfig;
import io.camunda.zeebe.dynamic.config.metrics.TopologyManagerMetrics;
import io.camunda.zeebe.dynamic.config.metrics.TopologyMetrics;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
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
import java.util.Objects;
import java.util.Optional;
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
  static final boolean USE_NEW_CONFIG = false;

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
  private final MemberId localMemberId;
  private final boolean useNewConfig;
  private ModeChangeExecutor modeChangeExecutor;

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
            topologyMetrics);
    configurationChangeCoordinator =
        new ConfigurationChangeCoordinatorImpl(
            clusterConfigurationManager, localMemberId, managerActor);
    configurationRequestServer =
        new ClusterConfigurationRequestServer(
            communicationService,
            new ProtoBufSerializer(),
            new ClusterConfigurationManagementRequestsHandler(
                configurationChangeCoordinator, localMemberId, managerActor));

    if (useNewConfig) {
      // Plain field wiring — safe here since it happens before either actor is submitted to a
      // scheduler. registerGlobalChangeAppliers (below) goes through the manager's executor, so it
      // is deferred to startClusterTopologyServices, after managerActor is actually scheduled — see
      // that method for why.
      clusterConfigurationGossiper.setCurrentConfigurationUpdateHandler(
          clusterConfigurationManager::onGossipReceivedCurrent);
      clusterConfigurationManager.setCurrentConfigurationGossiper(
          clusterConfigurationGossiper::updateCurrentClusterConfiguration);
    } else {
      clusterConfigurationManager.setConfigurationGossiper(
          clusterConfigurationGossiper::updateClusterConfiguration);
    }
  }

  private ClusterConfigurationInitializer getNonCoordinatorInitializer(
      final StaticConfiguration staticConfiguration) {
    final var otherKnownMembers =
        staticConfiguration.clusterMembers().stream()
            .filter(m -> !m.equals(staticConfiguration.localMemberId()))
            .toList();
    return new FileInitializer(configurationFile, new ProtoBufSerializer())
        // Recover via sync to ensure that we don't gossip an uninitialized configuration.
        // This is important so that we don't silently revert to uninitialized configuration when
        // multiple members have a broken configuration file at the same time, for example because
        // of a serialization bug.
        .recover(
            PersistedConfigurationIsBroken.class,
            new SyncInitializer(
                gossiperConfig.syncInitializerDelay(),
                clusterConfigurationGossiper,
                otherKnownMembers,
                managerActor,
                clusterConfigurationGossiper::queryClusterConfiguration))
        .orThen(
            new GossipInitializer(
                clusterConfigurationGossiper,
                persistedClusterConfiguration,
                clusterConfigurationGossiper::updateClusterConfiguration,
                managerActor))
        .andThen(
            new ExporterStateInitializer(
                staticConfiguration.partitionConfig().exporting().exporters().keySet(),
                staticConfiguration.localMemberId(),
                managerActor,
                false))
        .andThen(new RoutingStateInitializer(staticConfiguration.partitionCount()))
        // Must be initialized by the coordinator only. However, we still define it here because the
        // actual coordinator might be different from what is provided in the static configuration.
        // These initializers will be skipped if they are not running on the latest coordinator
        // based on the initialized configuration.
        .andThen(new PartitionDistributorInitializer(staticConfiguration))
        .andThen(new ClusterIdInitializer(staticConfiguration.clusterId(), localMemberId));
  }

  private ClusterConfigurationInitializer getCoordinatorInitializer(
      final StaticConfiguration staticConfiguration) {
    final var otherKnownMembers =
        staticConfiguration.clusterMembers().stream()
            .filter(m -> !m.equals(staticConfiguration.localMemberId()))
            .toList();
    return new FileInitializer(configurationFile, new ProtoBufSerializer())
        .orThen(
            new SyncInitializer(
                gossiperConfig.syncInitializerDelay(),
                clusterConfigurationGossiper,
                otherKnownMembers,
                managerActor,
                clusterConfigurationGossiper::queryClusterConfiguration))
        .orThen(new StaticInitializer(staticConfiguration))
        .andThen(
            new ExporterStateInitializer(
                staticConfiguration.partitionConfig().exporting().exporters().keySet(),
                staticConfiguration.localMemberId(),
                managerActor,
                true))
        .andThen(new RoutingStateInitializer(staticConfiguration.partitionCount()))
        // Must be initialized by the coordinator only
        .andThen(new PartitionDistributorInitializer(staticConfiguration))
        .andThen(new ClusterIdInitializer(staticConfiguration.clusterId(), localMemberId));
  }

  /** Starts ClusterConfigurationManager which initializes ClusterConfiguration */
  public ActorFuture<Void> start(
      final ActorSchedulingService actorSchedulingService,
      final StaticConfiguration staticConfiguration) {
    return startGossiper(actorSchedulingService)
        .andThen(
            () -> startClusterTopologyServices(actorSchedulingService, staticConfiguration),
            Runnable::run);
  }

  private ActorFuture<Void> startGossiper(final ActorSchedulingService actorSchedulingService) {
    return actorSchedulingService
        .submitActor(gossipActor)
        .andThen(clusterConfigurationGossiper::start, Runnable::run);
  }

  private CompletableActorFuture<Void> startClusterTopologyServices(
      final ActorSchedulingService actorSchedulingService,
      final StaticConfiguration staticConfiguration) {
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
              } else if (useNewConfig) {
                // Registered here rather than in the constructor: registerGlobalChangeAppliers goes
                // through managerActor's executor, and a job submitted before the actor is
                // scheduled
                // is not guaranteed to be processed before other work; doing it right after the
                // actor is confirmed scheduled avoids racing gossip/apply against the registration.
                // The global (cluster-membership) appliers need nothing broker-supplied: cluster
                // membership changes have no real executor in production (see
                // NoopClusterMembershipChangeExecutor's usage in the legacy path too), and
                // clusterChangeExecutor is already available here. This is therefore registered
                // once, unconditionally, unlike the per-tenant partition-group appliers which the
                // broker must register per physical tenant.
                clusterConfigurationManager.registerGlobalChangeAppliers(
                    new GlobalConfigurationChangeAppliersImpl(
                        new NoopClusterMembershipChangeExecutor(), clusterChangeExecutor));
                // Intermediate migration step: only the static initializer exists for the new
                // model, used unconditionally for both coordinator and non-coordinator roles (see
                // CurrentClusterConfigurationInitializer's class doc). File/gossip/sync recovery
                // and the coordinator/non-coordinator split are a follow-up.
                clusterConfigurationManager
                    .start(
                        new CurrentClusterConfigurationInitializer.StaticInitializer(
                            staticConfiguration))
                    .onComplete(result);
              } else {
                final var coordinatorMemberId =
                    ClusterConfigurationCoordinatorSupplier.ofMembers(
                            staticConfiguration.clusterMembers())
                        .getDefaultCoordinator();
                final var isCoordinator = coordinatorMemberId.equals(localMemberId);
                final ClusterConfigurationInitializer clusterConfigurationInitializer =
                    isCoordinator
                        ? getCoordinatorInitializer(staticConfiguration)
                        : getNonCoordinatorInitializer(staticConfiguration);
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

  /** Returns the full multi-partition-group configuration. Only valid when the new model is on. */
  public ActorFuture<CurrentClusterConfiguration> getMultiConfiguration() {
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
    return managerActor.closeAsync().andThen(gossipActor::closeAsync, Runnable::run);
  }

  public void registerPartitionChangeExecutors(
      final PartitionChangeExecutor partitionChangeExecutor,
      final PartitionScalingChangeExecutor partitionScalingChangeExecutor) {
    managerActor.run(
        () -> {
          Objects.requireNonNull(
              modeChangeExecutor,
              "ModeChangeExecutor not set before registering topology appliers.");
          clusterConfigurationManager.registerTopologyChangeAppliers(
              new ConfigurationChangeAppliersImpl(
                  partitionChangeExecutor,
                  new NoopClusterMembershipChangeExecutor(),
                  partitionScalingChangeExecutor,
                  clusterChangeExecutor,
                  modeChangeExecutor));
        });
  }

  public void removePartitionChangeExecutor() {
    clusterConfigurationManager.removeTopologyChangeAppliers();
  }

  public void registerModeChangeExecutor(final ModeChangeExecutor modeChangeExecutor) {
    managerActor.run(() -> this.modeChangeExecutor = modeChangeExecutor);
  }

  public void removeModeChangeExecutor() {
    managerActor.run(() -> modeChangeExecutor = null);
  }

  /**
   * Registers the appliers for a single partition group (physical tenant) on the new
   * multi-partition-group model, keyed by {@code groupId}. Unlike {@link
   * #registerPartitionChangeExecutors}/{@link #registerModeChangeExecutor} (one shared registration
   * for the single legacy group), this is called once per physical tenant — each tenant's own
   * executors are scoped to that tenant only.
   *
   * <p>Not yet called by any broker code: broker integration (registering every physical tenant's
   * {@code PartitionManagerImpl}/{@code PartitionModeHandler} here instead of only the default
   * tenant's) is a follow-up.
   */
  public void registerPartitionGroupChangeExecutors(
      final String groupId,
      final PartitionChangeExecutor partitionChangeExecutor,
      final PartitionScalingChangeExecutor partitionScalingChangeExecutor,
      final ModeChangeExecutor modeChangeExecutor) {
    managerActor.run(
        () ->
            clusterConfigurationManager.registerPartitionGroupChangeAppliers(
                groupId,
                new PartitionGroupConfigurationChangeAppliersImpl(
                    partitionChangeExecutor,
                    partitionScalingChangeExecutor,
                    clusterChangeExecutor,
                    modeChangeExecutor)));
  }

  public void removePartitionGroupChangeExecutors(final String groupId) {
    managerActor.run(() -> clusterConfigurationManager.removePartitionGroupChangeAppliers(groupId));
  }

  public void registerTopologyChangedListener(final InconsistentConfigurationListener listener) {
    clusterConfigurationManager.registerTopologyChangedListener(listener);
  }

  public void removeTopologyChangedListener() {
    clusterConfigurationManager.removeTopologyChangedListener();
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
