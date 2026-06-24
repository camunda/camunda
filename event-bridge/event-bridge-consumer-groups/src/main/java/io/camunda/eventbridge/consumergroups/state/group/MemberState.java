/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.state.group;

import io.camunda.eventbridge.consumergroups.state.EventBridgeColumnFamilies;
import io.camunda.eventbridge.protocol.topic.TopicPartition;
import io.camunda.eventbridge.protocol.topic.TopicPartitionValue;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;

/**
 * Per-member replicated state stored in the {@link
 * EventBridgeColumnFamilies#CONSUMER_GROUP_MEMBERS} column family, keyed by {@code (groupId,
 * memberId)}.
 *
 * <ul>
 *   <li>{@code instanceId} — the static-membership instance id (empty for a dynamic member).
 *   <li>{@code memberEpoch} — the member's generation, set to the group epoch at join, used to
 *       fence stale (zombie) requests.
 *   <li>{@code targetPartitions} — the assignor's target for this member; offset commits are fenced
 *       against it.
 * </ul>
 */
public final class MemberState extends UnpackedObject implements DbValue {

  private final StringProperty instanceIdProp = new StringProperty("instanceId", "");
  private final LongProperty memberEpochProp = new LongProperty("memberEpoch", 0L);
  private final ArrayProperty<TopicPartitionValue> targetPartitionsProp =
      new ArrayProperty<>("targetPartitions", TopicPartitionValue::new);

  public MemberState() {
    super(3);
    declareProperty(instanceIdProp)
        .declareProperty(memberEpochProp)
        .declareProperty(targetPartitionsProp);
  }

  /** The static-membership instance id, or {@code null} for a dynamic member. */
  public String getInstanceId() {
    final var instanceId = BufferUtil.bufferAsString(instanceIdProp.getValue());
    return instanceId.isEmpty() ? null : instanceId;
  }

  public MemberState setInstanceId(final String instanceId) {
    instanceIdProp.setValue(instanceId == null ? "" : instanceId);
    return this;
  }

  public boolean isStaticMember() {
    return getInstanceId() != null;
  }

  public long getMemberEpoch() {
    return memberEpochProp.getValue();
  }

  public MemberState setMemberEpoch(final long memberEpoch) {
    memberEpochProp.setValue(memberEpoch);
    return this;
  }

  public List<TopicPartition> getTargetPartitions() {
    return targetPartitionsProp.stream().map(TopicPartitionValue::toTopicPartition).toList();
  }

  public MemberState setTargetPartitions(final List<TopicPartition> partitions) {
    targetPartitionsProp.reset();
    partitions.forEach(p -> targetPartitionsProp.add().copyFrom(p));
    return this;
  }
}
