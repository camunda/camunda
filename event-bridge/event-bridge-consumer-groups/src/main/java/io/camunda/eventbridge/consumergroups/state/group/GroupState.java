/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.state.group;

import io.camunda.eventbridge.consumergroups.state.EventBridgeColumnFamilies;
import io.camunda.eventbridge.consumergroups.topic.TopicSubscriptionValue;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-group replicated state stored in the {@link EventBridgeColumnFamilies#CONSUMER_GROUPS} column
 * family.
 *
 * <ul>
 *   <li>{@code groupEpoch} — the desired-state version, bumped on every membership change.
 *   <li>{@code assignmentEpoch} — the group epoch the current member targets reflect; {@code
 *       assignmentEpoch < groupEpoch} means a rebalance is pending.
 *   <li>{@code subscriptions} — the topics the group subscribes to with their partition counts,
 *       fixed when the group is created; the assignor balances all their partitions together.
 * </ul>
 */
public final class GroupState extends UnpackedObject implements DbValue {

  private final LongProperty groupEpochProp = new LongProperty("groupEpoch", 0L);
  private final LongProperty assignmentEpochProp = new LongProperty("assignmentEpoch", 0L);
  private final ArrayProperty<TopicSubscriptionValue> subscriptionsProp =
      new ArrayProperty<>("subscriptions", TopicSubscriptionValue::new);

  public GroupState() {
    super(3);
    declareProperty(groupEpochProp)
        .declareProperty(assignmentEpochProp)
        .declareProperty(subscriptionsProp);
  }

  /** The group's subscription as {@code topic → partitionCount} (insertion order preserved). */
  public Map<String, Integer> getSubscriptions() {
    final var subscriptions = new LinkedHashMap<String, Integer>();
    subscriptionsProp.forEach(s -> subscriptions.put(s.getTopic(), s.getPartitionCount()));
    return subscriptions;
  }

  public GroupState setSubscriptions(final Map<String, Integer> subscriptions) {
    subscriptionsProp.reset();
    subscriptions.forEach(
        (topic, count) -> subscriptionsProp.add().setTopic(topic).setPartitionCount(count));
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
}
