/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.request.coordination;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.eventbridge.protocol.topic.TopicPartition;
import io.camunda.eventbridge.protocol.topic.TopicPartitionValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.LongValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class HeartbeatRequest extends UnpackedObject {

  private final StringProperty groupIdProp = new StringProperty("groupId", "");
  private final StringProperty memberIdProp = new StringProperty("memberId", "");
  private final LongProperty memberEpochProp = new LongProperty("memberEpoch", -1L);
  private final ArrayProperty<TopicPartitionValue> ownedPartitionsProp =
      new ArrayProperty<>("ownedPartitions", TopicPartitionValue::new);
  // Standby readiness (event-bridge-streaming ADR 0009 decision 6 / consumer-groups ADR 0006
  // decision 1): the member's changelog lag for each standby partition it is warming, as two
  // parallel arrays (partition[i] -> lag[i]), mirroring the committedOffsets encoding below.
  // Absent/empty for a member with no standby partitions, so a request encoded before this field
  // existed decodes unchanged.
  private final ArrayProperty<TopicPartitionValue> standbyReadinessPartitionsProp =
      new ArrayProperty<>("standbyReadinessPartitions", TopicPartitionValue::new);
  private final ArrayProperty<LongValue> standbyReadinessLagProp =
      new ArrayProperty<>("standbyReadinessLag", LongValue::new);

  public HeartbeatRequest() {
    super(6);
    declareProperty(groupIdProp)
        .declareProperty(memberIdProp)
        .declareProperty(memberEpochProp)
        .declareProperty(ownedPartitionsProp)
        .declareProperty(standbyReadinessPartitionsProp)
        .declareProperty(standbyReadinessLagProp);
  }

  public String getGroupId() {
    return bufferAsString(groupIdProp.getValue());
  }

  public HeartbeatRequest setGroupId(final String groupId) {
    groupIdProp.setValue(groupId);
    return this;
  }

  public String getMemberId() {
    return bufferAsString(memberIdProp.getValue());
  }

  public HeartbeatRequest setMemberId(final String memberId) {
    memberIdProp.reset();
    if (memberId != null && !memberId.isEmpty()) {
      memberIdProp.setValue(memberId);
    }
    return this;
  }

  public long getMemberEpoch() {
    return memberEpochProp.getValue();
  }

  public HeartbeatRequest setMemberEpoch(final long memberEpoch) {
    memberEpochProp.setValue(memberEpoch);
    return this;
  }

  public List<TopicPartition> getOwnedPartitions() {
    final var ownedPartitions = new ArrayList<TopicPartition>();
    ownedPartitionsProp.forEach(e -> ownedPartitions.add(e.toTopicPartition()));
    return ownedPartitions;
  }

  public HeartbeatRequest setOwnedPartitions(final List<TopicPartition> ownedPartitions) {
    ownedPartitionsProp.reset();
    if (ownedPartitions != null && !ownedPartitions.isEmpty()) {
      ownedPartitions.forEach(p -> ownedPartitionsProp.add().copyFrom(p));
    }
    return this;
  }

  /**
   * The member's reported changelog lag per standby partition it is warming (event-bridge-streaming
   * ADR 0009); empty for a member with no standby role. The coordinator derives readiness from this
   * (see {@code GroupReconciliation}) — it is never itself replicated, only the resulting promotion
   * decision is.
   */
  public Map<TopicPartition, Long> getStandbyReadiness() {
    final var partitions = new ArrayList<TopicPartition>();
    standbyReadinessPartitionsProp.forEach(e -> partitions.add(e.toTopicPartition()));
    final var lags = new ArrayList<Long>();
    standbyReadinessLagProp.forEach(e -> lags.add(e.getValue()));

    final var result = new TreeMap<TopicPartition, Long>();
    for (int i = 0; i < Math.min(partitions.size(), lags.size()); i++) {
      result.put(partitions.get(i), lags.get(i));
    }
    return result;
  }

  public HeartbeatRequest setStandbyReadiness(final Map<TopicPartition, Long> standbyReadiness) {
    standbyReadinessPartitionsProp.reset();
    standbyReadinessLagProp.reset();
    if (standbyReadiness != null) {
      standbyReadiness.forEach(
          (partition, lag) -> {
            standbyReadinessPartitionsProp.add().copyFrom(partition);
            standbyReadinessLagProp.add().setValue(lag);
          });
    }
    return this;
  }
}
