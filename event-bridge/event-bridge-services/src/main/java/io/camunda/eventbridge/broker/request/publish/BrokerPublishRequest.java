/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.request.publish;

import io.camunda.eventbridge.protocol.request.PublishBatchResponse;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public class BrokerPublishRequest extends BrokerExecutePublishRequest<PublishBatchResponse> {

  private final PublishBatchResponse response = new PublishBatchResponse();

  @Override
  public BufferWriter getRequestWriter() {
    return null;
  }

  @Override
  protected PublishBatchResponse toResponseDto(final DirectBuffer buffer) {
    wrapPublishResponse(response);
    return response;
  }

  @Override
  public String getType() {
    return "Publish";
  }
}
