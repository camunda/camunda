/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.api.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.util.Either;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class AddMembersTransformer implements ConfigurationChangeRequest {

  final Set<MemberId> members;

  public AddMembersTransformer(final Set<MemberId> members) {
    this.members = members;
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration clusterConfiguration) {
    if (clusterConfiguration.isFullyZoneAware() && members.stream().anyMatch(MemberId::isBare)) {
      return Either.left(
          new InvalidRequest(
              "Members without a zone cannot be added to a zone-aware cluster: "
                  + members.stream().filter(MemberId::isBare).sorted().toList()));
    }
    final var operations =
        members.stream()
            // only add members that are not already part of the cluster
            .filter(memberId -> !clusterConfiguration.hasMember(memberId))
            .map(MemberJoinOperation::new)
            .map(ClusterConfigurationChangeOperation.class::cast)
            .sorted(Comparator.comparing(ClusterConfigurationChangeOperation::memberId))
            .toList();
    return Either.right(operations);
  }
}
