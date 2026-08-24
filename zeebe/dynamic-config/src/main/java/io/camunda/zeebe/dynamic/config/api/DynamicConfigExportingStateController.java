/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExportingStateChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.NotFound;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;

/**
 * Applies exporting state changes through dynamic cluster configuration, scoped to the physical
 * tenant a {@link ByTenant} was resolved for, so that a pause survives restarts and applies to
 * partitions that join later.
 *
 * <p>The poll interval and timeout used while awaiting the change are configurable so tests do not
 * have to wait on production defaults.
 */
@NullMarked
public final class DynamicConfigExportingStateController implements ExportingStateController {

  private static final Duration POLL_INTERVAL = Duration.ofMillis(200);
  private static final Duration TIMEOUT = Duration.ofSeconds(60);

  private final ClusterConfigurationManagementRequestSender requestSender;
  private final ClusterConfigurationChangeAwaiter changeAwaiter;

  public DynamicConfigExportingStateController(
      final ClusterConfigurationManagementRequestSender requestSender) {
    this(requestSender, POLL_INTERVAL, TIMEOUT);
  }

  public DynamicConfigExportingStateController(
      final ClusterConfigurationManagementRequestSender requestSender,
      final Duration pollInterval,
      final Duration timeout) {
    this.requestSender = requestSender;
    changeAwaiter = new ClusterConfigurationChangeAwaiter(requestSender, pollInterval, timeout);
  }

  @Override
  public ExportingStateController.ByTenant getByTenant(final String physicalTenantId) {
    return new TenantController(physicalTenantId);
  }

  @Override
  public ExportingStateController.ClusterWide clusterWide() {
    return new ClusterWideController();
  }

  private CompletableFuture<Void> changeState(
      final Optional<String> physicalTenantId, final ExportingState targetState) {
    return changeAwaiter.awaitCompletion(
        requestSender.changeExportingState(
            new ExportingStateChangeRequest(targetState, physicalTenantId, false)));
  }

  private CompletableFuture<ExportingStatus> queryStatus(final String physicalTenantId) {
    return requestSender
        .getTopology()
        .thenApply(
            topology -> {
              if (topology.isLeft()) {
                throw topology.getLeft().toException();
              }
              return aggregateStatus(physicalTenantId, topology.get());
            });
  }

  private CompletableFuture<ExportingStatus> queryClusterWideStatus() {
    return requestSender
        .getTopology()
        .thenApply(
            topology -> {
              if (topology.isLeft()) {
                throw topology.getLeft().toException();
              }
              return aggregateClusterWideStatus(topology.get());
            });
  }

  /**
   * Exporting state is stored per partition replica, so replicas can disagree mid-rollout; {@link
   * ExportingStatus#aggregate(java.util.Collection)} folds them into a single status, or {@code
   * MIXED} when they don't agree. A physical tenant that does not exist, or is disabled, has no
   * live status to report — {@link #changeState} already rejects both the same way (the coordinator
   * plans against {@link CurrentClusterConfiguration#activePartitionGroups()}), and folding either
   * into {@code MIXED} would look indistinguishable from a real, converged tenant that merely
   * disagrees, so both are rejected here as {@link NotFound} instead.
   */
  private static ExportingStatus aggregateStatus(
      final String physicalTenantId, final CurrentClusterConfiguration configuration) {
    final PartitionGroupConfiguration group = configuration.partitionGroup(physicalTenantId);
    if (group == null || group.isDisabled()) {
      throw new NotFound(
          "Expected to query the exporting status of physical tenant '%s', but there's no such tenant"
              .formatted(physicalTenantId));
    }
    return ExportingStatus.aggregate(
        group.members().values().stream()
            .flatMap(member -> member.partitions().values().stream())
            .map(DynamicConfigExportingStateController::exportingStateOf)
            .toList());
  }

  /**
   * Aggregates the exporting status of every active (non-disabled) physical tenant's partition
   * replicas into one status. Unlike {@link #aggregateStatus}, there is no single tenant to reject
   * as {@link NotFound}; an empty active set (not reachable on a running cluster — there is always
   * at least the default tenant) reports {@code MIXED} rather than a vacuously "converged" status.
   */
  private static ExportingStatus aggregateClusterWideStatus(
      final CurrentClusterConfiguration configuration) {
    final var activeGroups = configuration.activePartitionGroups();
    if (activeGroups.isEmpty()) {
      return ExportingStatus.MIXED;
    }
    return ExportingStatus.aggregate(
        activeGroups.values().stream()
            .flatMap(group -> group.members().values().stream())
            .flatMap(member -> member.partitions().values().stream())
            .map(DynamicConfigExportingStateController::exportingStateOf)
            .toList());
  }

  /**
   * A partition's {@code exporting} config is {@code null} until something initializes it (e.g.
   * {@code ExporterStateInitializer} on the local member after restart, or the receiving side of a
   * gossip update from a peer that has not run that initializer yet, or a peer still on a wire
   * format that predates this field). {@link ExportingStateChangeRequestTransformer} already treats
   * that as equivalent to {@link ExportingState#UNKNOWN} rather than dereferencing it; this mirrors
   * that instead of risking a {@link NullPointerException}.
   */
  private static ExportingState exportingStateOf(final PartitionState partition) {
    final var config = partition.config();
    return config.isInitialized() ? config.exporting().state() : ExportingState.UNKNOWN;
  }

  private final class TenantController implements ExportingStateController.ByTenant {

    private final String physicalTenantId;

    private TenantController(final String physicalTenantId) {
      this.physicalTenantId = physicalTenantId;
    }

    @Override
    public CompletableFuture<Void> pauseExporting() {
      return changeState(Optional.of(physicalTenantId), ExportingState.PAUSED);
    }

    @Override
    public CompletableFuture<Void> softPauseExporting() {
      return changeState(Optional.of(physicalTenantId), ExportingState.SOFT_PAUSED);
    }

    @Override
    public CompletableFuture<Void> resumeExporting() {
      return changeState(Optional.of(physicalTenantId), ExportingState.EXPORTING);
    }

    @Override
    public CompletableFuture<ExportingStatus> getExportingStatus() {
      return queryStatus(physicalTenantId);
    }
  }

  private final class ClusterWideController implements ExportingStateController.ClusterWide {

    @Override
    public CompletableFuture<Void> pauseExporting() {
      return changeState(Optional.empty(), ExportingState.PAUSED);
    }

    @Override
    public CompletableFuture<Void> softPauseExporting() {
      return changeState(Optional.empty(), ExportingState.SOFT_PAUSED);
    }

    @Override
    public CompletableFuture<Void> resumeExporting() {
      return changeState(Optional.empty(), ExportingState.EXPORTING);
    }

    @Override
    public CompletableFuture<ExportingStatus> getExportingStatus() {
      return queryClusterWideStatus();
    }
  }
}
