/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import io.atomix.cluster.MemberId;
import io.atomix.raft.LeadershipTransferResult;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.application.commons.secrets.PhysicalTenantSecretStores;
import io.camunda.application.commons.secrets.PhysicalTenantSecretStores.Owned;
import io.camunda.cluster.DefaultPhysicalTenantRegistry;
import io.camunda.cluster.PhysicalTenant;
import io.camunda.cluster.PhysicalTenantHealth;
import io.camunda.cluster.PhysicalTenantHealth.State;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.physicaltenants.PhysicalTenantEnvironments;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.zeebe.broker.PhysicalTenantChangeException.Reason;
import io.camunda.zeebe.broker.partitioning.PartitionLeadershipHandover;
import io.camunda.zeebe.broker.partitioning.PartitionLeadershipHandover.Report;
import io.camunda.zeebe.broker.partitioning.PartitionLeadershipReclaim;
import io.camunda.zeebe.broker.partitioning.PartitionLeadershipReclaim.PartitionReclaim;
import io.camunda.zeebe.broker.partitioning.PartitionLeadershipReclaim.ReclaimPartition;
import io.camunda.zeebe.broker.partitioning.PartitionLeadershipReclaim.ReplayLag;
import io.camunda.zeebe.broker.partitioning.PartitionManager;
import io.camunda.zeebe.broker.partitioning.PartitionRestartSnapshots;
import io.camunda.zeebe.broker.partitioning.PartitionRestartSnapshots.PartitionSnapshot;
import io.camunda.zeebe.broker.partitioning.PartitionRestartSnapshots.SnapshotPartition;
import io.camunda.zeebe.broker.partitioning.PartitionRestartSnapshots.TakenSnapshot;
import io.camunda.zeebe.broker.system.PhysicalTenantContext;
import io.camunda.zeebe.broker.system.PhysicalTenantModules;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.RaftCfg;
import io.camunda.zeebe.broker.system.configuration.RaftCfg.TenantRestartHandover;
import io.camunda.zeebe.gateway.GatewayPhysicalTenants;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Adds, replaces and removes physical tenants on this node while it runs, without a node restart.
 * The other tenants keep serving.
 *
 * <p>A change carries the whole configuration subtree of one tenant, with names relative to {@code
 * camunda.physical-tenants.<id>}. It passes the boot validations before anything changes: the
 * resolution of the whole tenant set, the exporters of the tenant and the per-tenant broker
 * validations. A change that fails them leaves the node as it was.
 *
 * <p>A change stops and starts the tenant module: the broker module through {@link
 * PhysicalTenantModules} and the gateway module through {@link GatewayPhysicalTenants}. The HTTP
 * security chains of the Camunda Security Library are built once at startup, so the REST API only
 * serves the incarnation of a tenant that existed at startup.
 *
 * <p>Before a replaced tenant's module stops, the member takes, if configured, a snapshot of every
 * partition of the tenant (see {@link PartitionRestartSnapshots}), so the restarted replicas replay
 * less, and then hands the leadership of the tenant's partitions it leads to other replicas (see
 * {@link PartitionLeadershipHandover}), so the partitions keep serving through the restart. Once
 * the module started again, it takes back the leadership of the partitions it handed over as soon
 * as their replicas caught up (see {@link PartitionLeadershipReclaim}), so the leader distribution
 * is the same after the restart; the change completes after that. A partition no replica takes over
 * is restarted as it is, and {@link #lastRestart} says why. {@code
 * camunda.cluster.raft.rebalance.tenant-restart-handover} and {@code
 * camunda.cluster.raft.rebalance.tenant-restart-snapshot} choose what is done.
 *
 * <p>Only one change is applied at a time; a concurrent change is rejected, not queued.
 */
@NullMarked
public final class PhysicalTenantLifecycleService {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;
  private static final Duration HANDOVER_SLACK = Duration.ofSeconds(5);
  // a follower's replayed position trails the end of its log by the commands whose events the
  // leader has not written yet; under load that is a few dozen positions
  private static final long CAUGHT_UP_LAG = 100;

