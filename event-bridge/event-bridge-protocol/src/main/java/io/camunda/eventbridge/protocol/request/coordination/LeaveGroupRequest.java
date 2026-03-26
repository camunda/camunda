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
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;

public class LeaveGroupRequest extends UnpackedObject {

  private final StringProperty groupIdProp = new StringProperty("groupId", "");
  private final StringProperty memberIdProp = new StringProperty("memberId", "");
  private final LongProperty memberEpochProp = new LongProperty("memberEpoch", -1L);

  public LeaveGroupRequest() {
    super(3);
    declareProperty(groupIdProp).declareProperty(memberIdProp).declareProperty(memberEpochProp);
  }

  public String getGroupId() {
    return bufferAsString(groupIdProp.getValue());
  }

  public LeaveGroupRequest setGroupId(final String groupId) {
    groupIdProp.setValue(groupId);
    return this;
  }

  public String getMemberId() {
    return bufferAsString(memberIdProp.getValue());
  }

  public LeaveGroupRequest setMemberId(final String memberId) {
    memberIdProp.setValue(memberId);
    return this;
  }

  public long getMemberEpoch() {
    return memberEpochProp.getValue();
  }

  public LeaveGroupRequest setMemberEpoch(final long epoch) {
    memberEpochProp.setValue(epoch);
    return this;
  }
}
