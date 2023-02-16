/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.api.resource;

import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient.RequestStub;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerDeleteResourceRequest;
import io.camunda.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;

public class DeleteResourceStub
    implements RequestStub<BrokerDeleteResourceRequest, BrokerResponse<ResourceDeletionRecord>> {

  public static final ResourceDeletionRecord RESOURCE_DELETION_RECORD =
      new ResourceDeletionRecord().setResourceKey(1L);

  @Override
  public void registerWith(final StubbedBrokerClient gateway) {
    gateway.registerHandler(BrokerDeleteResourceRequest.class, this);
  }

  @Override
  public BrokerResponse<ResourceDeletionRecord> handle(final BrokerDeleteResourceRequest request)
      throws Exception {
    return new BrokerResponse<>(
        RESOURCE_DELETION_RECORD, request.getPartitionId(), request.getKey());
  }
}
