/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import io.camunda.zeebe.broker.bootstrap.BrokerStartupContext;
import io.camunda.zeebe.broker.partitioning.topology.ClusterConfigurationService;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.dynamic.config.changes.ModeChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import io.camunda.zeebe.protocol.record.PartitionRole;
import io.camunda.zeebe.scheduler.AsyncClosable;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives the transition of a single physical tenant's partition group between processing ({@link
 * PartitionManagerImpl}) and recovery ({@link RecoveryPartitionManager}) mode.
 *
 * <p>One handler is built per physical tenant by the {@link
 * io.camunda.zeebe.broker.bootstrap.PartitionManagerStep} that owns the tenant, reusing that
 * tenant's {@link TopologyManagerImpl}, and is closed when the step shuts the partition manager
 * down. Only the default tenant participates in cluster configuration changes, so only its handler
 * registers as the broker's {@link ModeChangeExecutor} and stays registered across mode changes. A
 * transition stops the active manager, recreates it in the target mode, starts it, and re-publishes
 * it on the {@link BrokerStartupContext}.
 *
 * <p>The handler keeps no partition manager reference of its own; the active manager and the
 * remaining dependencies are resolved from the {@link BrokerStartupContext} on demand.
 */
public final class PartitionModeHandler implements ModeChangeExecutor, AsyncClosable {

  private static final Logger LOG = LoggerFactory.getLogger(PartitionModeHandler.class);

  private final BrokerStartupContext brokerStartupContext;
  private final String partitionGroup;
  private final TopologyManagerImpl topologyManager;
  private final PartitionManagerFactory partitionManagerFactory;

  public PartitionModeHandler(
      final BrokerStartupContext brokerStartupContext,
      final String partitionGroup,
      final TopologyManagerImpl topologyManager) {
    this(
        brokerStartupContext,
        partitionGroup,
        topologyManager,
        defaultPartitionManagerFactory(brokerStartupContext, partitionGroup, topologyManager));
  }

  @VisibleForTesting
  PartitionModeHandler(
      final BrokerStartupContext brokerStartupContext,
      final String partitionGroup,
      final TopologyManagerImpl topologyManager,
      final PartitionManagerFactory partitionManagerFactory) {
    this.brokerStartupContext = brokerStartupContext;
    this.partitionGroup = partitionGroup;
    this.topologyManager = topologyManager;
    this.partitionManagerFactory = partitionManagerFactory;
  }

  private static PartitionManagerFactory defaultPartitionManagerFactory(
      final BrokerStartupContext brokerStartupContext,
      final String partitionGroup,
      final TopologyManagerImpl topologyManager) {
    return mode ->
        mode == Mode.RECOVERING
            ? PartitionManager.createRecoveryPartitionManager(
                brokerStartupContext, partitionGroup, topologyManager)
            : PartitionManager.createPartitionManager(
                brokerStartupContext, partitionGroup, topologyManager);
  }

  /**
   * Registers this handler as the broker's mode change executor. Only the default tenant
   * participates in cluster configuration changes, so only its handler registers. The partition
   * change executors are registered by the partition manager itself on {@link
   * PartitionManager#start()}.
   */
  public void register() {
    if (!isDefaultGroup()) {
      return;
    }
    clusterConfigurationService().registerModeChangeExecutor(this);
  }

  @Override
  public ActorFuture<Void> enterRecovery() {
    final var concurrencyControl = concurrencyControl();
    final var result = concurrencyControl.<Void>createFuture();
    concurrencyControl.run(
        () -> {
          if (isRecovering()) {
            result.complete(null);
            return;
          }
          transitionTo(Mode.RECOVERING, result);
        });
    return result;
  }

  @Override
  public ActorFuture<Void> exitRecovery() {
    final var concurrencyControl = concurrencyControl();
    final var result = concurrencyControl.<Void>createFuture();
    concurrencyControl.run(
        () -> {
          if (!isRecovering()) {
            result.complete(null);
            return;
          }
          transitionTo(Mode.PROCESSING, result);
        });
    return result;
  }

