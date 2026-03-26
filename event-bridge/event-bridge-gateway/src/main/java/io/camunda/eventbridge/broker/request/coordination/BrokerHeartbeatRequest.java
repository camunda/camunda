/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.request.coordination;

import io.camunda.eventbridge.protocol.CoordinateRequestType;
import io.camunda.eventbridge.protocol.request.coordination.HeartbeatRequest;
import io.camunda.eventbridge.protocol.request.coordination.HeartbeatResponse;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public class BrokerHeartbeatRequest extends BrokerExecuteCoordinateRequest<HeartbeatResponse> {

  private final HeartbeatRequest request = new HeartbeatRequest();
  private final HeartbeatResponse response = new HeartbeatResponse();

  public BrokerHeartbeatRequest() {
    super(CoordinateRequestType.HEARTBEAT);
  }

  public BrokerHeartbeatRequest wrapRequest(final HeartbeatRequest req) {
    request
        .setGroupId(req.getGroupId())
        .setMemberId(req.getMemberId())
        .setMemberEpoch(req.getMemberEpoch())
        .setOwnedPartitions(req.getOwnedPartitions());
    return this;
  }

  @Override
  public BufferWriter getRequestWriter() {
    return request;
  }

  @Override
  protected HeartbeatResponse toResponseDto(final DirectBuffer buffer) {
    response.wrap(buffer);
    return response;
  }

  @Override
  public String getType() {
    return "Heartbeat";
  }
}
