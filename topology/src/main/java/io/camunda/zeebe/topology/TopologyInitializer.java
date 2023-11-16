/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.utils.Version;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.topology.TopologyUpdateNotifier.TopologyUpdateListener;
import io.camunda.zeebe.topology.serializer.ClusterTopologySerializer;
import io.camunda.zeebe.topology.state.ClusterTopology;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initializes topology using different strategies.
 *
 * <h4>Initialization Process</h4>
 *
 * Each member is configured with a static configuration with initial set of cluster members and
 * partition distribution.
 *
 * <p>Both coordinator and other members first check the local persisted configuration to determine
 * the topology. If one exists, that is used to initialize the topology. See {@link
 * FileInitializer}. On bootstrap of the cluster, the local persisted topology is empty.
 * <li>When the local topology is empty, the coordinator queries cluster members in the static
 *     configuration for the current topology. See {@link SyncInitializer}. If any member replies
 *     with a valid topology, coordinator uses that one. If any member replies with an uninitialized
 *     topology, coordinator generates a new topology from the provided static configuration. See
 *     {@link StaticInitializer}.
 * <li>When the local topology is empty, a non-coordinating member waits until it receives a valid
 *     topology from the coordinator via gossip. See {@link GossipInitializer}.
 */
public interface TopologyInitializer {

  /**
   * Initializes the cluster topology.
   *
   * @return a future that completes with a topology which can be initialized or uninitialized
   */
  ActorFuture<ClusterTopology> initialize();

  /**
   * Chain initializers in oder. If this.initialize did not successfully initialize, then the
   * topology is initialized using the provided initializer
   *
   * @param after the next initializer used to initialize topology if the current one did not
   *     succeed.
   * @return a chained TopologyInitializer
   */
  default TopologyInitializer orThen(final TopologyInitializer after) {
    final TopologyInitializer actual = this;
    return () -> {
      final ActorFuture<ClusterTopology> chainedInitialize = new CompletableActorFuture<>();
      actual
          .initialize()
          .onComplete(
              (topology, error) -> {
                if (error != null || topology.isUninitialized()) {
                  after.initialize().onComplete(chainedInitialize);
                } else {
                  chainedInitialize.complete(topology);
                }
              });
      return chainedInitialize;
    };
  }