  private final Broker broker;
  private final SystemContextLoader systemContextLoader;
  private final Camunda rootCamunda;
  private final DefaultPhysicalTenantRegistry registry;
  private final Duration timeout;
  private final List<PhysicalTenantResources> resources;
  private static final SecretStoreRegistry NOT_BUILT_YET = new SecretStoreRegistry(Map.of());

  private final PhysicalTenantSecretStores secretStores;
  // the job secret stores built for a runtime context, released when the context is closed; the
  // stores of a boot context belong to the application context
  private final Map<PhysicalTenantContext, Owned<SecretStoreRegistry>> ownedSecretStores =
      Collections.synchronizedMap(new IdentityHashMap<>());
  private final ReentrantLock changeLock = new ReentrantLock();
  private final Map<String, Camunda> configurations = new ConcurrentHashMap<>();
  private final Map<String, PhysicalTenantContext> contexts = new ConcurrentHashMap<>();
  private final Map<String, TenantRestart> lastRestarts = new ConcurrentHashMap<>();
  private ConfigurableEnvironment environment;

  public PhysicalTenantLifecycleService(
      final Broker broker,
      final SystemContextLoader systemContextLoader,
      final ConfigurableEnvironment bootEnvironment,
      final Camunda rootCamunda,
      final PhysicalTenantResolver bootResolver,
      final DefaultPhysicalTenantRegistry registry,
      final Duration timeout,
      final List<PhysicalTenantResources> resources,
      final PhysicalTenantSecretStores secretStores) {
    this.broker = broker;
    this.resources = List.copyOf(resources);
    this.secretStores = secretStores;
    this.systemContextLoader = systemContextLoader;
    environment = bootEnvironment;
    this.rootCamunda = rootCamunda;
    this.registry = registry;
    this.timeout = timeout;
    configurations.putAll(bootResolver.getAll());
    contexts.putAll(broker.getSystemContext().getPhysicalTenantContexts());
    broker
        .start()
        .thenAccept(
            started ->
                registry.tenants().forEach(tenant -> markHealthyWhenReady(tenant, modules())));
  }

  /**
   * Adds a tenant and starts its module.
   *
   * @return the new incarnation; it is {@link State#HEALTHY} once its partitions are installed
   * @throws PhysicalTenantChangeException if the change is rejected, or the module did not start
   */
  public PhysicalTenant add(
      final String physicalTenantId, final Map<String, Object> configuration) {
    return add(physicalTenantId, configuration, UUID.randomUUID());
  }

  /**
   * Adds a tenant as the given incarnation, for an incarnation that the cluster agreed on.
   *
   * @see #add(String, Map)
   */
  public PhysicalTenant add(
      final String physicalTenantId,
      final Map<String, Object> configuration,
      final UUID incarnationId) {
    return underChangeLock(
        () -> {
          if (registry.find(physicalTenantId).isPresent()) {
            throw rejected(Reason.CONFLICT, physicalTenantId, "it already exists", null);
          }
          if (registry.isRemoved(physicalTenantId)) {
            throw rejected(
                Reason.CONFLICT,
                physicalTenantId,
                "the id was removed and stays reserved until its data is purged",
                null);
          }
          final var candidate = resolve(physicalTenantId, configuration);
          final var tenant = new PhysicalTenant(physicalTenantId, candidate.name(), incarnationId);
          registry.add(tenant);
          commit(physicalTenantId, candidate);
          startModule(tenant, candidate.context());
          return tenant;
        });
  }

  /**
   * Replaces the configuration of a tenant and restarts only its module. The tenant keeps its
   * incarnation.
   *
   * @throws PhysicalTenantChangeException if the change is rejected, or the module did not start
   */
  public PhysicalTenant replace(
      final String physicalTenantId, final Map<String, Object> configuration) {
    return replace(physicalTenantId, configuration, MemberId.ID_COMPARATOR);
  }

  /**
   * Replaces the configuration of a tenant as {@link #replace(String, Map)} does, handing the
   * leadership of its partitions to the replicas in the given order of preference first.
   */
  public PhysicalTenant replace(
      final String physicalTenantId,
      final Map<String, Object> configuration,
      final Comparator<MemberId> handoverPreference) {
    final var replacement = restart(physicalTenantId, configuration, handoverPreference);
    completeRestart(physicalTenantId, replacement);
    return replacement.tenant();
  }

