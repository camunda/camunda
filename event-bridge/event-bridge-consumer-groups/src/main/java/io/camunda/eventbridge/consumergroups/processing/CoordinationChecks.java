/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.processing;

import io.camunda.eventbridge.consumergroups.membership.TopicRegistry;
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
 * (append an event on the right, a rejection on the left). The checks read the replicated {@link
 * ConsumerGroupState} and the {@link TopicRegistry}; this runs on the stream-processing actor.
 */
public final class CoordinationChecks {

  private static final Either<Rejection, Void> VALID = Either.right(null);

  private final ConsumerGroupState state;
  private final TopicRegistry topicRegistry;

  public CoordinationChecks(final ConsumerGroupState state, final TopicRegistry topicRegistry) {
    this.state = state;
    this.topicRegistry = topicRegistry;
  }

  /**
   * A join needs a valid group id; a subscribed topic that this leader's registry view knows and
   * can serve (resolved <em>here</em>, at processing time, against the leader that produces the
   * durable event — not trusted from a value the requesting broker stamped, which a failover could
   * leave a new leader rubber-stamping for a topic it has never observed); a topic that matches the
   * one the group is already bound to (a group serves exactly one topic); and — for a static member
   * — an instance id that is not already held by a live member. A duplicate {@code
   * group.instance.id} fences the <em>new</em> joiner: the incumbent keeps the identity until it
   * leaves or its session expires (the eviction loop then frees the slot). A new member id is
   * minted by the processor.
   *
   * <p>On success it yields the resolved partition count, which the processor stamps onto the
   * {@code MEMBER_JOINED} event.
   */
  public Either<Rejection, Integer> validateJoin(final MembershipRecord command) {
    return groupIdPresent(command.getGroupId())
        .flatMap(ok -> topicServable(command.getTopic()))
        .flatMap(
            count -> topicMatchesGroup(command.getGroupId(), command.getTopic()).map(ok -> count))
        .flatMap(
            count ->
                instanceIdAvailable(command.getGroupId(), command.getInstanceId())
                    .map(ok -> count));
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

  private Either<Rejection, Integer> topicServable(final String topic) {
    if (topic == null || topic.isEmpty()) {
      return Either.left(
          new Rejection(CoordinationErrorCode.INVALID_TOPIC, "subscribed topic is empty"));
    }
    final var partitionCount = topicRegistry.partitionCount(topic);
    if (partitionCount <= 0) {
      return Either.left(
          new Rejection(
              CoordinationErrorCode.TOPIC_NOT_FOUND,
              "topic '%s' is not registered or not servable".formatted(topic)));
    }
    return Either.right(partitionCount);
  }

  private Either<Rejection, Void> topicMatchesGroup(final String groupId, final String topic) {
    final var group = state.getGroup(groupId);
    if (group == null) {
      return VALID; // first join — the applier binds the group to this topic
    }
    final var bound = group.getTopic();
    if (bound != null && !bound.isEmpty() && !bound.equals(topic)) {
      return Either.left(
          new Rejection(
              CoordinationErrorCode.INVALID_TOPIC,
              "group '%s' is bound to topic '%s', cannot join with topic '%s'"
                  .formatted(groupId, bound, topic)));
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
