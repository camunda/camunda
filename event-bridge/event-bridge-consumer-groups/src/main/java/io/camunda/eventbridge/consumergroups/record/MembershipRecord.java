/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.record;

import io.camunda.eventbridge.consumergroups.state.group.DbConsumerGroupState;
import io.camunda.eventbridge.consumergroups.topic.TopicSubscriptionValue;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Replicated record carrying a single consumer-group membership change. It rides the {@code
 * JOIN_GROUP}/{@code LEAVE_GROUP} commands and the {@code MEMBER_JOINED}/{@code MEMBER_LEFT} events
 * (the intent distinguishes them); both leader (process) and followers (replay) apply it to the
 * replicated {@link DbConsumerGroupState}.
 *
 * <ul>
 *   <li>{@code subscriptions} — the topics the group subscribes to with their partition counts;
 *       carried on join so the applier records the subscription on the group (counts are unset on
 *       the command and resolved from the registry onto the event).
 *   <li>{@code instanceId} — set for a static member, empty for a dynamic member.
 *   <li>{@code memberId} — assigned by the coordinator; on a {@code JOIN_GROUP} command it is the
 *       new member's id (a join whose static instance id is already in use is rejected, so every
 *       successful join mints a fresh member rather than re-using an existing one).
 *   <li>{@code memberEpoch} — the member's generation, set to the group epoch at join.
 *   <li>{@code groupEpoch} — the group epoch after this change (the desired-state version the
 *       assignor reconciles toward).
 * </ul>
 */
public final class MembershipRecord extends UnifiedRecordValue {

  private final StringProperty groupIdProp = new StringProperty("groupId", "");
  private final ArrayProperty<TopicSubscriptionValue> subscriptionsProp =
      new ArrayProperty<>("subscriptions", TopicSubscriptionValue::new);
  private final StringProperty memberIdProp = new StringProperty("memberId", "");
  private final StringProperty instanceIdProp = new StringProperty("instanceId", "");
  private final LongProperty memberEpochProp = new LongProperty("memberEpoch", 0L);
  private final LongProperty groupEpochProp = new LongProperty("groupEpoch", 0L);

  public MembershipRecord() {
    super(6);
    declareProperty(groupIdProp)
        .declareProperty(subscriptionsProp)
        .declareProperty(memberIdProp)
        .declareProperty(instanceIdProp)
        .declareProperty(memberEpochProp)
        .declareProperty(groupEpochProp);
  }

  /**
   * The reused {@link ValueType} this record rides on the coordinator's dedicated Raft partition;
   * see {@link EventBridgeRecordValues#MEMBERSHIP_VALUE_TYPE}. {@link
   * UnifiedRecordValue#valueType()} resolves via the engine's class→type map, which doesn't know
   * this event-bridge record; the StreamProcessor's result builder reads <em>this</em> to stamp
   * appended follow-up events, so it must be set explicitly or the event is never written (and
   * followers never replay it).
   */
  @Override
  public ValueType valueType() {
    return EventBridgeRecordValues.MEMBERSHIP_VALUE_TYPE;
  }

  public String getGroupId() {
    return BufferUtil.bufferAsString(groupIdProp.getValue());
  }

  public MembershipRecord setGroupId(final String groupId) {
    groupIdProp.setValue(groupId);
    return this;
  }

  /** The topics the group subscribes to (the partition counts may be unset on a command). */
  public List<String> getTopics() {
    return subscriptionsProp.stream().map(TopicSubscriptionValue::getTopic).toList();
  }

  /**
   * The group's subscription as {@code topic → partitionCount} (insertion order preserved). On a
   * {@code JOIN_GROUP} command the counts are {@code 0}; on the {@code MEMBER_JOINED} event they
   * are the registry-resolved counts.
   */
  public Map<String, Integer> getSubscriptions() {
    final var subscriptions = new LinkedHashMap<String, Integer>();
    subscriptionsProp.forEach(s -> subscriptions.put(s.getTopic(), s.getPartitionCount()));
    return subscriptions;
  }

  /** Sets the subscribed topics with unset ({@code 0}) partition counts — used on the command. */
  public MembershipRecord setTopics(final List<String> topics) {
    subscriptionsProp.reset();
    if (topics != null) {
      topics.forEach(topic -> subscriptionsProp.add().setTopic(topic).setPartitionCount(0));
    }
    return this;
  }

  /** Sets the resolved subscription ({@code topic → partitionCount}) — used on the event. */
  public MembershipRecord setSubscriptions(final Map<String, Integer> subscriptions) {
    subscriptionsProp.reset();
    subscriptions.forEach(
        (topic, count) -> subscriptionsProp.add().setTopic(topic).setPartitionCount(count));
    return this;
  }

  public String getMemberId() {
    return BufferUtil.bufferAsString(memberIdProp.getValue());
  }

  public MembershipRecord setMemberId(final String memberId) {
    memberIdProp.setValue(memberId);
    return this;
  }

  /** The static-membership instance id, or {@code null} for a dynamic member. */
  public String getInstanceId() {
    final var instanceId = BufferUtil.bufferAsString(instanceIdProp.getValue());
    return instanceId.isEmpty() ? null : instanceId;
  }

  public MembershipRecord setInstanceId(final String instanceId) {
    instanceIdProp.setValue(instanceId == null ? "" : instanceId);
    return this;
  }

  public long getMemberEpoch() {
    return memberEpochProp.getValue();
  }

  public MembershipRecord setMemberEpoch(final long memberEpoch) {
    memberEpochProp.setValue(memberEpoch);
    return this;
  }

  public long getGroupEpoch() {
    return groupEpochProp.getValue();
  }

  public MembershipRecord setGroupEpoch(final long groupEpoch) {
    groupEpochProp.setValue(groupEpoch);
    return this;
  }
}
