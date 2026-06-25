/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.request.coordination;

import io.camunda.eventbridge.protocol.CoordinateRequestType;
import io.camunda.eventbridge.protocol.request.coordination.OffsetFetchRequest;
import io.camunda.eventbridge.protocol.request.coordination.OffsetFetchResponse;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public class BrokerOffsetFetchRequest extends BrokerExecuteCoordinateRequest<OffsetFetchResponse> {

  private final OffsetFetchRequest request = new OffsetFetchRequest();
  private final OffsetFetchResponse response = new OffsetFetchResponse();

  public BrokerOffsetFetchRequest() {
    super(CoordinateRequestType.OFFSET_FETCH);
  }

  public BrokerOffsetFetchRequest wrapRequest(final OffsetFetchRequest req) {
    request.setGroupId(req.getGroupId());
    req.getPartitions().forEach(request::addPartition);
    return this;
  }

  @Override
  public BufferWriter getRequestWriter() {
    return request;
  }

  @Override
  protected OffsetFetchResponse toResponseDto(final DirectBuffer buffer) {
    response.wrap(buffer);
    return response;
  }

  @Override
  public String getType() {
    return "OffsetFetch";
  }
}
