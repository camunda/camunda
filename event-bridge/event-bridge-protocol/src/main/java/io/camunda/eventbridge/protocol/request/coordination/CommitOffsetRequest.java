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
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;

/** Commits a consumer's processed position for a (group, partition) to the coordinator. */
public class CommitOffsetRequest extends UnpackedObject {

  private final StringProperty groupIdProp = new StringProperty("groupId", "");
  private final StringProperty memberIdProp = new StringProperty("memberId", "");
  private final LongProperty memberEpochProp = new LongProperty("memberEpoch", -1L);
  private final IntegerProperty partitionIdProp = new IntegerProperty("partitionId", -1);
  private final LongProperty positionProp = new LongProperty("position", -1L);

  public CommitOffsetRequest() {
    super(5);
    declareProperty(groupIdProp)
        .declareProperty(memberIdProp)
        .declareProperty(memberEpochProp)
        .declareProperty(partitionIdProp)
        .declareProperty(positionProp);
  }

  public long getMemberEpoch() {
    return memberEpochProp.getValue();
  }

  public CommitOffsetRequest setMemberEpoch(final long memberEpoch) {
    memberEpochProp.setValue(memberEpoch);
    return this;
  }

  public String getGroupId() {
    return bufferAsString(groupIdProp.getValue());
  }

  public CommitOffsetRequest setGroupId(final String groupId) {
    groupIdProp.setValue(groupId);
    return this;
  }

  public String getMemberId() {
    return bufferAsString(memberIdProp.getValue());
  }

  public CommitOffsetRequest setMemberId(final String memberId) {
    memberIdProp.reset();
    if (memberId != null && !memberId.isEmpty()) {
      memberIdProp.setValue(memberId);
    }
    return this;
  }

  public int getPartitionId() {
    return partitionIdProp.getValue();
  }

  public CommitOffsetRequest setPartitionId(final int partitionId) {
    partitionIdProp.setValue(partitionId);
    return this;
  }

  public long getPosition() {
    return positionProp.getValue();
  }

  public CommitOffsetRequest setPosition(final long position) {
    positionProp.setValue(position);
    return this;
  }
}
