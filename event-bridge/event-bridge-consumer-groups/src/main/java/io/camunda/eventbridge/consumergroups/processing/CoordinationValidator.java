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
import io.camunda.eventbridge.protocol.topic.TopicPartition;
import io.camunda.zeebe.util.Either;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Command validation for the coordinator, in the engine style: each check returns {@code
 * Either<Rejection, T>} and the per-command validations chain them with {@link Either#flatMap},
 * short-circuiting on the first rejection. Processors compose the result via {@code ifRightOrLeft}
 * (append an event on the right, a rejection on the left). The checks read the replicated {@link
 * ConsumerGroupState} and the {@link TopicRegistry}; this runs on the stream-processing actor.
 */
public final class CoordinationValidator {

  private static final Either<Rejection, Void> VALID = Either.right(null);

  private final ConsumerGroupState state;
  private final TopicRegistry topicRegistry;

  public CoordinationValidator(final ConsumerGroupState state, final TopicRegistry topicRegistry) {
    this.state = state;
    this.topicRegistry = topicRegistry;
  }

  /**
   * A join needs a valid group id; a subscribed topic that this leader's registry view knows and
   * can serve (resolved <em>here</em>, at processing time, against the leader that produces the
   * durable event — not trusted from a value the requesting broker stamped, which a failover could
   * leave a new leader rubber-stamping for a topic it has never observed); and a topic that matches
   * the one the group is already bound to (a group serves exactly one topic). There is no rejection
   * left for a static member's instance id: a fresh or released one is a normal join, and one still
   * held by a live roster member is a static-membership <b>takeover</b> — the processor replaces
   * the incumbent's incarnation rather than fencing the new joiner (see {@link
   * JoinGroupProcessor}).
   *
   * <p>On success it yields a {@link JoinClassification}: the resolved subscription ({@code topic →
   * partitionCount}) every join carries, plus — only for a takeover — the memberId being taken
   * over.
   */
  public Either<Rejection, JoinClassification> validateJoin(final MembershipRecord command) {
    return groupIdPresent(command.getGroupId())
        .flatMap(ok -> topicsServable(command.getTopics()))
        .flatMap(
            subscriptions ->
                subscriptionMatchesGroup(command.getGroupId(), subscriptions.keySet())
                    .map(ok -> subscriptions))
        .map(
            subscriptions ->
                new JoinClassification(
                    subscriptions,
                    state.findMemberByInstanceId(command.getGroupId(), command.getInstanceId())));
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
        .flatMap(
            member ->
                ownsPartition(member, command.getTopic(), command.getPartitionId())
                    .map(ok -> member));
  }

  private Either<Rejection, Map<String, Integer>> topicsServable(final List<String> topics) {
    if (topics == null || topics.isEmpty()) {
      return Either.left(
          new Rejection(CoordinationErrorCode.INVALID_TOPIC, "no subscribed topics"));
    }
    final var subscriptions = new LinkedHashMap<String, Integer>();
    for (final var topic : topics) {
      if (topic == null || topic.isEmpty()) {
        return Either.left(
            new Rejection(CoordinationErrorCode.INVALID_TOPIC, "a subscribed topic is empty"));
      }
      final var partitionCount = topicRegistry.partitionCount(topic);
      if (partitionCount <= 0) {
        return Either.left(
            new Rejection(
                CoordinationErrorCode.TOPIC_NOT_FOUND,
                "topic '%s' is not registered or not servable".formatted(topic)));
      }
      subscriptions.put(topic, partitionCount);
    }
    return Either.right(subscriptions);
  }

  private Either<Rejection, Void> subscriptionMatchesGroup(
      final String groupId, final Set<String> topics) {
    final var group = state.getGroup(groupId);
    if (group == null) {
      return VALID; // first join — the applier binds the group to this subscription
    }
    final var bound = group.getSubscriptions().keySet();
    if (!bound.isEmpty() && !bound.equals(topics)) {
      return Either.left(
          new Rejection(
              CoordinationErrorCode.INVALID_TOPIC,
              "group '%s' is bound to topics %s, cannot join with topics %s"
                  .formatted(groupId, bound, topics)));
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

  private Either<Rejection, Void> ownsPartition(
      final MemberState member, final String topic, final int partitionId) {
    if (!member.getTargetPartitions().contains(new TopicPartition(topic, partitionId))) {
      return Either.left(
          new Rejection(
              CoordinationErrorCode.NOT_PARTITION_OWNER,
              "member does not own partition %d of topic '%s'".formatted(partitionId, topic)));
    }
    return VALID;
  }
}
