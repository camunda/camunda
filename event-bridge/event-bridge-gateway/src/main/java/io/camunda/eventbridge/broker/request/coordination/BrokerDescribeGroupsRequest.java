/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.request.coordination;

import io.camunda.eventbridge.protocol.CoordinateRequestType;
import io.camunda.eventbridge.protocol.request.coordination.DescribeGroupsRequest;
import io.camunda.eventbridge.protocol.request.coordination.DescribeGroupsResponse;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public class BrokerDescribeGroupsRequest
    extends BrokerExecuteCoordinateRequest<DescribeGroupsResponse> {

  private final DescribeGroupsRequest request = new DescribeGroupsRequest();
  private final DescribeGroupsResponse response = new DescribeGroupsResponse();

  public BrokerDescribeGroupsRequest() {
    super(CoordinateRequestType.DESCRIBE_GROUPS);
  }

  public BrokerDescribeGroupsRequest wrapRequest(final DescribeGroupsRequest req) {
    request.setGroupId(req.getGroupId());
    return this;
  }

  @Override
  public BufferWriter getRequestWriter() {
    return request;
  }

  @Override
  protected DescribeGroupsResponse toResponseDto(final DirectBuffer buffer) {
    response.wrap(buffer);
    return response;
  }

  @Override
  public String getType() {
    return "DescribeGroups";
  }
}