  /**
   * Checks whether this member's partitions have settled into the roles expected for {@code mode} -
   * {@code LEADER}/{@code FOLLOWER} for processing, {@code INACTIVE} for recovery - as reported by
   * the {@link TopologyManagerImpl}. The roles are driven by the partitions actually starting and
   * (for processing) joining their Raft groups, so this gates the cluster change on the transition
   * having genuinely taken effect rather than merely having been initiated.
   *
   * <p>It is safe to check here: all members have already flipped mode by the time the {@code
   * AwaitModeChange} operations run, so the quorum needed for leader election is available and this
   * cannot deadlock the change plan. If any partition has not yet reached its expected role the
   * operation fails immediately.
   */
  @Override
  public ActorFuture<Set<Integer>> awaitModeApplied(final Mode mode) {
    final var concurrencyControl = concurrencyControl();
    final var result = concurrencyControl.<Set<Integer>>createFuture();
    concurrencyControl.run(
        () -> {
          final var expectedRecovering = mode == Mode.RECOVERING;
          if (isRecovering() != expectedRecovering) {
            result.completeExceptionally(
                new IllegalStateException(
                    "Expected partition group %s to be in %s mode, but it was not"
                        .formatted(partitionGroup, mode)));
            return;
          }
          final var expectedPartitions = expectedLocalPartitionIds();
          if (expectedPartitions.isEmpty()) {
            result.complete(Set.of());
            return;
          }
          pollPartitionsReady(expectedPartitions, mode, result);
        });
    return result;
  }

  @Override
  public ActorFuture<Void> closeAsync() {
    final var concurrencyControl = concurrencyControl();
    final var result = concurrencyControl.<Void>createFuture();
    concurrencyControl.run(
        () -> {
          if (isDefaultGroup()) {
            clusterConfigurationService().removeModeChangeExecutor();
          }
          result.complete(null);
        });
    return result;
  }

  private void transitionTo(final Mode mode, final ActorFuture<Void> result) {
    final var current = currentManager();
    if (current == null) {
      result.completeExceptionally(
          new IllegalArgumentException(
              "No partition manager for partition group %s".formatted(partitionGroup)));
      return;
    }

    final var concurrencyControl = concurrencyControl();
    LOG.info("Transitioning partition group {} to {} mode", partitionGroup, mode);
    concurrencyControl.runOnCompletion(
        current.stop(),
        (ignored, stopError) -> {
          if (stopError != null) {
            result.completeExceptionally(stopError);
            return;
          }
          final var manager = partitionManagerFactory.create(mode);
          startManager(mode, manager, result);
        });
  }

  /**
   * Completes the mode transition once the new partition manager's start has been
   * <em>initiated</em> - it does not wait for the partitions to become ready.
   *
   * <p>Mode-change operations are applied one member at a time across the cluster: the next member
   * only transitions after this one's operation completes. Awaiting full readiness here deadlocks a
   * replicated cluster - a partition cannot elect a leader until a quorum of its members have
   * restarted their partitions, but those members are still waiting for this operation to complete.
   * The current member's partitions are already stopped (the previous manager's {@code stop()} was
   * awaited before this call), so it is safe to publish the new manager and complete the operation;
   * the partitions start, and any leader election, happen asynchronously once a quorum of members
   * has transitioned. Readiness is checked separately by {@link #awaitModeApplied(Mode)}, which
   * runs after every member has flipped. A failed start is logged and surfaced through health
   * monitoring rather than stalling the cluster change plan.
   */
  private void startManager(
      final Mode mode, final PartitionManager manager, final ActorFuture<Void> result) {
    brokerStartupContext.addPartitionManager(partitionGroup, manager);
    concurrencyControl()
        .runOnCompletion(
            manager.start(),
            (ignore, startError) -> {
              if (startError != null) {
                LOG.error(
                    "Failed to start partition group {} after transitioning to {} mode",
                    partitionGroup,
                    mode,
                    startError);
              } else {
                LOG.info("Partition group {} transitioned to {} mode", partitionGroup, mode);
              }
            });
    result.complete(null);
  }