  /**
   * The first part of {@link #replace(String, Map, Comparator)}: hands over the leadership, stops
   * the module and starts it with the new configuration. The caller completes the change with
   * {@link #completeRestart}, which may take until {@code rebalance.leader-wait-timeout} and does
   * not need the module to stay as it is, so it can run while other tenants' modules change.
   *
   * @throws PhysicalTenantChangeException if the change is rejected, or the module did not start
   */
  public Replacement restart(
      final String physicalTenantId,
      final Map<String, Object> configuration,
      final Comparator<MemberId> handoverPreference) {
    return underChangeLock(
        () -> {
          final var current = requireChangeable(physicalTenantId);
          final var candidate = resolve(physicalTenantId, configuration);
          registry.reportHealth(
              physicalTenantId,
              new PhysicalTenantHealth(State.RESTARTING, "applying a configuration change"));
          final var startedNanos = System.nanoTime();
          final var startedAt = Instant.now();
          // a replica that stops leading reloads its state from its latest snapshot, so the
          // snapshot is taken while it still leads; taken after the handover, it would hold only
          // what the replica replayed since
          final var snapshots = snapshotBeforeRestart(physicalTenantId);
          final var handover = handOverLeadership(physicalTenantId, handoverPreference);
          // the gateway keeps routing the tenant's requests to its leaders elsewhere while the
          // module restarts; it takes the new configuration once the module started again
          try {
            stopModule(physicalTenantId);
          } catch (final RuntimeException e) {
            close(physicalTenantId, candidate.context());
            registry.reportHealth(
                physicalTenantId,
                new PhysicalTenantHealth(State.FAILED, "module failed to stop: " + e));
            throw rejected(Reason.FAILED, physicalTenantId, "its module failed to stop", e);
          }
          final var stoppedNanos = System.nanoTime();
          final var replaced = current.withName(candidate.name());
          registry.replace(replaced);
          commit(physicalTenantId, candidate);
          final TenantRestart restart;
          try {
            startModule(replaced, candidate.context());
          } finally {
            restart =
                new TenantRestart(
                    startedAt,
                    startedNanos,
                    handover,
                    snapshots,
                    stoppedNanos,
                    System.nanoTime(),
                    List.of(),
                    0);
            lastRestarts.put(physicalTenantId, restart);
          }
          return new Replacement(replaced, restart);
        });
  }

  /**
   * The second part of {@link #replace(String, Map, Comparator)}: takes back the leadership of the
   * partitions handed over before the restart, once their replicas caught up, and returns after
   * that.
   */
  public void completeRestart(final String physicalTenantId, final Replacement replacement) {
    afterRestart(physicalTenantId, replacement.restart());
  }

  /**
   * Stops the module of a tenant and removes it from this node. Its data stays on disk and its id
   * stays reserved.
   *
   * @throws PhysicalTenantChangeException if the change is rejected
   */
  public void remove(final String physicalTenantId) {
    underChangeLock(
        () -> {
          requireChangeable(physicalTenantId);
          gateway().ifPresent(gateway -> gateway.remove(physicalTenantId));
          stopModule(physicalTenantId);
          releaseResources(physicalTenantId);
          registry.remove(physicalTenantId);
          configurations.remove(physicalTenantId);
          lastRestarts.remove(physicalTenantId);
          environment =
              PhysicalTenantEnvironments.withTenants(
                  environment, Map.of(), Set.of(physicalTenantId));
          return physicalTenantId;
        });
  }

  /**
   * Runs the boot validations on a configuration of a tenant without changing anything.
   *
   * @return the broker configuration the tenant would run with
   * @throws PhysicalTenantChangeException with {@link Reason#INVALID} if the validations fail
   */
  public BrokerCfg validate(
      final String physicalTenantId, final Map<String, Object> configuration) {
    final var candidate = resolve(physicalTenantId, configuration);
    close(physicalTenantId, candidate.context());
    return candidate.context().config();
  }

