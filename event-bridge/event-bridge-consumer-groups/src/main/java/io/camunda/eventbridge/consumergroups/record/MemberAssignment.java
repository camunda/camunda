/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.record;

import io.camunda.eventbridge.protocol.topic.TopicPartition;
import io.camunda.eventbridge.protocol.topic.TopicPartitionValue;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;

/**
 * One member's target partitions within a {@link RebalanceRecord} — the element type of its {@code
 * members} array. A nested msgpack object so the assignor can carry the whole group's proposed
 * assignment in a single {@code REBALANCE_GROUP} command.
 */
public final class MemberAssignment extends ObjectValue {

  private final StringProperty memberIdProp = new StringProperty("memberId", "");
  private final ArrayProperty<TopicPartitionValue> partitionsProp =
      new ArrayProperty<>("partitions", TopicPartitionValue::new);
  // The member's standby target within this proposal (consumer-groups ADR 0006 decision 1); empty
  // for a group with no standby replicas configured, today's behavior for a record written before
  // this field existed.
  private final ArrayProperty<TopicPartitionValue> standbyPartitionsProp =
      new ArrayProperty<>("standbyPartitions", TopicPartitionValue::new);

  public MemberAssignment() {
    super(3);
    declareProperty(memberIdProp)
        .declareProperty(partitionsProp)
        .declareProperty(standbyPartitionsProp);
  }

  public String getMemberId() {
    return BufferUtil.bufferAsString(memberIdProp.getValue());
  }

  public MemberAssignment setMemberId(final String memberId) {
    memberIdProp.setValue(memberId);
    return this;
  }

  public List<TopicPartition> getPartitions() {
    return partitionsProp.stream().map(TopicPartitionValue::toTopicPartition).toList();
  }

  public MemberAssignment setPartitions(final List<TopicPartition> partitions) {
    partitionsProp.reset();
    partitions.forEach(p -> partitionsProp.add().copyFrom(p));
    return this;
  }

  public List<TopicPartition> getStandbyPartitions() {
    return standbyPartitionsProp.stream().map(TopicPartitionValue::toTopicPartition).toList();
  }

  public MemberAssignment setStandbyPartitions(final List<TopicPartition> standbyPartitions) {
    standbyPartitionsProp.reset();
    if (standbyPartitions != null) {
      standbyPartitions.forEach(p -> standbyPartitionsProp.add().copyFrom(p));
    }
    return this;
  }
}
