/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerExpressionEvaluationRequest;
import io.camunda.zeebe.protocol.impl.record.value.expression.ExpressionRecord;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class ExpressionServices extends ApiServices<ExpressionServices> {

  public ExpressionServices(
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

  public CompletableFuture<ExpressionRecord> evaluateExpression(
      final ExpressionEvaluationRequest request, final CamundaAuthentication authentication) {
    return sendBrokerRequest(
        new BrokerExpressionEvaluationRequest()
            .setExpression(request.expression())
            .setVariables(request.variables())
            .setTenantId(request.tenantId()),
        authentication);
  }

  public record ExpressionEvaluationRequest(
      String expression, String tenantId, Map<String, Object> variables) {}
}