  /**
   * Resolves the configuration a tenant would run with, the root merged with the given subtree,
   * without the boot validations of {@link #validate}.
   *
   * @throws PhysicalTenantChangeException with {@link Reason#INVALID} if it does not resolve
   */
  public Camunda effectiveConfiguration(
      final String physicalTenantId, final Map<String, Object> configuration) {
    try {
      return PhysicalTenantResolver.of(
              PhysicalTenantEnvironments.withTenants(
                  environment, Map.of(physicalTenantId, configuration), Set.of()),
              rootCamunda)
          .forPhysicalTenant(physicalTenantId);
    } catch (final RuntimeException e) {
      throw rejected(Reason.INVALID, physicalTenantId, e.getMessage(), e);
    }
  }

  /**
   * @return completes once every local partition of the tenant is installed after its last start
   */
  public CompletableFuture<Void> awaitReady(final String physicalTenantId) {
    return modules().awaitReady(physicalTenantId);
  }

  /** The health of a tenant on this node and its last restart. */
  public Optional<LocalStatus> localStatus(final String physicalTenantId) {
    return registry
        .health(physicalTenantId)
        .map(
            health ->
                new LocalStatus(
                    health.state(),
                    health.detail(),
                    lastRestart(physicalTenantId).map(TenantRestart::describe).orElse(null)));
  }

  /** The last restart of a tenant's module on this node for a configuration change. */
  public Optional<TenantRestart> lastRestart(final String physicalTenantId) {
    return Optional.ofNullable(lastRestarts.get(physicalTenantId));
  }

  /** The resolved configuration a tenant currently runs with. */
  public Optional<Camunda> configuration(final String physicalTenantId) {
    return Optional.ofNullable(configurations.get(physicalTenantId));
  }

  private Candidate resolve(
      final String physicalTenantId, final Map<String, Object> configuration) {
    final var candidateEnvironment =
        PhysicalTenantEnvironments.withTenants(
            environment, Map.of(physicalTenantId, configuration), Set.of());
    final PhysicalTenantResolver resolver;
    final Camunda camunda;
    try {
      resolver = PhysicalTenantResolver.of(candidateEnvironment, rootCamunda);
      camunda = resolver.forPhysicalTenant(physicalTenantId);
      camunda.getSecrets().getRefreshInterval();
    } catch (final RuntimeException e) {
      throw rejected(Reason.INVALID, physicalTenantId, e.getMessage(), e);
    }

    final PhysicalTenantContext context;
    try {
      secretStores.validate(physicalTenantId, camunda.getSecrets());
      // the stores are built when the module starts, see withOwnJobStores
      context =
          systemContextLoader.buildPhysicalTenantContext(physicalTenantId, camunda, NOT_BUILT_YET);
    } catch (final RuntimeException e) {
      throw rejected(Reason.INVALID, physicalTenantId, e.getMessage(), e);
    }
    try {
      broker
          .getSystemContext()
          .getPhysicalTenantConfigurationValidator()
          .validate(physicalTenantId, context);
    } catch (final RuntimeException e) {
      close(physicalTenantId, context);
      throw rejected(Reason.INVALID, physicalTenantId, e.getMessage(), e);
    }
    return new Candidate(
        candidateEnvironment, camunda, context, resolver.nameOf(physicalTenantId).orElse(null));
  }

  private void commit(final String physicalTenantId, final Candidate candidate) {
    environment = candidate.environment();
    configurations.put(physicalTenantId, candidate.configuration());
    contexts.put(physicalTenantId, candidate.context());
  }

  private void startModule(final PhysicalTenant tenant, final PhysicalTenantContext candidate) {
    final var modules = modules();
    var context = candidate;
    try {
      final var configuration = Objects.requireNonNull(configurations.get(tenant.id()));
      context = withOwnJobStores(tenant.id(), configuration, candidate);
      resources.forEach(resource -> resource.apply(tenant.id(), configuration));
      await(modules.start(tenant.id(), context));
    } catch (final RuntimeException e) {
      registry.reportHealth(
          tenant.id(), new PhysicalTenantHealth(State.FAILED, "module failed to start: " + e));
      gateway()
          .ifPresent(
              gateway -> {
                gateway.put(tenant.id(), candidate.securityConfig());
                gateway.markUnavailable(tenant.id(), "its module failed to start");
              });
      throw rejected(Reason.FAILED, tenant.id(), "its module failed to start", e);
    }
    gateway().ifPresent(gateway -> gateway.put(tenant.id(), candidate.securityConfig()));
    markHealthyWhenReady(tenant, modules);
  }

