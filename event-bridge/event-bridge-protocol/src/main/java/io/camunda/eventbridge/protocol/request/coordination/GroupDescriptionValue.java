/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.request.coordination;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.IntegerValue;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.LinkedHashMap;
import java.util.Map;

/** One group's observable description within a {@link DescribeGroupsResponse}. */
public final class GroupDescriptionValue extends ObjectValue {

  private final StringProperty groupIdProp = new StringProperty("groupId", "");
  private final StringProperty stateProp = new StringProperty("state", "");
  private final LongProperty groupEpochProp = new LongProperty("groupEpoch", 0L);
  private final LongProperty assignmentEpochProp = new LongProperty("assignmentEpoch", 0L);
  // Subscription as parallel arrays: topic[i] -> partitionCount[i].
  private final ArrayProperty<StringValue> topicsProp =
      new ArrayProperty<>("topics", StringValue::new);
  private final ArrayProperty<IntegerValue> partitionCountsProp =
      new ArrayProperty<>("partitionCounts", IntegerValue::new);
  // Members as parallel arrays: memberId[i] -> assignedEpoch[i] (the epoch the member has
  // reconciled to; assignedEpoch < groupEpoch means that member is lagging a rebalance).
  private final ArrayProperty<StringValue> membersProp =
      new ArrayProperty<>("members", StringValue::new);
  private final ArrayProperty<LongValue> memberAssignedEpochsProp =
      new ArrayProperty<>("memberAssignedEpochs", LongValue::new);

  public GroupDescriptionValue() {
    super(8);
    declareProperty(groupIdProp)
        .declareProperty(stateProp)
        .declareProperty(groupEpochProp)
        .declareProperty(assignmentEpochProp)
        .declareProperty(topicsProp)
        .declareProperty(partitionCountsProp)
        .declareProperty(membersProp)
        .declareProperty(memberAssignedEpochsProp);
  }

  public String getGroupId() {
    return BufferUtil.bufferAsString(groupIdProp.getValue());
  }

  public GroupDescriptionValue setGroupId(final String groupId) {
    groupIdProp.setValue(groupId);
    return this;
  }

  public String getState() {
    return BufferUtil.bufferAsString(stateProp.getValue());
  }

  public GroupDescriptionValue setState(final String state) {
    stateProp.setValue(state);
    return this;
  }

  public long getGroupEpoch() {
    return groupEpochProp.getValue();
  }

  public GroupDescriptionValue setGroupEpoch(final long groupEpoch) {
    groupEpochProp.setValue(groupEpoch);
    return this;
  }

  public long getAssignmentEpoch() {
    return assignmentEpochProp.getValue();
  }

  public GroupDescriptionValue setAssignmentEpoch(final long assignmentEpoch) {
    assignmentEpochProp.setValue(assignmentEpoch);
    return this;
  }

  public Map<String, Integer> getSubscriptions() {
    final var subscriptions = new LinkedHashMap<String, Integer>();
    final var topics = topicsProp.iterator();
    final var counts = partitionCountsProp.iterator();
    while (topics.hasNext() && counts.hasNext()) {
      subscriptions.put(
          BufferUtil.bufferAsString(topics.next().getValue()), counts.next().getValue());
    }
    return subscriptions;
  }

  public GroupDescriptionValue setSubscriptions(final Map<String, Integer> subscriptions) {
    topicsProp.reset();
    partitionCountsProp.reset();
    subscriptions.forEach(
        (topic, count) -> {
          topicsProp.add().wrap(BufferUtil.wrapString(topic));
          partitionCountsProp.add().setValue(count);
        });
    return this;
  }

  /** Members as {@code memberId → assignedEpoch} (insertion order preserved). */
  public Map<String, Long> getMembers() {
    final var members = new LinkedHashMap<String, Long>();
    final var ids = membersProp.iterator();
    final var epochs = memberAssignedEpochsProp.iterator();
    while (ids.hasNext() && epochs.hasNext()) {
      members.put(BufferUtil.bufferAsString(ids.next().getValue()), epochs.next().getValue());
    }
    return members;
  }

  public GroupDescriptionValue setMembers(final Map<String, Long> members) {
    membersProp.reset();
    memberAssignedEpochsProp.reset();
    members.forEach(
        (memberId, assignedEpoch) -> {
          membersProp.add().wrap(BufferUtil.wrapString(memberId));
          memberAssignedEpochsProp.add().setValue(assignedEpoch);
        });
    return this;
  }
}
