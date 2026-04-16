/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.adapter;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.protocol.model.ExpressionEvaluationRequest;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.ExpressionServices;
import io.camunda.zeebe.gateway.rest.controller.generated.ExpressionServiceAdapter;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DefaultExpressionServiceAdapter implements ExpressionServiceAdapter {

  private final ExpressionServices expressionServices;
  private final MultiTenancyConfiguration multiTenancyCfg;

  public DefaultExpressionServiceAdapter(
      final ExpressionServices expressionServices,
      final MultiTenancyConfiguration multiTenancyCfg) {
    this.expressionServices = expressionServices;
    this.multiTenancyCfg = multiTenancyCfg;
  }

  @Override
  public ResponseEntity<Object> evaluateExpression(
      final ExpressionEvaluationRequest requestStrict, final CamundaAuthentication authentication) {
    return RequestMapper.toExpressionEvaluationRequest(
            requestStrict.getExpression(),
            requestStrict.getTenantId().orElse(null),
            requestStrict.getVariables().orElse(null),
            multiTenancyCfg.isChecksEnabled())
        .fold(
            RestErrorMapper::mapProblemToResponse,
            mapped ->
                RequestExecutor.executeSync(
                    () -> expressionServices.evaluateExpression(mapped, authentication),
                    ResponseMapper::toExpressionEvaluationResult,
                    HttpStatus.OK));
  }
}
