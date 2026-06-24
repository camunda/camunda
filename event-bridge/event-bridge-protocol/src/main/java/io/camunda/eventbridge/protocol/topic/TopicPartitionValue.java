/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.topic;

import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.util.buffer.BufferUtil;

/**
 * The msgpack encoding of a {@link TopicPartition} — a nested object used as the element type of
 * the {@code ArrayProperty}s that carry a member's owned/target partitions, both in replicated
 * state and on the heartbeat wire, now that those span multiple topics.
 */
public final class TopicPartitionValue extends ObjectValue {

  private final StringProperty topicProp = new StringProperty("topic", "");
  private final IntegerProperty partitionProp = new IntegerProperty("partition", -1);

  public TopicPartitionValue() {
    super(2);
    declareProperty(topicProp).declareProperty(partitionProp);
  }

  public String getTopic() {
    return BufferUtil.bufferAsString(topicProp.getValue());
  }

  public TopicPartitionValue setTopic(final String topic) {
    topicProp.setValue(topic == null ? "" : topic);
    return this;
  }

  public int getPartition() {
    return partitionProp.getValue();
  }

  public TopicPartitionValue setPartition(final int partition) {
    partitionProp.setValue(partition);
    return this;
  }

  public TopicPartition toTopicPartition() {
    return new TopicPartition(getTopic(), getPartition());
  }

  public TopicPartitionValue copyFrom(final TopicPartition topicPartition) {
    return setTopic(topicPartition.topic()).setPartition(topicPartition.partition());
  }
}
