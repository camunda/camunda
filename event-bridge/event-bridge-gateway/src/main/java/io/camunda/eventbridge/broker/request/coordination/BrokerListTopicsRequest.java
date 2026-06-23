/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.request.coordination;

import io.camunda.eventbridge.protocol.CoordinateRequestType;
import io.camunda.eventbridge.protocol.request.coordination.ListTopicsRequest;
import io.camunda.eventbridge.protocol.request.coordination.ListTopicsResponse;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public class BrokerListTopicsRequest extends BrokerExecuteCoordinateRequest<ListTopicsResponse> {

  private final ListTopicsRequest request = new ListTopicsRequest();
  private final ListTopicsResponse response = new ListTopicsResponse();

  public BrokerListTopicsRequest() {
    super(CoordinateRequestType.LIST_TOPICS);
  }

  @Override
  public BufferWriter getRequestWriter() {
    return request;
  }

  @Override
  protected ListTopicsResponse toResponseDto(final DirectBuffer buffer) {
    response.wrap(buffer);
    return response;
  }

  @Override
  public String getType() {
    return "ListTopics";
  }
}
