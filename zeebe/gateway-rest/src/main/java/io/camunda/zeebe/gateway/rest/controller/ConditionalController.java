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
import io.camunda.gateway.protocol.model.ConditionalEvaluationInstruction;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.ConditionalServices;
import io.camunda.service.ConditionalServices.EvaluateConditionalRequest;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenant;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/conditionals")
public class ConditionalController {

  private final ConditionalServices conditionalServices;
  private final MultiTenancyConfiguration multiTenancyCfg;
  private final CamundaAuthenticationProvider authenticationProvider;

  public ConditionalController(
      final ConditionalServices conditionalServices,
      final MultiTenancyConfiguration multiTenancyCfg,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.conditionalServices = conditionalServices;
    this.multiTenancyCfg = multiTenancyCfg;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping(path = "/evaluation")
  public CompletableFuture<ResponseEntity<Object>> evaluate(
      @RequestBody final ConditionalEvaluationInstruction request,
      @PhysicalTenant final String physicalTenantId) {
    return RequestMapper.toEvaluateConditionalRequest(request, multiTenancyCfg.isChecksEnabled())
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> evaluateConditionalEvent(req, physicalTenantId));
  }

  private CompletableFuture<ResponseEntity<Object>> evaluateConditionalEvent(
      final EvaluateConditionalRequest createRequest, final String physicalTenantId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () ->
            conditionalServices.evaluateConditional(
                createRequest, authentication, physicalTenantId),
        ResponseMapper::toConditionalEvaluationResponse,
        HttpStatus.OK);
  }
}
