/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.request.coordination;

import static io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode.UNKNOWN;

import io.camunda.eventbridge.protocol.topic.TopicPartition;
import io.camunda.eventbridge.protocol.topic.TopicPartitionValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.value.LongValue;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

/**
 * A group's committed offsets per owned {@code (topic, partition)}, returned by an {@link
 * OffsetFetchRequest}. The offsets are carried as two parallel arrays ({@code partition[i] ->
 * offset[i]}), mirroring how the heartbeat reply carries them.
 */
public class OffsetFetchResponse extends UnpackedObject {

  private final EnumProperty<CoordinationErrorCode> errorCodeProp =
      new EnumProperty<>("errorCode", CoordinationErrorCode.class, UNKNOWN);
  private final ArrayProperty<TopicPartitionValue> partitionsProp =
      new ArrayProperty<>("partitions", TopicPartitionValue::new);
  private final ArrayProperty<LongValue> offsetsProp =
      new ArrayProperty<>("offsets", LongValue::new);

  public OffsetFetchResponse() {
    super(3);
    declareProperty(errorCodeProp).declareProperty(partitionsProp).declareProperty(offsetsProp);
  }

  public CoordinationErrorCode getErrorCode() {
    return errorCodeProp.getValue();
  }

  public OffsetFetchResponse setErrorCode(final CoordinationErrorCode errorCode) {
    errorCodeProp.setValue(errorCode);
    return this;
  }

  public Map<TopicPartition, Long> getCommittedOffsets() {
    final var partitions = new ArrayList<TopicPartition>();
    partitionsProp.forEach(e -> partitions.add(e.toTopicPartition()));
    final var offsets = new ArrayList<Long>();
    offsetsProp.forEach(e -> offsets.add(e.getValue()));

    final var result = new TreeMap<TopicPartition, Long>();
    for (int i = 0; i < Math.min(partitions.size(), offsets.size()); i++) {
      result.put(partitions.get(i), offsets.get(i));
    }
    return result;
  }

  public OffsetFetchResponse setCommittedOffsets(final Map<TopicPartition, Long> committedOffsets) {
    partitionsProp.reset();
    offsetsProp.reset();
    if (committedOffsets != null) {
      committedOffsets.forEach(
          (partition, offset) -> {
            partitionsProp.add().copyFrom(partition);
            offsetsProp.add().setValue(offset);
          });
    }
    return this;
  }
}
