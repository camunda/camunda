/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.ConcurrentModificationException;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.MemberState.State;
import io.camunda.zeebe.util.Either;
import java.util.List;

/**
 * Validates a {@link RestoreRequest} against the current cluster configuration and produces the
 * change plan for the restore.
 *
 * <p>Validation failures are returned as an {@link Either#left(Object)} carrying a {@link
 * ClusterConfigurationRequestFailedException}, which the coordinator surfaces as an error response.
 * When validation passes, the resulting {@link ClusterConfigurationChangeOperation} list is the
 * restore plan. The plan is currently empty: the restore is not yet applied through the cluster
 * configuration, so validation is the only effect.
 */
public final class RestoreRequestTransformer implements ConfigurationChangeRequest {

  private final RestoreRequest request;

  public RestoreRequestTransformer(final RestoreRequest request) {
    this.request = request;
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration clusterConfiguration) {

    // Restore is only allowed in recovery
    if (!isClusterRecovering(clusterConfiguration)) {
      return Either.left(
          new ConcurrentModificationException(
              "Restore is only allowed while the cluster is in recovery mode."));
    }
    // Restore sequence steps will be generated
    return Either.right(List.of());
  }

  private static boolean isClusterRecovering(final ClusterConfiguration clusterConfiguration) {
    final var initializedMembers =
        clusterConfiguration.members().values().stream()
            .filter(member -> member.state() != State.UNINITIALIZED && member.state() != State.LEFT)
            .toList();
    return !initializedMembers.isEmpty()
        && initializedMembers.stream().allMatch(member -> member.state() == State.RECOVERING);
  }

  /**
   * Backup compatibility and resolution will happen before the change plan is generated. This
   * operation will be synchronous for better UX.
   *
   * <p>Placeholder
   */
  private void resolveBackups() {}
}
