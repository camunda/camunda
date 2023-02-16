/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public final class BrokerDeleteResourceRequest
    extends BrokerExecuteCommand<ResourceDeletionRecord> {

  private final ResourceDeletionRecord requestdto = new ResourceDeletionRecord();

  public BrokerDeleteResourceRequest() {
    super(ValueType.RESOURCE_DELETION, ResourceDeletionIntent.DELETE);
  }

  public BrokerDeleteResourceRequest setResourceKey(final long resourceKey) {
    requestdto.setResourceKey(resourceKey);
    request.setKey(resourceKey);
    return this;
  }

  @Override
  public BufferWriter getRequestWriter() {
    return requestdto;
  }

  @Override
  protected ResourceDeletionRecord toResponseDto(final DirectBuffer buffer) {
    final ResourceDeletionRecord responseDto = new ResourceDeletionRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
