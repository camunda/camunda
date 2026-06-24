/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.request.coordination;

import io.camunda.eventbridge.protocol.CoordinateRequestType;
import io.camunda.eventbridge.protocol.request.coordination.CommitOffsetRequest;
import io.camunda.eventbridge.protocol.request.coordination.CommitOffsetResponse;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public class BrokerCommitRequest extends BrokerExecuteCoordinateRequest<CommitOffsetResponse> {

  private final CommitOffsetRequest request = new CommitOffsetRequest();
  private final CommitOffsetResponse response = new CommitOffsetResponse();

  public BrokerCommitRequest() {
    super(CoordinateRequestType.COMMIT);
  }

  public BrokerCommitRequest wrapRequest(final CommitOffsetRequest req) {
    request
        .setGroupId(req.getGroupId())
        .setTopic(req.getTopic())
        .setMemberId(req.getMemberId())
        .setMemberEpoch(req.getMemberEpoch())
        .setPartitionId(req.getPartitionId())
        .setPosition(req.getPosition());
    return this;
  }

  @Override
  public BufferWriter getRequestWriter() {
    return request;
  }

  @Override
  protected CommitOffsetResponse toResponseDto(final DirectBuffer buffer) {
    response.wrap(buffer);
    return response;
  }

  @Override
  public String getType() {
    return "Commit";
  }
}
