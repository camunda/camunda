/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.service.DecisionDefinitionServices;
import io.camunda.zeebe.gateway.impl.configuration.MultiTenancyCfg;
import io.camunda.zeebe.gateway.protocol.rest.EvaluateDecisionRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RequestMapper.DecisionEvaluationRequest;
import io.camunda.zeebe.gateway.rest.ResponseMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/decision-definitions")
public class DecisionDefinitionController {

  private final DecisionDefinitionServices decisionServices;
  private final MultiTenancyCfg multiTenancyCfg;

  public DecisionDefinitionController(
      final DecisionDefinitionServices decisionServices, final MultiTenancyCfg multiTenancyCfg) {
    this.decisionServices = decisionServices;
    this.multiTenancyCfg = multiTenancyCfg;
  }

  @PostMapping(
      path = "/evaluation",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> evaluateDecision(
      @RequestBody final EvaluateDecisionRequest evaluateDecisionRequest) {
    return RequestMapper.toEvaluateDecisionRequest(
            evaluateDecisionRequest, multiTenancyCfg.isEnabled())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::evaluateDecision);
  }

  private CompletableFuture<ResponseEntity<Object>> evaluateDecision(
      final DecisionEvaluationRequest request) {
    return RequestMapper.executeServiceMethod(
        () ->
            decisionServices
                .withAuthentication(RequestMapper.getAuthentication())
                .evaluateDecision(
                    request.decisionId(),
                    request.decisionKey(),
                    request.variables(),
                    request.tenantId()),
        ResponseMapper::toEvaluateDecisionResponse);
  }
}
