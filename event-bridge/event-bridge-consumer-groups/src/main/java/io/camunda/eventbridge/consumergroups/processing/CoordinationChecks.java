/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.processing;

import io.camunda.eventbridge.consumergroups.record.MembershipRecord;
import io.camunda.eventbridge.consumergroups.record.OffsetCommitRecord;
import io.camunda.eventbridge.consumergroups.state.group.MemberState;
import io.camunda.eventbridge.consumergroups.state.immutable.ConsumerGroupState;
import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;
import io.camunda.zeebe.util.Either;

/**
 * Command validation for the coordinator, in the engine style: each check returns {@code
 * Either<Rejection, T>} and the per-command validations chain them with {@link Either#flatMap},
 * short-circuiting on the first rejection. Processors compose the result via {@code ifRightOrLeft}
 * (append an event on the right, a rejection on the left). All checks read the replicated {@link
 * ConsumerGroupState}; this runs on the stream-processing actor.
 */
public final class CoordinationChecks {

  private static final Either<Rejection, Void> VALID = Either.right(null);

  private final ConsumerGroupState state;

  public CoordinationChecks(final ConsumerGroupState state) {
    this.state = state;
  }

  /**
   * A join needs a valid group id, and — for a static member — an instance id that is not already
   * held by a live member. A duplicate {@code group.instance.id} fences the <em>new</em> joiner
   * (KIP-848): the incumbent keeps the identity until it leaves or its session expires (the
   * eviction loop then frees the slot). A new member id is minted by the processor.
   */
  public Either<Rejection, Void> validateJoin(final MembershipRecord command) {
    return groupIdPresent(command.getGroupId())
        .flatMap(ok -> instanceIdAvailable(command.getGroupId(), command.getInstanceId()));
  }

  /** A leave must reference an existing member presenting a current epoch. */
  public Either<Rejection, MemberState> validateLeave(final MembershipRecord command) {
    return groupIdPresent(command.getGroupId())
        .flatMap(ok -> memberExists(command.getGroupId(), command.getMemberId()))
        .flatMap(member -> epochUpToDate(member, command.getMemberEpoch()).map(ok -> member));
  }

  /** A commit must come from an existing member, with a current epoch, that owns the partition. */
  public Either<Rejection, MemberState> validateCommit(final OffsetCommitRecord command) {
    return groupIdPresent(command.getGroupId())
        .flatMap(ok -> memberExists(command.getGroupId(), command.getMemberId()))
        .flatMap(member -> epochUpToDate(member, command.getMemberEpoch()).map(ok -> member))
        .flatMap(member -> ownsPartition(member, command.getPartitionId()).map(ok -> member));
  }

  private Either<Rejection, Void> instanceIdAvailable(
      final String groupId, final String instanceId) {
    if (instanceId == null) {
      return VALID; // dynamic member — no instance id to contend for
    }
    final var holder = state.findMemberByInstanceId(groupId, instanceId);
    if (holder != null) {
      return Either.left(
          new Rejection(
              CoordinationErrorCode.UNRELEASED_INSTANCE_ID,
              "instance id '%s' is still in use by member '%s' in group '%s'; it must leave first"
                  .formatted(instanceId, holder, groupId)));
    }
    return VALID;
  }

  private Either<Rejection, Void> groupIdPresent(final String groupId) {
    if (groupId == null || groupId.isEmpty()) {
      return Either.left(
          new Rejection(CoordinationErrorCode.INVALID_GROUP_ID, "group id is empty"));
    }
    return VALID;
  }

  private Either<Rejection, MemberState> memberExists(final String groupId, final String memberId) {
    final var member = state.getMember(groupId, memberId);
    if (member == null) {
      return Either.left(
          new Rejection(
              CoordinationErrorCode.UNKNOWN_MEMBER_ID,
              "member '%s' is not a member of group '%s'".formatted(memberId, groupId)));
    }
    return Either.right(member);
  }

  private Either<Rejection, Void> epochUpToDate(final MemberState member, final long presented) {
    final var expected = member.getMemberEpoch();
    if (expected > presented) {
      return Either.left(
          new Rejection(
              CoordinationErrorCode.FENCED_MEMBER_EPOCH,
              "member epoch %d is fenced by %d".formatted(presented, expected)));
    }
    if (expected != presented) {
      return Either.left(
          new Rejection(
              CoordinationErrorCode.UNKNOWN_MEMBER_ID,
              "member epoch %d does not match %d".formatted(presented, expected)));
    }
    return VALID;
  }

  private Either<Rejection, Void> ownsPartition(final MemberState member, final int partitionId) {
    if (!member.getTargetPartitions().contains(partitionId)) {
      return Either.left(
          new Rejection(
              CoordinationErrorCode.NOT_PARTITION_OWNER,
              "member does not own partition %d".formatted(partitionId)));
    }
    return VALID;
  }
}
