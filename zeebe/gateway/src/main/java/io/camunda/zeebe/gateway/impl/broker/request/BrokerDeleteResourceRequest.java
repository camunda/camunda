/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
import io.camunda.zeebe.protocol.record.value.ResourceType;
import org.agrona.DirectBuffer;

public final class BrokerDeleteResourceRequest
    extends BrokerExecuteCommand<ResourceDeletionRecord> {

  private final ResourceDeletionRecord requestDto = new ResourceDeletionRecord();

  public BrokerDeleteResourceRequest() {
    super(ValueType.RESOURCE_DELETION, ResourceDeletionIntent.DELETE);
  }

  public BrokerDeleteResourceRequest setResourceKey(final long resourceKey) {
    requestDto.setResourceKey(resourceKey);
    request.setKey(resourceKey);
    return this;
  }

  public BrokerDeleteResourceRequest setDeleteHistory(final boolean deleteHistory) {
    requestDto.setDeleteHistory(deleteHistory);
    return this;
  }

  public BrokerDeleteResourceRequest setResourceType(final ResourceType resourceType) {
    requestDto.setResourceType(resourceType);
    return this;
  }

  public BrokerDeleteResourceRequest setTenantId(final String tenantId) {
    requestDto.setTenantId(tenantId);
    return this;
  }

  public BrokerDeleteResourceRequest setResourceId(final String resourceId) {
    requestDto.setResourceId(resourceId);
    return this;
  }

  @Override
  public ResourceDeletionRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected ResourceDeletionRecord toResponseDto(final DirectBuffer buffer) {
    final ResourceDeletionRecord responseDto = new ResourceDeletionRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
