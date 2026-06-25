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
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.util.buffer.BufferUtil;
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
 *   <li>{@code state} — the {@link GroupLifecycle} the appliers transition through as they apply
 *       membership/rebalance events (the state machine lives in the log).
 *   <li>{@code subscriptions} — the topics the group subscribes to with their partition counts,
 *       fixed when the group is created; the assignor balances all their partitions together.
 *   <li>{@code emptySince} — the wall-clock (epoch millis) at which the group last became {@code
 *       EMPTY}, stamped from the emptying {@code MEMBER_LEFT} event so the retention task's
 *       deadline survives failover; only meaningful while the group is {@code EMPTY}, {@code 0}
 *       otherwise.
 * </ul>
 */
public final class GroupState extends UnpackedObject implements DbValue {

  private final LongProperty groupEpochProp = new LongProperty("groupEpoch", 0L);
  private final LongProperty assignmentEpochProp = new LongProperty("assignmentEpoch", 0L);
  private final StringProperty stateProp = new StringProperty("state", GroupLifecycle.EMPTY.name());
  private final ArrayProperty<TopicSubscriptionValue> subscriptionsProp =
      new ArrayProperty<>("subscriptions", TopicSubscriptionValue::new);
  private final LongProperty emptySinceProp = new LongProperty("emptySince", 0L);

  public GroupState() {
    super(5);
    declareProperty(groupEpochProp)
        .declareProperty(assignmentEpochProp)
        .declareProperty(stateProp)
        .declareProperty(subscriptionsProp)
        .declareProperty(emptySinceProp);
  }

  public GroupLifecycle getState() {
    return GroupLifecycle.fromName(BufferUtil.bufferAsString(stateProp.getValue()));
  }

  public GroupState setState(final GroupLifecycle state) {
    stateProp.setValue(state.name());
    return this;
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

  /** Epoch millis the group last became {@code EMPTY}; {@code 0} when it has members. */
  public long getEmptySince() {
    return emptySinceProp.getValue();
  }

  public GroupState setEmptySince(final long emptySince) {
    emptySinceProp.setValue(emptySince);
    return this;
  }
}