  private PartitionManager currentManager() {
    return brokerStartupContext.getPartitionManagers().get(partitionGroup);
  }

  private boolean isRecovering() {
    return currentManager() instanceof RecoveryPartitionManager;
  }

  private boolean isDefaultGroup() {
    return PartitionManager.isDefaultPhysicalTenant(partitionGroup);
  }

  private ConcurrencyControl concurrencyControl() {
    return brokerStartupContext.getConcurrencyControl();
  }

  private ClusterConfigurationService clusterConfigurationService() {
    return brokerStartupContext.getClusterConfigurationService();
  }

  private void pollPartitionsReady(
      final Set<Integer> expectedPartitions,
      final Mode mode,
      final ActorFuture<Set<Integer>> result) {
    final var targetRoles = readyRolesFor(mode);
    concurrencyControl()
        .runOnCompletion(
            topologyManager.getLocalPartitionRoles(),
            (roles, rolesError) -> {
              if (rolesError != null) {
                result.completeExceptionally(rolesError);
                return;
              }
              final var pendingRolePartitions =
                  expectedPartitions.stream()
                      .filter(partitionId -> !targetRoles.contains(roles.get(partitionId)))
                      .toList();
              if (!pendingRolePartitions.isEmpty()) {
                result.completeExceptionally(
                    new IllegalStateException(
                        "Partition group %s: partitions %s have not yet reached roles %s (current roles: %s); the operation will be retried"
                            .formatted(partitionGroup, pendingRolePartitions, targetRoles, roles)));
                return;
              }
              if (mode != Mode.RECOVERING) {
                result.complete(expectedPartitions);
                return;
              }
              pollHealthAndComplete(expectedPartitions, result);
            });
  }

  private void pollHealthAndComplete(
      final Set<Integer> expectedPartitions, final ActorFuture<Set<Integer>> result) {
    concurrencyControl()
        .runOnCompletion(
            topologyManager.getLocalPartitionHealth(),
            (health, healthError) -> {
              if (healthError != null) {
                result.completeExceptionally(healthError);
                return;
              }
              final var pendingHealthPartitions =
                  expectedPartitions.stream()
                      .filter(partitionId -> !health.containsKey(partitionId))
                      .toList();
              if (!pendingHealthPartitions.isEmpty()) {
                result.completeExceptionally(
                    new IllegalStateException(
                        "Partition group %s: partitions %s have not yet reported health; the operation will be retried"
                            .formatted(partitionGroup, pendingHealthPartitions)));
                return;
              }
              final var healthyPartitions =
                  expectedPartitions.stream()
                      .filter(
                          partitionId -> health.get(partitionId) == PartitionHealthStatus.HEALTHY)
                      .collect(Collectors.toSet());
              if (healthyPartitions.size() < expectedPartitions.size()) {
                final var unhealthyPartitions = new HashSet<>(expectedPartitions);
                unhealthyPartitions.removeAll(healthyPartitions);
                LOG.warn(
                    "Partition group {}: partitions {} are not healthy during recovery transition - {}",
                    partitionGroup,
                    unhealthyPartitions,
                    health);
              }
              result.complete(healthyPartitions);
            });
  }

  private static Set<PartitionRole> readyRolesFor(final Mode mode) {
    return mode == Mode.RECOVERING
        ? EnumSet.of(PartitionRole.INACTIVE)
        : EnumSet.of(PartitionRole.LEADER, PartitionRole.FOLLOWER);
  }

  /** The ids of the partitions of this group that the local member replicates. */
  private Set<Integer> expectedLocalPartitionIds() {
    final var localMemberId =
        brokerStartupContext.getClusterServices().getMembershipService().getLocalMember().id();
    return clusterConfigurationService()
        .getPartitionDistribution()
        .withGroupName(partitionGroup)
        .partitions()
        .stream()
        .filter(partition -> partition.members().contains(localMemberId))
        .map(partition -> partition.id().number())
        .collect(Collectors.toSet());
  }

  /** Creates the partition manager for a target mode */
  @FunctionalInterface
  interface PartitionManagerFactory {
    PartitionManager create(Mode mode);
  }
}