  /**
   * Builds the secret stores that the tenant's jobs resolve from, for this start alone. They are
   * built only once the previous module of the tenant stopped and released its own: the stores of
   * both would publish the same meters, and releasing the previous ones would remove them.
   */
  private PhysicalTenantContext withOwnJobStores(
      final String physicalTenantId,
      final Camunda configuration,
      final PhysicalTenantContext candidate) {
    final var jobStores = secretStores.jobStores(physicalTenantId, configuration.getSecrets());
    final var context =
        new PhysicalTenantContext(
            candidate.securityConfig(),
            candidate.authorizationConverter(),
            candidate.featureFlags(),
            candidate.config(),
            candidate.exporterRepository(),
            jobStores.value());
    ownedSecretStores.put(context, jobStores);
    contexts.put(physicalTenantId, context);
    return context;
  }

  private void markHealthyWhenReady(
      final PhysicalTenant tenant, final PhysicalTenantModules modules) {
    final UUID incarnation = tenant.incarnationId();
    modules
        .awaitReady(tenant.id())
        .whenComplete(
            (ready, error) -> {
              if (error != null
                  || registry
                      .find(tenant.id())
                      .filter(current -> current.incarnationId().equals(incarnation))
                      .isEmpty()) {
                return;
              }
              final var health = registry.health(tenant.id()).map(PhysicalTenantHealth::state);
              if (health
                  .filter(state -> state == State.STARTING || state == State.RESTARTING)
                  .isPresent()) {
                registry.reportHealth(tenant.id(), PhysicalTenantHealth.of(State.HEALTHY));
              }
            });
  }

  /**
   * Hands the leadership of the tenant's partitions that this member leads to other replicas, so
   * they keep a leader while the module restarts. Never fails the change: a partition that is not
   * handed over is restarted as it is.
   */
  private Report handOverLeadership(
      final String physicalTenantId, final Comparator<MemberId> preference) {
    final var brokerConfiguration = broker.getSystemContext().getBrokerConfiguration();
    final var raft = brokerConfiguration.getCluster().getRaft();
    final var context = broker.getBrokerContext();
    final var partitionManager =
        context == null ? null : context.getPartitionManagers().get(physicalTenantId);
    if (partitionManager == null) {
      return Report.skipped();
    }
    if (raft.getTenantRestartHandover() == TenantRestartHandover.NONE) {
      return PartitionLeadershipHandover.withoutHandover(partitionManager.getRaftPartitions());
    }
    final var bound = raft.getRebalanceReplicationTimeout().plus(HANDOVER_SLACK);
    final var handover =
        new PartitionLeadershipHandover(
            MemberId.from(String.valueOf(brokerConfiguration.getCluster().getNodeId())), bound);
    try {
      final var report =
          handover
              .handOverRaftPartitions(partitionManager.getRaftPartitions(), preference)
              .get(bound.plus(HANDOVER_SLACK).toMillis(), TimeUnit.MILLISECONDS);
      final var fallbacks = report.fallbacks();
      if (!fallbacks.isEmpty()) {
        LOG.info(
            "Restarting physical tenant '{}' without handing over the leadership of {}",
            physicalTenantId,
            fallbacks);
      }
      return report;
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      return Report.skipped();
    } catch (final ExecutionException | TimeoutException e) {
      LOG.warn(
          "Failed to hand over the leadership of physical tenant '{}' before its restart",
          physicalTenantId,
          e);
      return Report.skipped();
    }
  }

