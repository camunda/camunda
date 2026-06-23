/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.request.coordination;

import io.camunda.eventbridge.protocol.CoordinateRequestType;
import io.camunda.eventbridge.protocol.request.coordination.DeleteTopicRequest;
import io.camunda.eventbridge.protocol.request.coordination.DeleteTopicResponse;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public class BrokerDeleteTopicRequest extends BrokerExecuteCoordinateRequest<DeleteTopicResponse> {

  private final DeleteTopicRequest request = new DeleteTopicRequest();
  private final DeleteTopicResponse response = new DeleteTopicResponse();

  public BrokerDeleteTopicRequest() {
    super(CoordinateRequestType.DELETE_TOPIC);
  }

  public BrokerDeleteTopicRequest wrapRequest(final DeleteTopicRequest req) {
    request.setName(req.getName());
    return this;
  }

  @Override
  public BufferWriter getRequestWriter() {
    return request;
  }

  @Override
  protected DeleteTopicResponse toResponseDto(final DirectBuffer buffer) {
    response.wrap(buffer);
    return response;
  }

  @Override
  public String getType() {
    return "DeleteTopic";
  }
}
