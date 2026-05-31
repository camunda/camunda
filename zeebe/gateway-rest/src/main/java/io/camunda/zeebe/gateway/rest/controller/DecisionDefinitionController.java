/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.RequestMapper.DecisionEvaluationRequest;
import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.protocol.model.DecisionDefinitionResult;
import io.camunda.gateway.protocol.model.DecisionDefinitionSearchQuery;
import io.camunda.gateway.protocol.model.DecisionDefinitionSearchQueryResult;
import io.camunda.gateway.protocol.model.DecisionEvaluationInstruction;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.config.MultiTenancyConfiguration;
import io.camunda.service.DecisionDefinitionServices;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
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

  private final ServiceRegistry serviceRegistry;
  private final MultiTenancyConfiguration multiTenancyCfg;
  private final CamundaAuthenticationProvider authenticationProvider;

  public DecisionDefinitionController(
      final ServiceRegistry serviceRegistry,
      final MultiTenancyConfiguration multiTenancyCfg,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceRegistry = serviceRegistry;
    this.multiTenancyCfg = multiTenancyCfg;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping(path = "/evaluation")
  public CompletableFuture<ResponseEntity<Object>> evaluateDecision(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final DecisionEvaluationInstruction evaluateDecisionRequest) {
    return RequestMapper.toEvaluateDecisionRequest(
            evaluateDecisionRequest, multiTenancyCfg.isChecksEnabled())
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            mapped ->
                evaluateDecision(
                    serviceRegistry.decisionDefinitionServices(physicalTenantId), mapped));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  public ResponseEntity<DecisionDefinitionSearchQueryResult> searchDecisionDefinitions(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody(required = false) final DecisionDefinitionSearchQuery query) {
    return SearchQueryRequestMapper.toDecisionDefinitionQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            q -> search(serviceRegistry.decisionDefinitionServices(physicalTenantId), q));
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{decisionDefinitionKey}")
  public ResponseEntity<DecisionDefinitionResult> getDecisionDefinitionByKey(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("decisionDefinitionKey") final long decisionDefinitionKey) {
    try {
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toDecisionDefinition(
              serviceRegistry
                  .decisionDefinitionServices(physicalTenantId)
                  .getByKey(
                      decisionDefinitionKey, authenticationProvider.getCamundaAuthentication())));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(
      path = "/{decisionDefinitionKey}/xml",
      produces = {MediaType.TEXT_XML_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<String> getDecisionDefinitionXml(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("decisionDefinitionKey") final long decisionDefinitionKey) {
    try {
      return ResponseEntity.ok()
          .contentType(new MediaType(MediaType.TEXT_XML, StandardCharsets.UTF_8))
          .body(
              serviceRegistry
                  .decisionDefinitionServices(physicalTenantId)
                  .getDecisionDefinitionXml(
                      decisionDefinitionKey, authenticationProvider.getCamundaAuthentication()));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<DecisionDefinitionSearchQueryResult> search(
      final DecisionDefinitionServices decisionDefinitionServices,
      final DecisionDefinitionQuery query) {
    try {
      final var result =
          decisionDefinitionServices.search(
              query, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toDecisionDefinitionSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private CompletableFuture<ResponseEntity<Object>> evaluateDecision(
      final DecisionDefinitionServices decisionDefinitionServices,
      final DecisionEvaluationRequest request) {
    return RequestExecutor.executeServiceMethod(
        () ->
            decisionDefinitionServices.evaluateDecision(
                request.decisionId(),
                request.decisionKey(),
                request.variables(),
                request.tenantId(),
                authenticationProvider.getCamundaAuthentication()),
        ResponseMapper::toEvaluateDecisionResponse,
        HttpStatus.OK);
  }
}
