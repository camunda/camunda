/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerEvaluateConditionalRequest;
import io.camunda.zeebe.protocol.impl.record.value.conditional.ConditionalEvaluationRecord;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ConditionalServices extends ApiServices<ConditionalServices> {

  public ConditionalServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  public CompletableFuture<BrokerResponse<ConditionalEvaluationRecord>> evaluateConditional(
      final EvaluateConditionalRequest request,
      final CamundaAuthentication authentication,
      final String physicalTenantId) {
    final var brokerRequest =
        new BrokerEvaluateConditionalRequest()
            .setProcessDefinitionKey(request.processDefinitionKey())
            .setTenantId(request.tenantId())
            .setVariables(getDocumentOrEmpty(request.variables()));

    return sendBrokerRequestWithFullResponse(brokerRequest, authentication);
  }

  public record EvaluateConditionalRequest(
      String tenantId, Long processDefinitionKey, Map<String, Object> variables) {}
}
