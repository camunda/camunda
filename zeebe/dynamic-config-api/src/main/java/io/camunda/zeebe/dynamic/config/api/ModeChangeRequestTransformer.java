/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.camunda.zeebe.dynamic.config.api.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.MemberState.State;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.AwaitModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

public final class ModeChangeRequestTransformer implements ConfigurationChangeRequest {

  private final Mode mode;

  public ModeChangeRequestTransformer(final Mode mode) {
    this.mode = mode;
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration clusterConfiguration) {
    final State sourceState = mode == Mode.RECOVERING ? State.ACTIVE : State.RECOVERING;
    final var members =
        clusterConfiguration.members().entrySet().stream()
            .filter(e -> e.getValue().state() == sourceState)
            .map(Entry::getKey)
            .toList();

    // All members first start the change operation and complete it async
    // Following up with a verification step that the operation completed successfully
    final List<ClusterConfigurationChangeOperation> operations = new ArrayList<>();
    members.forEach(memberId -> operations.add(new ModeChangeOperation(memberId, mode)));
    members.forEach(memberId -> operations.add(new AwaitModeChangeOperation(memberId, mode)));

    return Either.right(operations);
  }
}
