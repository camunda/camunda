/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.request.coordination;

import io.camunda.eventbridge.protocol.CoordinateRequestType;
import io.camunda.eventbridge.protocol.request.coordination.CreateTopicRequest;
import io.camunda.eventbridge.protocol.request.coordination.CreateTopicResponse;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public class BrokerCreateTopicRequest extends BrokerExecuteCoordinateRequest<CreateTopicResponse> {

  private final CreateTopicRequest request = new CreateTopicRequest();
  private final CreateTopicResponse response = new CreateTopicResponse();

  public BrokerCreateTopicRequest() {
    super(CoordinateRequestType.CREATE_TOPIC, METADATA_ROUTING_GROUP);
  }

  public BrokerCreateTopicRequest wrapRequest(final CreateTopicRequest req) {
    request
        .setName(req.getName())
        .setPartitionCount(req.getPartitionCount())
        .setReplicationFactor(req.getReplicationFactor());
    return this;
  }

  @Override
  public BufferWriter getRequestWriter() {
    return request;
  }

  @Override
  protected CreateTopicResponse toResponseDto(final DirectBuffer buffer) {
    response.wrap(buffer);
    return response;
  }

  @Override
  public String getType() {
    return "CreateTopic";
  }
}
