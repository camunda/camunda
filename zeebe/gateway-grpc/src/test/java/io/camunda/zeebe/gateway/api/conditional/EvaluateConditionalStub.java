/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.api.conditional;

import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient.RequestStub;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerEvaluateConditionalRequest;
import io.camunda.zeebe.protocol.impl.record.value.conditional.ConditionalEvaluationRecord;

public class EvaluateConditionalStub
    implements RequestStub<
        BrokerEvaluateConditionalRequest, BrokerResponse<ConditionalEvaluationRecord>> {

  @Override
  public void registerWith(final StubbedBrokerClient gateway) {
    gateway.registerHandler(BrokerEvaluateConditionalRequest.class, this);
  }

  @Override
  public BrokerResponse<ConditionalEvaluationRecord> handle(
      final BrokerEvaluateConditionalRequest request) throws Exception {
    final var response = new ConditionalEvaluationRecord();
    response.setTenantId(request.getRequestWriter().getTenantId());
    return new BrokerResponse<>(response, request.getPartitionId(), request.getKey());
  }
}
