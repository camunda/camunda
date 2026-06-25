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
import io.camunda.zeebe.msgpack.property.StringProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches a consumer group's committed offsets without joining (a read; no log write). An empty
 * partition filter returns every committed offset for the group; a non-empty one returns just those
 * partitions, with {@code -1} for any that has no committed offset yet.
 */
public class OffsetFetchRequest extends UnpackedObject {

  private final StringProperty groupIdProp = new StringProperty("groupId", "");
  private final ArrayProperty<TopicPartitionValue> partitionsProp =
      new ArrayProperty<>("partitions", TopicPartitionValue::new);

  public OffsetFetchRequest() {
    super(2);
    declareProperty(groupIdProp).declareProperty(partitionsProp);
  }

  public String getGroupId() {
    return bufferAsString(groupIdProp.getValue());
  }

  public OffsetFetchRequest setGroupId(final String groupId) {
    groupIdProp.setValue(groupId);
    return this;
  }

  /** The partitions to fetch, or an empty list to fetch every committed offset for the group. */
  public List<TopicPartition> getPartitions() {
    final var partitions = new ArrayList<TopicPartition>();
    partitionsProp.forEach(partition -> partitions.add(partition.toTopicPartition()));
    return partitions;
  }

  public OffsetFetchRequest addPartition(final TopicPartition partition) {
    partitionsProp.add().copyFrom(partition);
    return this;
  }
}
