/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.request.coordination;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.IntegerValue;
import java.util.ArrayList;
import java.util.List;

public class HeartbeatRequest extends UnpackedObject {

  private final StringProperty groupIdProp = new StringProperty("groupId", "");
  private final StringProperty memberIdProp = new StringProperty("memberId", "");
  private final LongProperty memberEpochProp = new LongProperty("memberEpoch", -1L);
  private final ArrayProperty<IntegerValue> ownedPartitionsProp =
      new ArrayProperty<>("ownedPartitions", IntegerValue::new);

  public HeartbeatRequest() {
    super(4);
    declareProperty(groupIdProp)
        .declareProperty(memberIdProp)
        .declareProperty(memberEpochProp)
        .declareProperty(ownedPartitionsProp);
  }

  public String getGroupId() {
    return bufferAsString(groupIdProp.getValue());
  }

  public HeartbeatRequest setGroupId(final String groupId) {
    groupIdProp.setValue(groupId);
    return this;
  }

  public String getMemberId() {
    return bufferAsString(memberIdProp.getValue());
  }

  public HeartbeatRequest setMemberId(final String memberId) {
    memberIdProp.reset();
    if (memberId != null && !memberId.isEmpty()) {
      memberIdProp.setValue(memberId);
    }
    return this;
  }

  public long getMemberEpoch() {
    return memberEpochProp.getValue();
  }

  public HeartbeatRequest setMemberEpoch(final long memberEpoch) {
    memberEpochProp.setValue(memberEpoch);
    return this;
  }

  public List<Integer> getOwnedPartitions() {
    final var ownedPartitions = new ArrayList<Integer>();
    ownedPartitionsProp.forEach(e -> ownedPartitions.add(e.getValue()));
    return ownedPartitions;
  }

  public HeartbeatRequest setOwnedPartitions(final List<Integer> ownedPartitions) {
    ownedPartitionsProp.reset();
    if (ownedPartitions != null && !ownedPartitions.isEmpty()) {
      ownedPartitions.forEach(p -> ownedPartitionsProp.add().setValue(p));
    }
    return this;
  }
}
