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
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.util.Either;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Names the brokers to force-remove rather than the ones to keep, and answers the request by
 * retaining every other broker. See {@link ForceScaleDownRequestTransformer} for what the removal
 * itself does.
 */
public final class ForceRemoveBrokersRequestTransformer implements ConfigurationChangeRequest {

  private final Set<MemberId> membersToRemove;
  private final MemberId coordinator;

  public ForceRemoveBrokersRequestTransformer(
      final Set<MemberId> membersToRemove, final MemberId coordinator) {
    this.membersToRemove = membersToRemove;
    this.coordinator = coordinator;
  }

  /** Force-removes the named brokers from every physical tenant's partition group. */
  @Override
  public Either<Exception, List<Phase>> phases(final CurrentClusterConfiguration configuration) {
    final var membersToRetain =
        new HashSet<>(configuration.globalConfiguration().members().keySet());
    membersToRetain.removeAll(membersToRemove);

    return new ForceScaleDownRequestTransformer(membersToRetain, coordinator).phases(configuration);
  }

  @Override
  public boolean isForced() {
    return true;
  }
}
