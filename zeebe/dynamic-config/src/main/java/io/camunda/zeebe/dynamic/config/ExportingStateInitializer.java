/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.Map;

/**
 * Migrates the overall exporting state of the local member's partitions from the legacy persisted
 * {@code .exporterPaused} file into the dynamic cluster configuration.
 *
 * <p>Before exporting was controlled through {@link
 * io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig}, the paused state was persisted in
 * a per-partition {@code .exporterPaused} file. Brokers upgraded from such a version start with
 * {@link ExportingState#UNKNOWN} in the dynamic configuration. This initializer seeds the state
 * read from the legacy file so that, once propagated and persisted, the file becomes redundant and
 * can be removed in a future release.
 *
 * <p>Only the local member's partitions are updated (the legacy file is local disk state), and only
 * when the current dynamic state is {@link ExportingState#UNKNOWN} — a state already set through
 * the dynamic configuration is authoritative and never overwritten.
 */
public class ExportingStateInitializer
    implements ClusterConfigurationModifier<ClusterConfiguration> {

  private final Map<Integer, ExportingState> legacyExportingStates;
  private final MemberId localMemberId;
  private final ConcurrencyControl executor;

  public ExportingStateInitializer(
      final Map<Integer, ExportingState> legacyExportingStates,
      final MemberId localMemberId,
      final ConcurrencyControl executor) {
    this.legacyExportingStates = legacyExportingStates;
    this.localMemberId = localMemberId;
    this.executor = executor;
  }

  @Override
  public ActorFuture<ClusterConfiguration> modify(final ClusterConfiguration configuration) {
    final ActorFuture<ClusterConfiguration> result = executor.createFuture();
    // Only the local member's legacy file is available here, so we cannot seed other members. In
    // the after-restore case the coordinator rewrites all members' state, so we skip to avoid
    // conflicting concurrent changes; the exporter director's startup fallback still honors the
    // legacy file when the dynamic state is UNKNOWN.
    if (!configuration.hasMember(localMemberId) || configuration.isAfterRestore()) {
      result.complete(configuration);
    } else {
      result.complete(configuration.updateMember(localMemberId, this::seedExportingState));
    }
    return result;
  }

  private MemberState seedExportingState(final MemberState memberState) {
    MemberState updatedMemberState = memberState;
    for (final var partitionId : memberState.partitions().keySet()) {
      final var partitionState = memberState.partitions().get(partitionId);
      if (!partitionState.config().isInitialized()
          || partitionState.config().exporting().state() != ExportingState.UNKNOWN) {
        continue;
      }
      final var legacyState = legacyExportingStates.get(partitionId);
      if (legacyState == null || legacyState == ExportingState.UNKNOWN) {
        continue;
      }
      updatedMemberState =
          updatedMemberState.updatePartition(
              partitionId,
              ps -> ps.updateConfig(c -> c.updateExporting(e -> e.withState(legacyState))));
    }
    return updatedMemberState;
  }
}