  /**
   * Takes a snapshot of every partition of the tenant on this member, within the configured bound.
   * Never fails the change: a partition without a new snapshot restarts from the one it has.
   */
  private List<PartitionSnapshot> snapshotBeforeRestart(final String physicalTenantId) {
    final var raft = broker.getSystemContext().getBrokerConfiguration().getCluster().getRaft();
    final var partitionManager = partitionManager(physicalTenantId);
    if (!raft.isTenantRestartSnapshot() || partitionManager == null) {
      return List.of();
    }
    final var bound = raft.getTenantRestartSnapshotTimeout();
    final var partitions =
        partitionManager.getZeebePartitions().stream()
            .<SnapshotPartition>map(
                partition ->
                    new SnapshotPartition() {
                      @Override
                      public int partition() {
                        return partition.getPartitionId();
                      }

                      @Override
                      public CompletableFuture<@Nullable TakenSnapshot> forceSnapshot() {
                        return partition
                            .forceSnapshot()
                            .toCompletableFuture()
                            .thenApply(
                                snapshot ->
                                    snapshot == null
                                        ? null
                                        : new TakenSnapshot(
                                            snapshot.getMetadata().processedPosition(),
                                            snapshot.getTotalSizeInBytes()));
                      }
                    })
            .toList();
    try {
      return new PartitionRestartSnapshots(bound)
          .take(partitions)
          .get(bound.plus(HANDOVER_SLACK).toMillis(), TimeUnit.MILLISECONDS);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      return List.of();
    } catch (final ExecutionException | TimeoutException e) {
      LOG.warn(
          "Failed to take snapshots of physical tenant '{}' before its restart",
          physicalTenantId,
          e);
      return List.of();
    }
  }

