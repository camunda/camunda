/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.stream;

import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.buffer.BufferUtil;

/**
 * Replicated consumer-group metadata (members, their epochs, and assigned partitions) for one
 * group, carried as a {@link GroupMetadataCodec} payload. Written on each rebalance so a
 * coordinator failover can rebuild membership from replayed state.
 */
public final class GroupMetadataRecord extends UnifiedRecordValue {

  private final StringProperty groupIdProp = new StringProperty("groupId", "");
  private final StringProperty payloadProp = new StringProperty("payload", "");

  public GroupMetadataRecord() {
    super(2);
    declareProperty(groupIdProp);
    declareProperty(payloadProp);
  }

  /**
   * The reused {@link ValueType} this record rides on the coordinator's dedicated Raft partition.
   * {@link UnifiedRecordValue#valueType()} resolves via the engine's class→type map, which doesn't
   * know this event-bridge record (it would return {@code null}); the StreamProcessor's result
   * builder reads <em>this</em> to stamp appended follow-up events, so it must be set explicitly or
   * the GROUP_METADATA_COMMITTED event is never written (and followers never replay it).
   */
  @Override
  public ValueType valueType() {
    return EventBridgeRecordValues.GROUP_METADATA_VALUE_TYPE;
  }

  public String getGroupId() {
    return BufferUtil.bufferAsString(groupIdProp.getValue());
  }

  public GroupMetadataRecord setGroupId(final String groupId) {
    groupIdProp.setValue(groupId);
    return this;
  }

  public String getPayload() {
    return BufferUtil.bufferAsString(payloadProp.getValue());
  }

  public GroupMetadataRecord setPayload(final String payload) {
    payloadProp.setValue(payload);
    return this;
  }
}
