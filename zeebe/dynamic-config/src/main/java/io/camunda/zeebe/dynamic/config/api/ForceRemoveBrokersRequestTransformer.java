/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.util.Either;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ForceRemoveBrokersRequestTransformer implements ConfigurationChangeRequest {

  private final Set<MemberId> membersToRemove;
  private final MemberId coordinator;

  public ForceRemoveBrokersRequestTransformer(
      final Set<MemberId> membersToRemove, final MemberId coordinator) {
    this.membersToRemove = membersToRemove;
    this.coordinator = coordinator;
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration clusterConfiguration) {
    final var membersToRetain = new HashSet<>(clusterConfiguration.members().keySet());
    membersToRetain.removeAll(membersToRemove);

    return new ForceScaleDownRequestTransformer(membersToRetain, coordinator)
        .operations(clusterConfiguration);
  }

  @Override
  public boolean isForced() {
    return true;
  }
}
