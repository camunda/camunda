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
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.config.MultiTenancyConfiguration;
import io.camunda.service.ConditionalServices;
import io.camunda.service.ConditionalServices.EvaluateConditionalRequest;
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
@RequestMapping("/v2/conditionals")
public class ConditionalController {

  private final ServiceRegistry registry;
  private final MultiTenancyConfiguration multiTenancyCfg;
  private final CamundaAuthenticationProvider authenticationProvider;

  public ConditionalController(
      final ServiceRegistry registry,
      final MultiTenancyConfiguration multiTenancyCfg,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.registry = registry;
    this.multiTenancyCfg = multiTenancyCfg;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping(path = "/evaluation")
  public CompletableFuture<ResponseEntity<Object>> evaluate(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final ConditionalEvaluationInstruction request) {
    return RequestMapper.toEvaluateConditionalRequest(request, multiTenancyCfg.isChecksEnabled())
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            mapped ->
                evaluateConditionalEvent(registry.conditionalServices(physicalTenantId), mapped));
  }

  private CompletableFuture<ResponseEntity<Object>> evaluateConditionalEvent(
      final ConditionalServices conditionalServices,
      final EvaluateConditionalRequest createRequest) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> conditionalServices.evaluateConditional(createRequest, authentication),
        ResponseMapper::toConditionalEvaluationResponse,
        HttpStatus.OK);
  }
}
