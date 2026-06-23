/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.request.coordination;

import io.camunda.eventbridge.protocol.CoordinateRequestType;
import io.camunda.eventbridge.protocol.request.coordination.ReassignTopicRequest;
import io.camunda.eventbridge.protocol.request.coordination.ReassignTopicResponse;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public class BrokerReassignTopicRequest
    extends BrokerExecuteCoordinateRequest<ReassignTopicResponse> {

  private final ReassignTopicRequest request = new ReassignTopicRequest();
  private final ReassignTopicResponse response = new ReassignTopicResponse();

  public BrokerReassignTopicRequest() {
    super(CoordinateRequestType.REASSIGN_TOPIC, METADATA_ROUTING_GROUP);
  }

  public BrokerReassignTopicRequest wrapRequest(final ReassignTopicRequest req) {
    request.setName(req.getName()).setReplicationFactor(req.getReplicationFactor());
    return this;
  }

  @Override
  public BufferWriter getRequestWriter() {
    return request;
  }

  @Override
  protected ReassignTopicResponse toResponseDto(final DirectBuffer buffer) {
    response.wrap(buffer);
    return response;
  }

  @Override
  public String getType() {
    return "ReassignTopic";
  }
}
