/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import io.camunda.zeebe.dynamic.config.changes.appliers.MemberJoinApplier;
import io.camunda.zeebe.dynamic.config.changes.appliers.MemberLeaveApplier;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberRemoveOperation;

public final class GlobalConfigurationChangeAppliersImpl
    implements GlobalConfigurationChangeAppliers {

  private final ClusterMembershipChangeExecutor clusterMembershipChangeExecutor;

  public GlobalConfigurationChangeAppliersImpl(
      final ClusterMembershipChangeExecutor clusterMembershipChangeExecutor) {
    this.clusterMembershipChangeExecutor = clusterMembershipChangeExecutor;
  }

  @Override
  public GlobalConfigurationChangeApplier getApplier(final GlobalChangeOperation operation) {
    return switch (operation) {
      case final MemberJoinOperation op ->
          new MemberJoinApplier(op.memberId(), clusterMembershipChangeExecutor);
      case final MemberLeaveOperation op ->
          new MemberLeaveApplier(op.memberId(), clusterMembershipChangeExecutor);
      case final MemberRemoveOperation op ->
          // Reuse MemberLeaveApplier, only difference is that the member applying the operation is
          // not the member that is leaving
          new MemberLeaveApplier(op.memberToRemove(), clusterMembershipChangeExecutor);
      default ->
          throw new UnsupportedOperationException(
              "No new-model applier implemented yet for %s".formatted(operation));
    };
  }
}
