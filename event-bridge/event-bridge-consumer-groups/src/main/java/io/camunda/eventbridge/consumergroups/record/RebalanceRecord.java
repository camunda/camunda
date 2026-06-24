/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.record;

import io.camunda.eventbridge.consumergroups.processing.RebalanceProcessor;
import io.camunda.eventbridge.consumergroups.state.group.DbConsumerGroupState;
import io.camunda.eventbridge.protocol.topic.TopicPartition;
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
 * Replicated record carrying a proposed/committed target assignment for one group: the async
 * assignor writes it as a {@code REBALANCE_GROUP} command, and the {@link RebalanceProcessor}
 * re-emits it as a {@code GROUP_REBALANCED} event applied to {@link DbConsumerGroupState}.
 *
 * <p>{@code assignmentEpoch} is the group epoch the target reflects — the {@link
 * RebalanceProcessor} drops the command if the group epoch has since advanced (roster changed) or
 * the assignment is already applied, so a committed target always matches the membership it was
 * computed for.
 */
public final class RebalanceRecord extends UnifiedRecordValue {

  private final StringProperty groupIdProp = new StringProperty("groupId", "");
  private final LongProperty assignmentEpochProp = new LongProperty("assignmentEpoch", 0L);
  private final ArrayProperty<MemberAssignment> membersProp =
      new ArrayProperty<>("members", MemberAssignment::new);

  public RebalanceRecord() {
    super(3);
    declareProperty(groupIdProp).declareProperty(assignmentEpochProp).declareProperty(membersProp);
  }

  /**
   * The reused {@link ValueType} this record rides on the coordinator's dedicated Raft partition;
   * see {@link EventBridgeRecordValues#REBALANCE_VALUE_TYPE}. Set explicitly so the StreamProcessor
   * stamps appended follow-up events (the engine's class→type map does not know this record).
   */
  @Override
  public ValueType valueType() {
    return EventBridgeRecordValues.REBALANCE_VALUE_TYPE;
  }

  public String getGroupId() {
    return BufferUtil.bufferAsString(groupIdProp.getValue());
  }

  public RebalanceRecord setGroupId(final String groupId) {
    groupIdProp.setValue(groupId);
    return this;
  }

  public long getAssignmentEpoch() {
    return assignmentEpochProp.getValue();
  }

  public RebalanceRecord setAssignmentEpoch(final long assignmentEpoch) {
    assignmentEpochProp.setValue(assignmentEpoch);
    return this;
  }

  /** The proposed target as {@code memberId → (topic, partition)s} (insertion order preserved). */
  public Map<String, List<TopicPartition>> getMembers() {
    final var members = new LinkedHashMap<String, List<TopicPartition>>();
    membersProp.forEach(m -> members.put(m.getMemberId(), m.getPartitions()));
    return members;
  }

  public RebalanceRecord setMembers(final Map<String, List<TopicPartition>> assignment) {
    membersProp.reset();
    assignment.forEach(
        (memberId, partitions) ->
            membersProp.add().setMemberId(memberId).setPartitions(partitions));
    return this;
  }
}
