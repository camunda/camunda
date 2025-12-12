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
import io.camunda.zeebe.gateway.impl.broker.request.BrokerEvaluateConditionalRequest;
import io.camunda.zeebe.protocol.impl.record.value.conditional.ConditionalEvaluationRecord;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ConditionalServices extends ApiServices<ConditionalServices> {

  public ConditionalServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final CamundaAuthentication authentication,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        brokerClient,
        securityContextProvider,
        authentication,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  @Override
  public ConditionalServices withAuthentication(final CamundaAuthentication authentication) {
    return new ConditionalServices(
        brokerClient,
        securityContextProvider,
        authentication,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  public CompletableFuture<ConditionalEvaluationRecord> evaluateConditional(
      final EvaluateConditionalRequest request) {
    final var brokerRequest =
        new BrokerEvaluateConditionalRequest()
            .setProcessDefinitionKey(request.processDefinitionKey())
            .setTenantId(request.tenantId())
            .setVariables(getDocumentOrEmpty(request.variables()));

    return sendBrokerRequest(brokerRequest);
  }

  public record EvaluateConditionalRequest(
      String tenantId, Long processDefinitionKey, Map<String, Object> variables) {}
}
