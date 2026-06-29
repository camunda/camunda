/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.request.coordination;

import io.camunda.eventbridge.consumergroups.record.MembershipRecord;
import io.camunda.eventbridge.protocol.CoordinateRequestType;
import io.camunda.eventbridge.protocol.request.coordination.LeaveGroupRequest;
import io.camunda.eventbridge.protocol.request.coordination.LeaveGroupResponse;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public class BrokerLeaveGroupRequest extends BrokerExecuteCoordinateRequest<LeaveGroupResponse> {

  // The wire payload is the log command record itself (written straight through, no mapping).
  private final MembershipRecord request = new MembershipRecord();
  private final LeaveGroupResponse response = new LeaveGroupResponse();

  public BrokerLeaveGroupRequest() {
    super(CoordinateRequestType.LEAVE_GROUP);
  }

  public BrokerLeaveGroupRequest wrapRequest(final LeaveGroupRequest req) {
    request
        .setGroupId(req.getGroupId())
        .setMemberId(req.getMemberId())
        .setMemberEpoch(req.getMemberEpoch());
    return this;
  }

  @Override
  public BufferWriter getRequestWriter() {
    return request;
  }

  @Override
  protected LeaveGroupResponse toResponseDto(final DirectBuffer buffer) {
    response.wrap(buffer);
    return response;
  }

  @Override
  public String getType() {
    return "LeaveGroup";
  }
}
