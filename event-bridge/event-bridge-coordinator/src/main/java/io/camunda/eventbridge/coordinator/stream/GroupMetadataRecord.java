/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.coordinator.stream;

import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
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
