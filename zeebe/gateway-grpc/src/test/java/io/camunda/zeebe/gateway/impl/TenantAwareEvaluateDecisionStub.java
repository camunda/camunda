/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl;

import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient.RequestStub;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerEvaluateDecisionRequest;
import io.camunda.zeebe.protocol.impl.record.value.decision.DecisionEvaluationRecord;

public class TenantAwareEvaluateDecisionStub
    implements RequestStub<
        BrokerEvaluateDecisionRequest, BrokerResponse<DecisionEvaluationRecord>> {

  @Override
  public void registerWith(final StubbedBrokerClient gateway) {
    gateway.registerHandler(BrokerEvaluateDecisionRequest.class, this);
  }

  @Override
  public BrokerResponse<DecisionEvaluationRecord> handle(
      final BrokerEvaluateDecisionRequest request) throws Exception {
    final var response = new DecisionEvaluationRecord();
    response.setTenantId(request.getRequestWriter().getTenantId());
    return new BrokerResponse<>(response, request.getPartitionId(), request.getKey());
  }
}
