/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberRemoveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PostScalingOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PreScalingOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.UpdatePartitionDistributorConfigOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.AwaitModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.DeleteHistoryOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ExportingStateChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionDeleteExporterOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionDisableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionEnableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionForceReconfigureOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPreRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.AwaitRedistributionCompletion;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.AwaitRelocationCompletion;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.StartPartitionScaleUp;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateIncarnationNumberOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateRoutingState;
import org.jspecify.annotations.NullMarked;

/**
 * What each operation writes in the configuration, derived from what its applier actually does.
 *
 * <p>This switch is the counterpart of the one in {@code ConfigurationChangeAppliersImpl} that
 * picks the applier. The two must stay in step: an entry here that claims less than its applier
 * writes is a silent correctness bug, because it lets the coordinator admit a plan whose concurrent
 * operations race. Adding an operation without adding it here does not compile, which is the point
 * of switching over the sealed hierarchy rather than using a default branch.
 *
 * <p>Six operations write nothing at all — their appliers return {@code UnaryOperator.identity()}
 * from both {@code init} and {@code apply}, and their entire effect is elsewhere in the broker.
 * Disjointness therefore says nothing useful about them, and only a declared dependency can order
 * them. They are marked below.
 */
@NullMarked
public final class WriteSets {

  private WriteSets() {}

  public static WriteSet of(final ClusterConfigurationChangeOperation operation) {
    return switch (operation) {
      case final GlobalChangeOperation global -> ofGlobal(global);
      case final PartitionGroupOperation group -> ofPartitionGroup(group);
    };
  }

  private static WriteSet ofGlobal(final GlobalChangeOperation operation) {
    return switch (operation) {
      case final MemberJoinOperation op -> WriteSet.member(op.memberId());
      case final MemberLeaveOperation op -> WriteSet.member(op.memberId());

      // Writes the entry of the member being removed, NOT the member applying the operation —
      // it dispatches to MemberLeaveApplier(op.memberToRemove(), ...).
      case final MemberRemoveOperation op -> WriteSet.member(op.memberToRemove());

      // Writes nothing: brackets an engine-side scale-up. Ordered only by declared dependencies.
      case final PreScalingOperation ignored -> WriteSet.none();
      case final PostScalingOperation ignored -> WriteSet.none();

      case final UpdatePartitionDistributorConfigOperation ignored -> WriteSet.subConfig();
    };
  }

  private static WriteSet ofPartitionGroup(final PartitionGroupOperation operation) {
    return switch (operation) {
      // Writes nothing: deletes history as a side effect.
      case final DeleteHistoryOperation ignored -> WriteSet.none();

      case final UpdateRoutingState ignored -> WriteSet.subConfig();
      case final UpdateIncarnationNumberOperation ignored -> WriteSet.subConfig();

      case final ModeChangeOperation op -> WriteSet.member(op.memberId());
      // Writes several partitions of its own member, so it is member-scoped, not partition-scoped.
      case final AwaitModeChangeOperation op -> WriteSet.member(op.memberId());
      case final ExportingStateChangeOperation op -> WriteSet.member(op.memberId());

      case final StartPartitionScaleUp ignored -> WriteSet.subConfig();
      case final AwaitRedistributionCompletion ignored -> WriteSet.subConfig();
      // Writes nothing: waits for relocation as a side effect.
      case final AwaitRelocationCompletion ignored -> WriteSet.none();

      case final PartitionJoinOperation op -> WriteSet.partition(op.memberId(), op.partitionId());
      case final PartitionLeaveOperation op -> WriteSet.partition(op.memberId(), op.partitionId());
      case final PartitionReconfigurePriorityOperation op ->
          WriteSet.partition(op.memberId(), op.partitionId());
      case final PartitionDisableExporterOperation op ->
          WriteSet.partition(op.memberId(), op.partitionId());
      case final PartitionDeleteExporterOperation op ->
          WriteSet.partition(op.memberId(), op.partitionId());
      case final PartitionEnableExporterOperation op ->
          WriteSet.partition(op.memberId(), op.partitionId());
      case final PartitionBootstrapOperation op ->
          WriteSet.partition(op.memberId(), op.partitionId());

      // Deliberately conflicts with everything in its group. The applier loops
      // group.members().keySet() and removes the partition from every member not in the target
      // replication group, so the written set depends on the live configuration and cannot be
      // derived from the operation alone. Force-reconfigure is a recovery path; serialising it is
      // both safe and correct.
      case final PartitionForceReconfigureOperation ignored -> WriteSet.subConfig();

      // Write nothing: drop local data / restore from backup, both purely local side effects.
      case final PartitionPreRestoreOperation ignored -> WriteSet.none();
      case final PartitionRestoreOperation ignored -> WriteSet.none();
    };
  }
}
