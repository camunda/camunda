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
import io.camunda.eventbridge.protocol.request.coordination.JoinGroupRequest;
import io.camunda.eventbridge.protocol.request.coordination.JoinGroupResponse;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public class BrokerJoinGroupRequest extends BrokerExecuteCoordinateRequest<JoinGroupResponse> {

  // The wire payload is the log command record itself, so the coordinator writes it straight to the
  // stream without a request->record mapping; the member id is assigned server-side.
  private final MembershipRecord request = new MembershipRecord();
  private final JoinGroupResponse response = new JoinGroupResponse();

  public BrokerJoinGroupRequest() {
    super(CoordinateRequestType.JOIN_GROUP);
  }

  public BrokerJoinGroupRequest wrapRequest(final JoinGroupRequest req) {
    request
        .setGroupId(req.getGroupId())
        .setTopics(req.getTopics())
        .setInstanceId(req.getInstanceId());
    return this;
  }

  @Override
  public BufferWriter getRequestWriter() {
    return request;
  }

  @Override
  protected JoinGroupResponse toResponseDto(final DirectBuffer buffer) {
    response.wrap(buffer);
    return response;
  }

  @Override
  public String getType() {
    return "JoinGroup";
  }
}
