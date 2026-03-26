/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.request.coordination;

import static io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode.UNKNOWN;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.IntegerValue;
import java.util.ArrayList;
import java.util.List;

public class HeartbeatResponse extends UnpackedObject {

  private final EnumProperty<CoordinationErrorCode> errorCodeProp =
      new EnumProperty<>("errorCode", CoordinationErrorCode.class, UNKNOWN);
  private final StringProperty memberIdProp = new StringProperty("memberId", "");
  private final LongProperty memberEpochProp = new LongProperty("memberEpoch", -1L);
  private final ArrayProperty<IntegerValue> assignProp =
      new ArrayProperty<>("assign", IntegerValue::new);
  private final ArrayProperty<IntegerValue> revokeProp =
      new ArrayProperty<>("revoke", IntegerValue::new);
  private final ArrayProperty<IntegerValue> assignmentProp =
      new ArrayProperty<>("assignment", IntegerValue::new);
  private final LongProperty assignmentEpochProp = new LongProperty("assignmentEpoch", -1L);

  public HeartbeatResponse() {
    super(7);
    declareProperty(errorCodeProp)
        .declareProperty(memberIdProp)
        .declareProperty(memberEpochProp)
        .declareProperty(assignProp)
        .declareProperty(revokeProp)
        .declareProperty(assignmentProp)
        .declareProperty(assignmentEpochProp);
  }

  public CoordinationErrorCode getErrorCode() {
    return errorCodeProp.getValue();
  }

  public HeartbeatResponse setErrorCode(final CoordinationErrorCode errorCode) {
    errorCodeProp.setValue(errorCode);
    return this;
  }

  public String getMemberId() {
    return bufferAsString(memberIdProp.getValue());
  }

  public HeartbeatResponse setMemberId(final String memberId) {
    memberIdProp.setValue(memberId);
    return this;
  }

  public long getMemberEpoch() {
    return memberEpochProp.getValue();
  }

  public HeartbeatResponse setMemberEpoch(final long epoch) {
    memberEpochProp.setValue(epoch);
    return this;
  }

  public List<Integer> getAssign() {
    final var assign = new ArrayList<Integer>();
    assignProp.forEach(e -> assign.add(e.getValue()));
    return assign;
  }

  public HeartbeatResponse setAssign(final List<Integer> assign) {
    assignProp.reset();
    if (assign != null && !assign.isEmpty()) {
      assign.forEach(p -> assignProp.add().setValue(p));
    }
    return this;
  }

  public List<Integer> getRevoke() {
    final var revoke = new ArrayList<Integer>();
    revokeProp.forEach(e -> revoke.add(e.getValue()));
    return revoke;
  }

  public HeartbeatResponse setRevoke(final List<Integer> revoke) {
    revokeProp.reset();
    if (revoke != null && !revoke.isEmpty()) {
      revoke.forEach(p -> revokeProp.add().setValue(p));
    }
    return this;
  }

  public List<Integer> getAssignment() {
    final var assignment = new ArrayList<Integer>();
    assignmentProp.forEach(e -> assignment.add(e.getValue()));
    return assignment;
  }

  public HeartbeatResponse setAssignment(final List<Integer> assignment) {
    assignmentProp.reset();
    if (assignment != null && !assignment.isEmpty()) {
      assignment.forEach(p -> assignmentProp.add().setValue(p));
    }
    return this;
  }

  public long getAssignmentEpoch() {
    return assignmentEpochProp.getValue();
  }

  public HeartbeatResponse setAssignmentEpoch(final long assignmentEpoch) {
    assignmentEpochProp.setValue(assignmentEpoch);
    return this;
  }
}
