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
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.Set;

/**
 * After the configuration is initialized, we can use a {@link ClusterConfigurationModifier} to
 * update the initialized configuration. This process do not go through the usual process of adding
 * a {@link io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation} to change the
 * configuration. Instead, overwrites the configuration immediately after it is initialized. Hence,
 * this should be used carefully.
 *
 * <p>Ideally, a modifier should only update the configuration of the local member to avoid any
 * concurrent conflicting changes from other members.
 */
public interface ClusterConfigurationModifier {

  /**
   * Modifies the given configuration and returns the modified configuration.
   *
   * @param configuration current configuration
   * @return modified configuration
   */
  ActorFuture<ClusterConfiguration> modify(ClusterConfiguration configuration);

  class ExporterStateInitializer implements ClusterConfigurationModifier {

    private final Set<String> configuredExporters;
    private final MemberId localMemberId;
    private final ConcurrencyControl executor;

    public ExporterStateInitializer(
        final Set<String> configuredExporters,
        final MemberId localMemberId,
        final ConcurrencyControl executor) {
      this.configuredExporters = configuredExporters;
      this.localMemberId = localMemberId;
      this.executor = executor;
    }

    @Override
    public ActorFuture<ClusterConfiguration> modify(final ClusterConfiguration configuration) {
      final ActorFuture<ClusterConfiguration> result = executor.createFuture();
      if (!configuration.hasMember(localMemberId)) {
        result.complete(configuration);
      } else {
        result.complete(
            configuration.updateMember(
                localMemberId, memberState -> updateExporterState(memberState)));
      }
      return result;
    }

    private MemberState updateExporterState(final MemberState memberState) {
      MemberState updatedMemberState = memberState;
      for (final var p : memberState.partitions().keySet()) {
        updatedMemberState =
            updatedMemberState.updatePartition(
                p, partitionState -> updateExporterStateInPartition(partitionState));
      }
      return updatedMemberState;
    }

    private PartitionState updateExporterStateInPartition(final PartitionState partitionState) {
      final var exportersInConfig = partitionState.config().exporting().exporters();

      final var newlyAddedExporters =
          configuredExporters.stream().filter(id -> !exportersInConfig.containsKey(id)).toList();
      final var removedExporters =
          exportersInConfig.keySet().stream()
              .filter(id -> !configuredExporters.contains(id))
              .toList();

      return partitionState
          .updateConfig(c -> c.updateExporting(e -> e.disableExporters(removedExporters)))
          .updateConfig(c -> c.updateExporting(e -> e.addExporters(newlyAddedExporters)));
    }
  }
}