  /**
   * Takes back the leadership of the partitions handed over before the restart, once their replicas
   * caught up, and waits until every other restarted replica caught up too, so that the next member
   * of a rollout hands its leadership only to replicas that do not have to replay first.
   */
  private void afterRestart(final String physicalTenantId, final TenantRestart restart) {
    final var raft = broker.getSystemContext().getBrokerConfiguration().getCluster().getRaft();
    final var reclaim =
        new PartitionLeadershipReclaim(
            raft.getRebalanceLeaderWaitTimeout(),
            CAUGHT_UP_LAG,
            raft.getRebalanceReplicationTimeout().plus(HANDOVER_SLACK));
    final var partitions =
        restart.handover().partitions().stream()
            .map(partition -> reclaimPartition(physicalTenantId, partition.partition().number()))
            .toList();
    final var takeBack =
        raft.getTenantRestartHandover() == TenantRestartHandover.TRANSFER_BACK
            ? restart.handover().partitions().stream()
                .filter(
                    partition ->
                        partition.outcome() == PartitionLeadershipHandover.Outcome.TRANSFERRED)
                .map(
                    partition -> reclaimPartition(physicalTenantId, partition.partition().number()))
                .toList()
            : List.<ReclaimPartition>of();
    final var others =
        partitions.stream()
            .filter(
                partition ->
                    takeBack.stream()
                        .noneMatch(taken -> taken.partition() == partition.partition()))
            .toList();
    final var caughtUp = reclaim.awaitCaughtUp(others);
    List<PartitionReclaim> reclaimed = List.of();
    if (!takeBack.isEmpty()) {
      try {
        reclaimed =
            reclaim.reclaim(takeBack).get(reclaimBound(raft).toMillis(), TimeUnit.MILLISECONDS);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (final ExecutionException | TimeoutException e) {
        LOG.warn(
            "Failed to take back the leadership of physical tenant '{}' after its restart",
            physicalTenantId,
            e);
      }
    }
    List<PartitionReclaim> watched = List.of();
    try {
      watched = caughtUp.get(reclaimBound(raft).toMillis(), TimeUnit.MILLISECONDS);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (final ExecutionException | TimeoutException e) {
      LOG.warn(
          "Physical tenant '{}' moves on before every restarted replica caught up",
          physicalTenantId,
          e);
    }
    final var completed = restart.after(reclaimed, System.nanoTime()).after(watched);
    lastRestarts.computeIfPresent(
        physicalTenantId, (id, current) -> current == restart ? completed : current);
  }

  private static Duration reclaimBound(final RaftCfg raft) {
    return raft.getRebalanceLeaderWaitTimeout().plus(HANDOVER_SLACK);
  }

  private ReclaimPartition reclaimPartition(final String physicalTenantId, final int partitionId) {
    return new ReclaimPartition() {
      @Override
      public int partition() {
        return partitionId;
      }

      @Override
      public boolean leads() {
        final var raftPartition = raftPartition();
        return raftPartition != null && raftPartition.getRole() == Role.LEADER;
      }

      @Override
      public CompletableFuture<ReplayLag> replayLag() {
        final var partitionManager = partitionManager(physicalTenantId);
        final var partition =
            partitionManager == null
                ? null
                : partitionManager.getZeebePartitions().stream()
                    .filter(candidate -> candidate.getPartitionId() == partitionId)
                    .findFirst()
                    .orElse(null);
        if (partition == null) {
          return CompletableFuture.failedFuture(
              new IllegalStateException("Partition %d is not installed".formatted(partitionId)));
        }
        return partition
            .replayProgress()
            .toCompletableFuture()
            .thenApply(
                progress ->
                    new ReplayLag(
                        progress.lag(),
                        progress.logEndPosition() - progress.snapshotPosition(),
                        !partition.isDiskSpaceAvailable()));
      }

      @Override
      public CompletableFuture<LeadershipTransferResult> requestLeadership(final Duration bound) {
        final var raftPartition = raftPartition();
        return raftPartition == null
            ? CompletableFuture.completedFuture(LeadershipTransferResult.LEADER_CHANGED)
            : raftPartition.requestLeadership(bound);
      }

      private @Nullable RaftPartition raftPartition() {
        final var partitionManager = partitionManager(physicalTenantId);
        return partitionManager == null ? null : partitionManager.getRaftPartition(partitionId);
      }
    };
  }

  private @Nullable PartitionManager partitionManager(final String physicalTenantId) {
    final var context = broker.getBrokerContext();
    return context == null ? null : context.getPartitionManagers().get(physicalTenantId);
  }

  private void stopModule(final String physicalTenantId) {
    final var modules = modules();
    if (modules.running().contains(physicalTenantId)) {
      await(modules.stop(physicalTenantId));
    }
    final var context = contexts.remove(physicalTenantId);
    if (context != null) {
      close(physicalTenantId, context);
    }
  }

  private void releaseResources(final String physicalTenantId) {
    resources.forEach(
        resource -> {
          try {
            resource.release(physicalTenantId);
          } catch (final RuntimeException e) {
            LOG.warn(
                "Failed to release a resource of removed physical tenant '{}'",
                physicalTenantId,
                e);
          }
        });
  }

  private PhysicalTenant requireChangeable(final String physicalTenantId) {
    if (PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID.equals(physicalTenantId)) {
      throw rejected(
          Reason.CONFLICT,
          physicalTenantId,
          "the default tenant is the root configuration and changes only with a node restart",
          null);
    }
    return registry
        .find(physicalTenantId)
        .orElseThrow(() -> rejected(Reason.CONFLICT, physicalTenantId, "it does not exist", null));
  }

  private PhysicalTenantModules modules() {
    final var context = broker.getBrokerContext();
    if (context == null) {
      throw new IllegalStateException("The broker has not started yet");
    }
    return context.getPhysicalTenantModules();
  }

  private Optional<GatewayPhysicalTenants> gateway() {
    return Optional.ofNullable(broker.getBrokerContext())
        .map(context -> context.getEmbeddedGatewayService())
        .map(gatewayService -> gatewayService.get().physicalTenants());
  }

  private void await(final CompletableFuture<Void> future) {
    try {
      future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } catch (final ExecutionException e) {
      throw e.getCause() instanceof final RuntimeException runtime
          ? runtime
          : new IllegalStateException(e.getCause());
    } catch (final TimeoutException e) {
      throw new IllegalStateException("Timed out after " + timeout, e);
    }
  }

  private void close(final String physicalTenantId, final PhysicalTenantContext context) {
    try {
      context.exporterRepository().close();
    } catch (final Exception e) {
      LOG.warn("Failed to close the exporters of physical tenant '{}'", physicalTenantId, e);
    }
    final var jobStores = ownedSecretStores.remove(context);
    if (jobStores != null) {
      try {
        jobStores.close();
      } catch (final RuntimeException e) {
        LOG.warn("Failed to close the secret stores of physical tenant '{}'", physicalTenantId, e);
      }
    }
  }

  private <T> T underChangeLock(final Change<T> change) {
    if (!changeLock.tryLock()) {
      throw new PhysicalTenantChangeException(
          Reason.IN_PROGRESS, "Another physical tenant change is in progress", null);
    }
    try {
      return change.apply();
    } finally {
      changeLock.unlock();
    }
  }

  private static PhysicalTenantChangeException rejected(
      final Reason reason,
      final String physicalTenantId,
      final @Nullable String why,
      final @Nullable Throwable cause) {
    return new PhysicalTenantChangeException(
        reason, "Cannot change physical tenant '%s': %s".formatted(physicalTenantId, why), cause);
  }

  @FunctionalInterface
  private interface Change<T> {
    T apply();
  }

  /**
   * The health of a tenant on this node, and its last restart.
   *
   * @param lastRestart empty until the tenant restarted on this node for a configuration change
   */
  /**
   * A tenant whose module was restarted with a new configuration, and how the restart went so far.
   */
  public record Replacement(PhysicalTenant tenant, TenantRestart restart) {}

  public record LocalStatus(
      State health, @Nullable String healthDetail, @Nullable String lastRestart) {}

  /**
   * A restart of a tenant's module for a configuration change.
   *
   * @param startedNanos {@link System#nanoTime()} when the restart began, after the change passed
   *     its checks
   * @param handover how the leadership of the tenant's partitions was handed over; no partitions if
   *     the handover is turned off
   * @param snapshots the snapshots taken before the module stopped; none if turned off
   * @param stoppedNanos {@link System#nanoTime()} once the module stopped
   * @param startedAgainNanos {@link System#nanoTime()} once the module started again, before its
   *     partitions were installed
   * @param afterRestart per partition, how its replica caught up after the restart and whether its
   *     leadership was taken back; filled in once known
   * @param completedNanos {@link System#nanoTime()} once the change completed, after the leadership
   *     was taken back; 0 until then
   */
  public record TenantRestart(
      Instant startedAt,
      long startedNanos,
      Report handover,
      List<PartitionSnapshot> snapshots,
      long stoppedNanos,
      long startedAgainNanos,
      List<PartitionReclaim> afterRestart,
      long completedNanos) {

    TenantRestart after(final List<PartitionReclaim> reclaimed, final long completedNanos) {
      return new TenantRestart(
          startedAt,
          startedNanos,
          handover,
          snapshots,
          stoppedNanos,
          startedAgainNanos,
          reclaimed,
          completedNanos);
    }

    TenantRestart after(final List<PartitionReclaim> caughtUp) {
      final var all = new ArrayList<>(afterRestart);
      all.addAll(caughtUp);
      return after(List.copyOf(all), completedNanos);
    }

    /** Whether this member led any partition of the tenant when the restart began. */
    public boolean led() {
      return handover.partitions().stream()
          .anyMatch(PartitionLeadershipHandover.PartitionReport::led);
    }

    String describe() {
      final var partitions =
          handover.partitions().stream()
              .filter(PartitionLeadershipHandover.PartitionReport::led)
              .map(
                  partition ->
                      partition.partition().number()
                          + ": "
                          + partition.outcome()
                          + (partition.newLeader() == null ? "" : " to " + partition.newLeader())
                          + " in "
                          + partition.took().toMillis()
                          + " ms")
              .toList();
      final var taken =
          snapshots.stream()
              .map(
                  snapshot ->
                      snapshot.partition()
                          + ": "
                          + snapshot.outcome()
                          + " in "
                          + snapshot.took().toMillis()
                          + " ms")
              .toList();
      final var after =
          afterRestart.stream()
              .map(
                  partition ->
                      partition.partition()
                          + ": "
                          + partition.outcome()
                          + " after "
                          + partition.took().toMillis()
                          + " ms")
              .toList();
      return "at %s, handover %s, snapshots %s, module down %d ms, after the restart %s"
          .formatted(
              startedAt,
              partitions.isEmpty() ? "of no partition" : String.join(", ", partitions),
              taken.isEmpty() ? "none" : String.join(", ", taken),
              Duration.ofNanos(startedAgainNanos - stoppedNanos).toMillis(),
              after.isEmpty() ? "nothing yet" : String.join(", ", after));
    }
  }

  private record Candidate(
      ConfigurableEnvironment environment,
      Camunda configuration,
      PhysicalTenantContext context,
      @Nullable String name) {}
}
