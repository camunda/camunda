/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.coordinator.stream;

import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.buffer.BufferUtil;

/**
 * Replicated record carrying a single consumer offset commit {@code (groupId, partitionId,
 * offset)}. Written to the coordinator partition's log as a COMMAND and re-emitted as an
 * OFFSET_COMMITTED event; both the leader (process) and followers (replay) apply it to {@link
 * DbOffsetState}.
 */
public final class OffsetCommitRecord extends UnifiedRecordValue {

  private final StringProperty groupIdProp = new StringProperty("groupId", "");
  private final IntegerProperty partitionIdProp = new IntegerProperty("partitionId", -1);
  private final LongProperty offsetProp = new LongProperty("offset", -1L);

  public OffsetCommitRecord() {
    super(3);
    declareProperty(groupIdProp);
    declareProperty(partitionIdProp);
    declareProperty(offsetProp);
  }

  /**
   * The reused {@link ValueType} this record rides on the coordinator's dedicated Raft partition.
   * {@link UnifiedRecordValue#valueType()} resolves via the engine's class→type map, which doesn't
   * know this event-bridge record (it would return {@code null}); the StreamProcessor's result
   * builder reads <em>this</em> to stamp appended follow-up events, so it must be set explicitly or
   * the OFFSET_COMMITTED event is never written (and followers never replay it).
   */
  @Override
  public ValueType valueType() {
    return EventBridgeRecordValues.OFFSET_VALUE_TYPE;
  }

  public String getGroupId() {
    return BufferUtil.bufferAsString(groupIdProp.getValue());
  }

  public OffsetCommitRecord setGroupId(final String groupId) {
    groupIdProp.setValue(groupId);
    return this;
  }

  public int getPartitionId() {
    return partitionIdProp.getValue();
  }

  public OffsetCommitRecord setPartitionId(final int partitionId) {
    partitionIdProp.setValue(partitionId);
    return this;
  }

  public long getOffset() {
    return offsetProp.getValue();
  }

  public OffsetCommitRecord setOffset(final long offset) {
    offsetProp.setValue(offset);
    return this;
  }
}
