/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.request.coordination;

import static io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode.UNKNOWN;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.eventbridge.protocol.topic.TopicPartition;
import io.camunda.eventbridge.protocol.topic.TopicPartitionValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.LongValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class HeartbeatResponse extends UnpackedObject {

  private final EnumProperty<CoordinationErrorCode> errorCodeProp =
      new EnumProperty<>("errorCode", CoordinationErrorCode.class, UNKNOWN);
  private final StringProperty memberIdProp = new StringProperty("memberId", "");
  private final LongProperty memberEpochProp = new LongProperty("memberEpoch", -1L);
  private final ArrayProperty<TopicPartitionValue> assignProp =
      new ArrayProperty<>("assign", TopicPartitionValue::new);
  private final ArrayProperty<TopicPartitionValue> revokeProp =
      new ArrayProperty<>("revoke", TopicPartitionValue::new);
  private final ArrayProperty<TopicPartitionValue> assignmentProp =
      new ArrayProperty<>("assignment", TopicPartitionValue::new);
  private final LongProperty assignmentEpochProp = new LongProperty("assignmentEpoch", -1L);
  // Committed offsets for the member's partitions, as two parallel arrays (partition[i] ->
  // offset[i]).
  private final ArrayProperty<TopicPartitionValue> committedPartitionsProp =
      new ArrayProperty<>("committedPartitions", TopicPartitionValue::new);
  private final ArrayProperty<LongValue> committedOffsetsProp =
      new ArrayProperty<>("committedOffsets", LongValue::new);

  public HeartbeatResponse() {
    super(9);
    declareProperty(errorCodeProp)
        .declareProperty(memberIdProp)
        .declareProperty(memberEpochProp)
        .declareProperty(assignProp)
        .declareProperty(revokeProp)
        .declareProperty(assignmentProp)
        .declareProperty(assignmentEpochProp)
        .declareProperty(committedPartitionsProp)
        .declareProperty(committedOffsetsProp);
  }

  public CoordinationErrorCode getErrorCode() {
    return errorCodeProp.getValue();
  }

  public HeartbeatResponse setErrorCode(final CoordinationErrorCode errorCode) {
    errorCodeProp.setValue(errorCode);
    return this;
  }

  public String getMemberId() {
    return bufferAsString(memberIdProp.getValue());
  }

  public HeartbeatResponse setMemberId(final String memberId) {
    memberIdProp.setValue(memberId);
    return this;
  }

  public long getMemberEpoch() {
    return memberEpochProp.getValue();
  }

  public HeartbeatResponse setMemberEpoch(final long epoch) {
    memberEpochProp.setValue(epoch);
    return this;
  }

  public List<TopicPartition> getAssign() {
    return readPartitions(assignProp);
  }

  public HeartbeatResponse setAssign(final List<TopicPartition> assign) {
    writePartitions(assignProp, assign);
    return this;
  }

  public List<TopicPartition> getRevoke() {
    return readPartitions(revokeProp);
  }

  public HeartbeatResponse setRevoke(final List<TopicPartition> revoke) {
    writePartitions(revokeProp, revoke);
    return this;
  }

  public List<TopicPartition> getAssignment() {
    return readPartitions(assignmentProp);
  }

  public HeartbeatResponse setAssignment(final List<TopicPartition> assignment) {
    writePartitions(assignmentProp, assignment);
    return this;
  }

  public long getAssignmentEpoch() {
    return assignmentEpochProp.getValue();
  }

  public HeartbeatResponse setAssignmentEpoch(final long assignmentEpoch) {
    assignmentEpochProp.setValue(assignmentEpoch);
    return this;
  }

  /** Committed offset per owned (topic, partition) for this member (next position to read). */
  public Map<TopicPartition, Long> getCommittedOffsets() {
    final var partitions = readPartitions(committedPartitionsProp);
    final var offsets = new ArrayList<Long>();
    committedOffsetsProp.forEach(e -> offsets.add(e.getValue()));

    final var result = new TreeMap<TopicPartition, Long>();
    for (int i = 0; i < Math.min(partitions.size(), offsets.size()); i++) {
      result.put(partitions.get(i), offsets.get(i));
    }
    return result;
  }

  public HeartbeatResponse setCommittedOffsets(final Map<TopicPartition, Long> committedOffsets) {
    committedPartitionsProp.reset();
    committedOffsetsProp.reset();
    if (committedOffsets != null) {
      committedOffsets.forEach(
          (partition, offset) -> {
            committedPartitionsProp.add().copyFrom(partition);
            committedOffsetsProp.add().setValue(offset);
          });
    }
    return this;
  }

  private static List<TopicPartition> readPartitions(
      final ArrayProperty<TopicPartitionValue> property) {
    final var partitions = new ArrayList<TopicPartition>();
    property.forEach(e -> partitions.add(e.toTopicPartition()));
    return partitions;
  }

  private static void writePartitions(
      final ArrayProperty<TopicPartitionValue> property, final List<TopicPartition> partitions) {
    property.reset();
    if (partitions != null && !partitions.isEmpty()) {
      partitions.forEach(p -> property.add().copyFrom(p));
    }
  }
}
