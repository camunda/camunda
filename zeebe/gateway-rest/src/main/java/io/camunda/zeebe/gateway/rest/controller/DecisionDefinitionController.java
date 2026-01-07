/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.model.mapper.RequestMapper;
import io.camunda.gateway.model.mapper.RequestMapper.DecisionEvaluationRequest;
import io.camunda.gateway.model.mapper.ResponseMapper;
import io.camunda.gateway.model.mapper.search.SearchQueryRequestMapper;
import io.camunda.gateway.model.mapper.search.SearchQueryResponseMapper;
import io.camunda.gateway.protocol.model.DecisionDefinitionResult;
import io.camunda.gateway.protocol.model.DecisionDefinitionSearchQuery;
import io.camunda.gateway.protocol.model.DecisionDefinitionSearchQueryResult;
import io.camunda.gateway.protocol.model.DecisionEvaluationInstruction;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.DecisionDefinitionServices;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/decision-definitions")
public class DecisionDefinitionController {

  private final DecisionDefinitionServices decisionDefinitionServices;
  private final MultiTenancyConfiguration multiTenancyCfg;
  private final CamundaAuthenticationProvider authenticationProvider;

  public DecisionDefinitionController(
      final DecisionDefinitionServices decisionServices,
      final MultiTenancyConfiguration multiTenancyCfg,
      final CamundaAuthenticationProvider authenticationProvider) {
    decisionDefinitionServices = decisionServices;
    this.multiTenancyCfg = multiTenancyCfg;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping(path = "/evaluation")
  public CompletableFuture<ResponseEntity<Object>> evaluateDecision(
      @RequestBody final DecisionEvaluationInstruction evaluateDecisionRequest) {
    return RequestMapper.toEvaluateDecisionRequest(
            evaluateDecisionRequest, multiTenancyCfg.isChecksEnabled())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::evaluateDecision);
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  public ResponseEntity<DecisionDefinitionSearchQueryResult> searchDecisionDefinitions(
      @RequestBody(required = false) final DecisionDefinitionSearchQuery query) {
    return SearchQueryRequestMapper.toDecisionDefinitionQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{decisionDefinitionKey}")
  public ResponseEntity<DecisionDefinitionResult> getDecisionDefinitionByKey(
      @PathVariable("decisionDefinitionKey") final long decisionDefinitionKey) {
    try {
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toDecisionDefinition(
              decisionDefinitionServices
                  .withAuthentication(authenticationProvider.getCamundaAuthentication())
                  .getByKey(decisionDefinitionKey)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(
      path = "/{decisionDefinitionKey}/xml",
      produces = {MediaType.TEXT_XML_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<String> getDecisionDefinitionXml(
      @PathVariable("decisionDefinitionKey") final long decisionDefinitionKey) {
    try {
      return ResponseEntity.ok()
          .contentType(new MediaType(MediaType.TEXT_XML, StandardCharsets.UTF_8))
          .body(
              decisionDefinitionServices
                  .withAuthentication(authenticationProvider.getCamundaAuthentication())
                  .getDecisionDefinitionXml(decisionDefinitionKey));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<DecisionDefinitionSearchQueryResult> search(
      final DecisionDefinitionQuery query) {
    try {
      final var result =
          decisionDefinitionServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(query);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toDecisionDefinitionSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private CompletableFuture<ResponseEntity<Object>> evaluateDecision(
      final DecisionEvaluationRequest request) {
    return RequestExecutor.executeServiceMethod(
        () ->
            decisionDefinitionServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .evaluateDecision(
                    request.decisionId(),
                    request.decisionKey(),
                    request.variables(),
                    request.tenantId()),
        ResponseMapper::toEvaluateDecisionResponse,
        HttpStatus.OK);
  }
}
