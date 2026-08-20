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
import io.camunda.zeebe.dynamic.config.changes.appliers.PostScalingApplier;
import io.camunda.zeebe.dynamic.config.changes.appliers.PreScalingApplier;
import io.camunda.zeebe.dynamic.config.changes.appliers.UpdatePartitionDistributorConfigApplier;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberRemoveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PostScalingOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PreScalingOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.UpdatePartitionDistributorConfigOperation;

public final class GlobalConfigurationChangeAppliersImpl
    implements GlobalConfigurationChangeAppliers {

  private final ClusterMembershipChangeExecutor clusterMembershipChangeExecutor;
  private final ClusterChangeExecutor clusterChangeExecutor;

  public GlobalConfigurationChangeAppliersImpl(
      final ClusterMembershipChangeExecutor clusterMembershipChangeExecutor,
      final ClusterChangeExecutor clusterChangeExecutor) {
    this.clusterMembershipChangeExecutor = clusterMembershipChangeExecutor;
    this.clusterChangeExecutor = clusterChangeExecutor;
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
      case final PreScalingOperation op ->
          new PreScalingApplier(op.memberId(), op.clusterMembers(), clusterChangeExecutor);
      case final PostScalingOperation op ->
          new PostScalingApplier(op.memberId(), op.clusterMembers(), clusterChangeExecutor);
      case final UpdatePartitionDistributorConfigOperation op ->
          new UpdatePartitionDistributorConfigApplier(op);
    };
  }
}
