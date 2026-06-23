/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.ModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.MemberState.State;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.util.Either;
import java.util.List;

public final class ModeChangeRequestTransformer implements ConfigurationChangeRequest {

  private final Mode mode;

  public ModeChangeRequestTransformer(final Mode mode) {
    this.mode = mode;
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration clusterConfiguration) {
    final State sourceState = mode == Mode.RECOVERING ? State.ACTIVE : State.RECOVERING;
    final Mode targetMode = mode == Mode.RECOVERING ? Mode.RECOVERING : Mode.PROCESSING;

    final var operations =
        clusterConfiguration.members().entrySet().stream()
            .filter(e -> e.getValue().state() == sourceState)
            .map(
                e ->
                    (ClusterConfigurationChangeOperation)
                        new ModeChangeOperation(e.getKey(), targetMode))
            .toList();

    if (operations.isEmpty()) {
      return Either.left(
          new InvalidRequest(
              mode == Mode.RECOVERING
                  ? "No active members found to enter recovery mode"
                  : "No recovering members found to transition to processing mode"));
    }
    return Either.right(operations);
  }
}
