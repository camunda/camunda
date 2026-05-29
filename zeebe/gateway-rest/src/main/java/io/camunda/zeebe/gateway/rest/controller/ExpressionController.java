/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.protocol.model.ExpressionEvaluationRequest;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.config.MultiTenancyConfiguration;
import io.camunda.service.ExpressionServices;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/expression")
public class ExpressionController {

  private final ServiceRegistry registry;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final MultiTenancyConfiguration multiTenancyCfg;

  public ExpressionController(
      final ServiceRegistry registry,
      final CamundaAuthenticationProvider authenticationProvider,
      final MultiTenancyConfiguration multiTenancyCfg) {
    this.registry = registry;
    this.authenticationProvider = authenticationProvider;
    this.multiTenancyCfg = multiTenancyCfg;
  }

  @CamundaPostMapping(path = "/evaluation")
  public CompletableFuture<ResponseEntity<Object>> evaluateExpression(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final ExpressionEvaluationRequest request) {
    return RequestMapper.toExpressionEvaluationRequest(
            request.getExpression(),
            request.getTenantId(),
            request.getScopeKey(),
            request.getVariables(),
            multiTenancyCfg.isChecksEnabled())
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            r -> evaluateExpression(r, physicalTenantId));
  }

  private CompletableFuture<ResponseEntity<Object>> evaluateExpression(
      final ExpressionServices.ExpressionEvaluationRequest request, final String physicalTenantId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () ->
            registry
                .expressionServices(physicalTenantId)
                .evaluateExpression(request, authentication),
        ResponseMapper::toExpressionEvaluationResult,
        HttpStatus.OK);
  }
}
