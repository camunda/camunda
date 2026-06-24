/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.topic;

import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.util.buffer.BufferUtil;

/**
 * The msgpack encoding of one entry in a group's subscription — a topic the group consumes and its
 * partition count. The element type of the {@code ArrayProperty}s that carry a group's subscription
 * (on the {@code MEMBER_JOINED} record and in {@code GroupState}). On a {@code JOIN_GROUP} command
 * the count is unset ({@code 0}); the join processor resolves it from the registry and stamps it on
 * the event.
 */
public final class TopicSubscriptionValue extends ObjectValue {

  private final StringProperty topicProp = new StringProperty("topic", "");
  private final IntegerProperty partitionCountProp = new IntegerProperty("partitionCount", 0);

  public TopicSubscriptionValue() {
    super(2);
    declareProperty(topicProp).declareProperty(partitionCountProp);
  }

  public String getTopic() {
    return BufferUtil.bufferAsString(topicProp.getValue());
  }

  public TopicSubscriptionValue setTopic(final String topic) {
    topicProp.setValue(topic == null ? "" : topic);
    return this;
  }

  public int getPartitionCount() {
    return partitionCountProp.getValue();
  }

  public TopicSubscriptionValue setPartitionCount(final int partitionCount) {
    partitionCountProp.setValue(partitionCount);
    return this;
  }
}