  /** Initialized topology from the locally persisted topology */
  class FileInitializer implements TopologyInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileInitializer.class);

    private final Path topologyFile;
    private final ClusterTopologySerializer serializer;

    public FileInitializer(final Path topologyFile, final ClusterTopologySerializer serializer) {
      this.topologyFile = topologyFile;
      this.serializer = serializer;
    }

    @Override
    public ActorFuture<ClusterTopology> initialize() {
      try {
        if (Files.exists(topologyFile)) {
          final var serializedTopology = Files.readAllBytes(topologyFile);
          if (serializedTopology.length > 0) {
            final var clusterTopology = serializer.decodeClusterTopology(serializedTopology);
            LOGGER.debug(
                "Initialized cluster topology '{}' from file '{}'", clusterTopology, topologyFile);
            return CompletableActorFuture.completed(clusterTopology);
          }
        }
      } catch (final Exception e) {
        return CompletableActorFuture.completedExceptionally(e);
      }

      return CompletableActorFuture.completed(ClusterTopology.uninitialized());
    }
  }

  /**
   * Initializes local topology from the topology received from other members via gossip.
   * Initialization completes successfully, when it receives a valid initialized topology from any
   * member. The future returned by initialize is never completed until a valid topology is
   * received.
   */
  class GossipInitializer implements TopologyInitializer, TopologyUpdateListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(GossipInitializer.class);
    private final io.camunda.zeebe.topology.TopologyUpdateNotifier topologyUpdateNotifier;
    private final PersistedClusterTopology persistedClusterTopology;
    private final Consumer<ClusterTopology> topologyGossiper;
    private final ActorFuture<ClusterTopology> initialized;

    private final ConcurrencyControl executor;

    public GossipInitializer(
        final TopologyUpdateNotifier topologyUpdateNotifier,
        final PersistedClusterTopology persistedClusterTopology,
        final Consumer<ClusterTopology> topologyGossiper,
        final ConcurrencyControl executor) {
      this.topologyUpdateNotifier = topologyUpdateNotifier;
      this.persistedClusterTopology = persistedClusterTopology;
      this.topologyGossiper = topologyGossiper;
      this.executor = executor;
      initialized = new CompletableActorFuture<>();
    }

    @Override
    public ActorFuture<ClusterTopology> initialize() {
      LOGGER.debug("Waiting for initial cluster topology via gossip.");
      topologyUpdateNotifier.addUpdateListener(this);
      if (persistedClusterTopology.isUninitialized()) {
        // When uninitialized, the member should gossip uninitialized topology so that the
        // coordinator is not waiting in SyncInitializer forever.

        // Check persisted cluster topology directly, so as not to overwrite and concurrently
        // received gossip
        topologyGossiper.accept(persistedClusterTopology.getTopology());
      }
      return initialized;
    }

    @Override
    public void onTopologyUpdated(final ClusterTopology clusterTopology) {
      executor.run(
          () -> {
            if (initialized.isDone()) {
              return;
            }
            if (!clusterTopology.isUninitialized()) {
              LOGGER.debug("Received cluster topology {} via gossip.", clusterTopology);
              initialized.complete(clusterTopology);
              topologyUpdateNotifier.removeUpdateListener(this);
            }
          });
    }
  }

  /**
   * Initializes topology by sending sync requests to other members. If any of them return a valid
   * topology, it will be initialized. If any of them returns an uninitialized topology, the future
   * returned by initialize completes as failed.
   */
  class SyncInitializer implements TopologyInitializer, TopologyUpdateListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncInitializer.class);
    private static final Duration SYNC_QUERY_RETRY_DELAY = Duration.ofSeconds(5);
    private final TopologyUpdateNotifier topologyUpdateNotifier;
    private final ActorFuture<ClusterTopology> initialized;
    private final List<MemberId> knownMembersToSync;
    private final ConcurrencyControl executor;
    private final Function<MemberId, ActorFuture<ClusterTopology>> syncRequester;

    public SyncInitializer(
        final TopologyUpdateNotifier topologyUpdateNotifier,
        final List<MemberId> knownMembersToSync,
        final ConcurrencyControl executor,
        final Function<MemberId, ActorFuture<ClusterTopology>> syncRequester) {
      this.topologyUpdateNotifier = topologyUpdateNotifier;
      this.knownMembersToSync = knownMembersToSync;
      this.executor = executor;
      this.syncRequester = syncRequester;
      initialized = new CompletableActorFuture<>();
    }

    @Override
    public ActorFuture<ClusterTopology> initialize() {
      if (knownMembersToSync.isEmpty()) {
        initialized.complete(ClusterTopology.uninitialized());
      } else {
        LOGGER.debug("Querying members {} before initializing ClusterTopology", knownMembersToSync);
        topologyUpdateNotifier.addUpdateListener(this);
        knownMembersToSync.forEach(this::tryInitializeFrom);
      }
      return initialized;
    }

    private void tryInitializeFrom(final MemberId memberId) {
      requestSync(memberId)
          .onComplete(
              (topology, error) -> {
                if (initialized.isDone()) {
                  return;
                }
                if (error != null) {
                  LOGGER.trace(
                      "Failed to get a response for cluster topology sync query to {}. Will retry.",
                      memberId,
                      error);
                } else if (topology == null) {
                  LOGGER.trace("Received null cluster topology from {}. Will retry.", memberId);
                } else if (topology.isUninitialized()) {
                  LOGGER.trace("Cluster topology is uninitialized in {}", memberId);
                  initialized.complete(topology);
                  return;
                } else {
                  LOGGER.debug("Received cluster topology {} from {}", topology, memberId);
                  onTopologyUpdated(topology);
                  return;
                }
                // retry
                if (!initialized.isDone()) {
                  executor.schedule(SYNC_QUERY_RETRY_DELAY, () -> tryInitializeFrom(memberId));
                }
              });
    }

    private ActorFuture<ClusterTopology> requestSync(final MemberId memberId) {
      return syncRequester.apply(memberId);
    }

    @Override
    public void onTopologyUpdated(final ClusterTopology clusterTopology) {
      executor.run(
          () -> {
            if (initialized.isDone()) {
              return;
            }
            if (!clusterTopology.isUninitialized()) {
              initialized.complete(clusterTopology);
              topologyUpdateNotifier.removeUpdateListener(this);
            }
          });
    }
  }

  /** Initialized topology from the given static partition distribution */
  class StaticInitializer implements TopologyInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(StaticInitializer.class);

    private final StaticConfiguration staticConfiguration;

    public StaticInitializer(final StaticConfiguration staticConfiguration) {
      this.staticConfiguration = staticConfiguration;
    }

    @Override
    public ActorFuture<ClusterTopology> initialize() {
      try {
        final var topology = staticConfiguration.generateTopology();
        LOGGER.debug("Generated cluster topology from provided configuration. {}", topology);
        return CompletableActorFuture.completed(topology);
      } catch (final Exception e) {
        return CompletableActorFuture.completedExceptionally(e);
      }
    }
  }

  /** Initializer that allows rolling update from 8.3.x to 8.4.x */
  class RollingUpdateAwareInitializerV83ToV84 implements TopologyInitializer {
    private static final Logger LOGGER =
        LoggerFactory.getLogger(RollingUpdateAwareInitializerV83ToV84.class);
    private static final Duration RETRY_DELAY = Duration.ofMillis(200);
    private final ClusterMembershipService membershipService;
    private final StaticInitializer staticInitializer;
    private final int staticClusterSize;
    private final ConcurrencyControl executor;
    private final CompletableActorFuture<ClusterTopology> initializeFuture;

    public RollingUpdateAwareInitializerV83ToV84(
        final ClusterMembershipService membershipService,
        final StaticConfiguration staticConfiguration,
        final ConcurrencyControl executor) {
      this.membershipService = membershipService;
      staticInitializer = new StaticInitializer(staticConfiguration);
      staticClusterSize = staticConfiguration.clusterMembers().size();
      this.executor = executor;
      initializeFuture = new CompletableActorFuture<>();
    }

    @Override
    public ActorFuture<ClusterTopology> initialize() {
      if (staticClusterSize == 1) {
        // Cluster will be initialized via normal static initializer
        initializeFuture.complete(ClusterTopology.uninitialized());
        return initializeFuture;
      }

      final Version version = membershipService.getLocalMember().version();
      if (isVersion84(version)) {
        final boolean knowOtherMembers = hasOtherReachableMembers(membershipService);
        if (knowOtherMembers && isRollingUpdate(membershipService)) {
          LOGGER.debug(
              "Cluster is doing rolling update. Cannot initialize cluster topology via gossip. Initializing cluster topology from static configuration.");
          staticInitializer.initialize().onComplete(initializeFuture);
        } else if (!knowOtherMembers) {
          LOGGER.debug(
              "No other members are reachable. Cannot initialize topology. Will retry in {}",
              RETRY_DELAY);
          executor.schedule(RETRY_DELAY, this::initialize);
        } else {
          // do not initialize. It is not rolling update so initialize via gossip or sync
          initializeFuture.complete(ClusterTopology.uninitialized());
        }
      } else {
        initializeFuture.complete(ClusterTopology.uninitialized());
      }
      return initializeFuture;
    }

    private boolean hasOtherReachableMembers(final ClusterMembershipService membershipService) {
      return membershipService.getMembers().stream()
          .map(Member::id)
          .anyMatch(memberId -> !memberId.equals(membershipService.getLocalMember().id()));
    }

    private boolean isRollingUpdate(final ClusterMembershipService membershipService) {
      final List<Member> otherMembers =
          membershipService.getMembers().stream()
              .filter(member -> !member.id().equals(membershipService.getLocalMember().id()))
              .toList();
      final var cannotDetermineVersionOfOtherMembers =
          otherMembers.stream().map(Member::version).noneMatch(Objects::nonNull);
      if (cannotDetermineVersionOfOtherMembers) {
        // This is mainly required for testing, where the DiscoveryMembershipProvider cannot
        // determine version of remote members.
        LOGGER.warn(
            "Cannot determine version of local member. Assuming this is not rolling update and skip initialization.");
        return false;
      }
      return otherMembers.stream().map(Member::version).anyMatch(this::isVersion83);
    }

    private boolean isVersion84(final Version version) {
      if (version == null) {
        // This is mainly required for testing, where the DiscoveryMembershipProvider cannot
        // determine version of remote members.
        LOGGER.warn(
            "Cannot determine version of local member. Assuming this is not rolling update and skip initialization.");
        return false;
      }
      return version.major() == 8 && version.minor() == 4;
    }

    private boolean isVersion83(final Version version) {
      return version.major() == 8 && version.minor() == 3;
    }
  }
}
