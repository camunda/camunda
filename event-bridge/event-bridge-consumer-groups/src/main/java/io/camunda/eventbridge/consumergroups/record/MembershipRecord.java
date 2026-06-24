/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.record;

import io.camunda.eventbridge.consumergroups.state.group.DbConsumerGroupState;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.buffer.BufferUtil;

/**
 * Replicated record carrying a single consumer-group membership change. It rides the {@code
 * JOIN_GROUP}/{@code LEAVE_GROUP} commands and the {@code MEMBER_JOINED}/{@code MEMBER_LEFT} events
 * (the intent distinguishes them); both leader (process) and followers (replay) apply it to the
 * replicated {@link DbConsumerGroupState}.
 *
 * <ul>
 *   <li>{@code instanceId} — set for a static member (KIP-345), empty for a dynamic member.
 *   <li>{@code memberId} — assigned by the coordinator; on a {@code JOIN_GROUP} command it is the
 *       <em>candidate</em> id to use if the member is new (ignored for an idempotent static
 *       rejoin).
 *   <li>{@code memberEpoch} — the member's generation, set to the group epoch at join.
 *   <li>{@code groupEpoch} — the group epoch after this change (the desired-state version the
 *       assignor reconciles toward).
 *   <li>{@code partitionCount} — carried on join so the applier can create the group's partition
 *       space.
 * </ul>
 */
public final class MembershipRecord extends UnifiedRecordValue {

  private final StringProperty groupIdProp = new StringProperty("groupId", "");
  private final StringProperty memberIdProp = new StringProperty("memberId", "");
  private final StringProperty instanceIdProp = new StringProperty("instanceId", "");
  private final LongProperty memberEpochProp = new LongProperty("memberEpoch", 0L);
  private final LongProperty groupEpochProp = new LongProperty("groupEpoch", 0L);
  private final IntegerProperty partitionCountProp = new IntegerProperty("partitionCount", 0);

  public MembershipRecord() {
    super(6);
    declareProperty(groupIdProp)
        .declareProperty(memberIdProp)
        .declareProperty(instanceIdProp)
        .declareProperty(memberEpochProp)
        .declareProperty(groupEpochProp)
        .declareProperty(partitionCountProp);
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

  public int getPartitionCount() {
    return partitionCountProp.getValue();
  }

  public MembershipRecord setPartitionCount(final int partitionCount) {
    partitionCountProp.setValue(partitionCount);
    return this;
  }
}
