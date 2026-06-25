/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.processing;

import io.camunda.eventbridge.consumergroups.record.MembershipRecord;
import io.camunda.eventbridge.consumergroups.record.RebalanceRecord;
import io.camunda.eventbridge.consumergroups.state.group.GroupLifecycle;
import io.camunda.eventbridge.consumergroups.state.immutable.ConsumerGroupState;
import io.camunda.zeebe.util.Either;

/**
 * Validation for the coordinator's <em>internal</em> state-machine commands — {@code
 * RECONCILE_MEMBER}, {@code REBALANCE_GROUP}, {@code DELETE_GROUP} — the sibling of {@link
 * CoordinationValidator}. These commands are appended by the coordinator/async tasks, not clients,
 * so a failed check is not a client error code but a reason the command is dropped: each check
 * returns {@code Either<String, Void>} (the rejection reason on the left), which the processor
 * turns into a {@code COMMAND_REJECTION} record with no reply. The guards encode the legal
 * transitions of the {@link GroupLifecycle} state machine against the current replicated {@link
 * ConsumerGroupState}; this runs on the stream-processing actor.
 */
public final class TransitionValidator {

  private static final Either<String, Void> VALID = Either.right(null);

  private final ConsumerGroupState state;

  public TransitionValidator(final ConsumerGroupState state) {
    this.state = state;
  }

  /**
   * A reconcile is valid while the reported member still exists at the current group epoch and has
   * not already converged to it (a stale or duplicate report is dropped — the heartbeat may
   * re-send).
   */
  public Either<String, Void> validateReconcile(final MembershipRecord command) {
    final var group = state.getGroup(command.getGroupId());
    if (group == null) {
      return Either.left("group no longer exists");
    }
    final var member = state.getMember(command.getGroupId(), command.getMemberId());
    if (member == null) {
      return Either.left(
          "member '%s' is not in group '%s'"
              .formatted(command.getMemberId(), command.getGroupId()));
    }
    if (command.getGroupEpoch() != group.getGroupEpoch()) {
      return Either.left(
          "stale reconcile: reported epoch %d != group epoch %d"
              .formatted(command.getGroupEpoch(), group.getGroupEpoch()));
    }
    if (member.getAssignedEpoch() == group.getGroupEpoch()) {
      return Either.left("member already reconciled to epoch %d".formatted(group.getGroupEpoch()));
    }
    return VALID;
  }

  /**
   * A proposed target is valid while the group still exists, the target was computed for the
   * current group epoch (the roster has not changed since), and it has not already been applied.
   */
  public Either<String, Void> validateRebalance(final RebalanceRecord command) {
    final var group = state.getGroup(command.getGroupId());
    if (group == null) {
      return Either.left("group no longer exists");
    }
    if (command.getAssignmentEpoch() != group.getGroupEpoch()) {
      return Either.left(
          "stale roster: target epoch %d != group epoch %d"
              .formatted(command.getAssignmentEpoch(), group.getGroupEpoch()));
    }
    if (group.getAssignmentEpoch() >= command.getAssignmentEpoch()) {
      return Either.left("target epoch %d already applied".formatted(command.getAssignmentEpoch()));
    }
    return VALID;
  }

  /** A delete is valid only while the group still exists and is still {@code EMPTY}. */
  public Either<String, Void> validateDelete(final MembershipRecord command) {
    final var group = state.getGroup(command.getGroupId());
    if (group == null) {
      return Either.left("group no longer exists");
    }
    if (group.getState() != GroupLifecycle.EMPTY || !state.isGroupEmpty(command.getGroupId())) {
      return Either.left("group '%s' is no longer empty".formatted(command.getGroupId()));
    }
    return VALID;
  }
}
