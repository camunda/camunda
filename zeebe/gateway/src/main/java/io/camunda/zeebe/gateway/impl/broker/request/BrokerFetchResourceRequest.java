/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import org.agrona.DirectBuffer;

public final class BrokerFetchResourceRequest extends BrokerExecuteCommand<ResourceRecord> {

  private final ResourceRecord requestDto = new ResourceRecord();

  public BrokerFetchResourceRequest() {
    super(ValueType.RESOURCE, ResourceIntent.FETCH);
  }

  public BrokerFetchResourceRequest setResourceKey(final long resourceKey) {
    requestDto.setResourceKey(resourceKey);
    request.setKey(resourceKey);
    return this;
  }

  @Override
  public ResourceRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected ResourceRecord toResponseDto(final DirectBuffer buffer) {
    final ResourceRecord responseDto = new ResourceRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
