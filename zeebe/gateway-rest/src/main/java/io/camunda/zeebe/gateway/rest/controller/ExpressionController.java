/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.ExpressionServices;
import io.camunda.zeebe.gateway.protocol.rest.ExpressionEvaluationRequest;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.mapper.RequestMapper;
import io.camunda.zeebe.gateway.rest.mapper.RequestMapper.ExpressionEvaluationRequestDto;
import io.camunda.zeebe.gateway.rest.mapper.ResponseMapper;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/expressions")
public class ExpressionController {

  private final ExpressionServices expressionServices;
  private final MultiTenancyConfiguration multiTenancyCfg;
  private final CamundaAuthenticationProvider authenticationProvider;

  public ExpressionController(
      final ExpressionServices expressionServices,
      final MultiTenancyConfiguration multiTenancyCfg,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.expressionServices = expressionServices;
    this.multiTenancyCfg = multiTenancyCfg;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping(path = "/evaluation")
  public CompletableFuture<ResponseEntity<Object>> evaluateExpression(
      @RequestBody final ExpressionEvaluationRequest evaluateExpressionRequest) {
    return RequestMapper.toExpressionEvaluationRequest(
            evaluateExpressionRequest, multiTenancyCfg.isChecksEnabled())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::evaluateExpression);
  }

  private CompletableFuture<ResponseEntity<Object>> evaluateExpression(
      final ExpressionEvaluationRequestDto request) {
    return RequestMapper.executeServiceMethod(
        () ->
            expressionServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .evaluateExpression(
                    request.expression(),
                    request.scopeType(),
                    request.processInstanceKey(),
                    request.context(),
                    request.tenantId()),
        ResponseMapper::toExpressionEvaluationResponse);
  }
}
