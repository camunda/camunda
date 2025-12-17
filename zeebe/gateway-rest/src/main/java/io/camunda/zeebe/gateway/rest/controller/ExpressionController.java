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
import io.camunda.zeebe.gateway.rest.mapper.ResponseMapper;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/expression")
public class ExpressionController {

  private final ExpressionServices expressionServices;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final MultiTenancyConfiguration multiTenancyCfg;

  public ExpressionController(
      final ExpressionServices expressionServices,
      final CamundaAuthenticationProvider authenticationProvider,
      final MultiTenancyConfiguration multiTenancyCfg) {
    this.expressionServices = expressionServices;
    this.authenticationProvider = authenticationProvider;
    this.multiTenancyCfg = multiTenancyCfg;
  }

  @CamundaPostMapping(path = "/evaluation")
  public CompletableFuture<ResponseEntity<Object>> evaluateExpression(
      @RequestBody final ExpressionEvaluationRequest request) {
    return RequestMapper.toExpressionEvaluationRequest(
            request.getExpression(), request.getTenantId(), multiTenancyCfg.isChecksEnabled())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::evaluateExpression);
  }

  private CompletableFuture<ResponseEntity<Object>> evaluateExpression(
      final ExpressionServices.ExpressionEvaluationRequest request) {
    return RequestMapper.executeServiceMethod(
        () ->
            expressionServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .evaluateExpression(request),
        ResponseMapper::toExpressionEvaluationResult);
  }
}
