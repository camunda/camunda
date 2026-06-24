/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.state.group;

import io.camunda.eventbridge.consumergroups.state.EventBridgeColumnFamilies;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.util.buffer.BufferUtil;

/**
 * Per-group replicated state stored in the {@link EventBridgeColumnFamilies#CONSUMER_GROUPS} column
 * family.
 *
 * <ul>
 *   <li>{@code groupEpoch} — the desired-state version, bumped on every membership change.
 *   <li>{@code assignmentEpoch} — the group epoch the current member targets reflect; {@code
 *       assignmentEpoch < groupEpoch} means a rebalance is pending.
 *   <li>{@code topic} — the topic the group subscribes to, fixed when the group is created.
 *   <li>{@code partitionCount} — the topic's partition count, fixed when the group is created.
 * </ul>
 */
public final class GroupState extends UnpackedObject implements DbValue {

  private final LongProperty groupEpochProp = new LongProperty("groupEpoch", 0L);
  private final LongProperty assignmentEpochProp = new LongProperty("assignmentEpoch", 0L);
  private final StringProperty topicProp = new StringProperty("topic", "");
  private final IntegerProperty partitionCountProp = new IntegerProperty("partitionCount", 0);

  public GroupState() {
    super(4);
    declareProperty(groupEpochProp)
        .declareProperty(assignmentEpochProp)
        .declareProperty(topicProp)
        .declareProperty(partitionCountProp);
  }

  public String getTopic() {
    return BufferUtil.bufferAsString(topicProp.getValue());
  }

  public GroupState setTopic(final String topic) {
    topicProp.setValue(topic == null ? "" : topic);
    return this;
  }

  public long getGroupEpoch() {
    return groupEpochProp.getValue();
  }

  public GroupState setGroupEpoch(final long groupEpoch) {
    groupEpochProp.setValue(groupEpoch);
    return this;
  }

  public long getAssignmentEpoch() {
    return assignmentEpochProp.getValue();
  }

  public GroupState setAssignmentEpoch(final long assignmentEpoch) {
    assignmentEpochProp.setValue(assignmentEpoch);
    return this;
  }

  public int getPartitionCount() {
    return partitionCountProp.getValue();
  }

  public GroupState setPartitionCount(final int partitionCount) {
    partitionCountProp.setValue(partitionCount);
    return this;
  }
}
