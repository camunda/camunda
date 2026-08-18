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
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation;
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
    return joins(clusterConfiguration.members().keySet(), clusterConfiguration.isFullyZoneAware())
        .map(List::<ClusterConfigurationChangeOperation>copyOf);
  }

  /**
   * The joins this request amounts to on a cluster with the given members.
   *
   * <p>Takes the member set rather than a configuration, because that is all the answer depends on:
   * joining a broker is global, with no per-tenant dimension.
   */
  Either<Exception, List<GlobalChangeOperation>> joins(
      final Set<MemberId> currentMembers, final boolean fullyZoneAware) {
    if (fullyZoneAware && members.stream().anyMatch(MemberId::isBare)) {
      return Either.left(
          new InvalidRequest(
              "Members without a zone cannot be added to a zone-aware cluster: "
                  + members.stream().filter(MemberId::isBare).sorted().toList()));
    }
    return Either.right(
        members.stream()
            // only add members that are not already part of the cluster
            .filter(memberId -> !currentMembers.contains(memberId))
            .map(memberId -> (GlobalChangeOperation) new MemberJoinOperation(memberId))
            .sorted(Comparator.comparing(GlobalChangeOperation::memberId))
            .toList());
  }
}
