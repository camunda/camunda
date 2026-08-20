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
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.util.Either;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class RemoveMembersTransformer implements ConfigurationChangeRequest {

  final Set<MemberId> members;

  public RemoveMembersTransformer(final Set<MemberId> members) {
    this.members = members;
  }

  /**
   * One global phase asking the brokers to leave. The leave itself is cluster-wide, but it only
   * succeeds once the broker replicates no partition of any physical tenant — this request moves
   * nothing off it, so a broker that still holds partitions is refused when the plan is applied.
   * Use a scale request to have the partitions moved first.
   */
  @Override
  public Either<Exception, List<Phase>> phases(final CurrentClusterConfiguration configuration) {
    final List<GlobalChangeOperation> leaves =
        members.stream()
            // only remove members that are already part of the cluster
            .filter(configuration.getMembers()::contains)
            .map(memberId -> (GlobalChangeOperation) new MemberLeaveOperation(memberId))
            .sorted(Comparator.comparing(GlobalChangeOperation::memberId))
            .toList();
    return Either.right(leaves.isEmpty() ? List.of() : List.of(new GlobalPhase(leaves)));
  }
}
