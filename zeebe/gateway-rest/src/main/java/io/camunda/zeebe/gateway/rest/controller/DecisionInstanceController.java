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
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mapping.http.validator.RequestValidator;
import io.camunda.gateway.protocol.model.DecisionInstanceDeletionBatchOperationRequest;
import io.camunda.gateway.protocol.model.DecisionInstanceGetQueryResult;
import io.camunda.gateway.protocol.model.DecisionInstanceSearchQuery;
import io.camunda.gateway.protocol.model.DecisionInstanceSearchQueryResult;
import io.camunda.gateway.protocol.model.DeleteDecisionInstanceRequest;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.DecisionInstanceServices;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenant;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/decision-instances")
public class DecisionInstanceController {

  private final DecisionInstanceServices decisionInstanceServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public DecisionInstanceController(
      final DecisionInstanceServices decisionInstanceServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.decisionInstanceServices = decisionInstanceServices;
    this.authenticationProvider = authenticationProvider;
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  public ResponseEntity<DecisionInstanceSearchQueryResult> searchDecisionInstances(
      @RequestBody(required = false) final DecisionInstanceSearchQuery query,
      @PhysicalTenant final String physicalTenantId) {
    return SearchQueryRequestMapper.toDecisionInstanceQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, q -> search(q, physicalTenantId));
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{decisionEvaluationInstanceKey}")
  public ResponseEntity<DecisionInstanceGetQueryResult> getDecisionInstanceById(
      @PathVariable("decisionEvaluationInstanceKey") final String decisionEvaluationInstanceKey,
      @PhysicalTenant final String physicalTenantId) {
    return RequestValidator.validate(
            violations ->
                RequestValidator.validateDecisionEvaluationInstanceKeyFormat(
                    decisionEvaluationInstanceKey, violations))
        .<ResponseEntity<DecisionInstanceGetQueryResult>>map(RestErrorMapper::mapProblemToResponse)
        .orElseGet(() -> getDecisionInstance(decisionEvaluationInstanceKey, physicalTenantId));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{decisionEvaluationKey}/deletion")
  public CompletableFuture<ResponseEntity<Object>> deleteDecisionInstance(
      @PathVariable final long decisionEvaluationKey,
      @RequestBody(required = false) final DeleteDecisionInstanceRequest request,
      @PhysicalTenant final String physicalTenantId) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            decisionInstanceServices.deleteDecisionInstance(
                decisionEvaluationKey,
                Objects.nonNull(request) ? request.getOperationReference() : null,
                authenticationProvider.getCamundaAuthentication(),
                physicalTenantId));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/deletion")
  public CompletableFuture<ResponseEntity<Object>> deleteDecisionInstancesBatchOperation(
      @RequestBody final DecisionInstanceDeletionBatchOperationRequest request,
      @PhysicalTenant final String physicalTenantId) {
    return RequestMapper.toRequiredDecisionInstanceFilter(request.getFilter())
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            filter -> batchOperationDeletion(filter, physicalTenantId));
  }

  private ResponseEntity<DecisionInstanceSearchQueryResult> search(
      final DecisionInstanceQuery query, final String physicalTenantId) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var decisionInstances =
          decisionInstanceServices.search(query, authentication, physicalTenantId);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toDecisionInstanceSearchQueryResponse(decisionInstances));
    } catch (final Exception e) {
      return RestErrorMapper.mapErrorToResponse(e);
    }
  }

  private ResponseEntity<DecisionInstanceGetQueryResult> getDecisionInstance(
      final String decisionEvaluationInstanceKey, final String physicalTenantId) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var decisionInstanceById =
          decisionInstanceServices.getById(
              decisionEvaluationInstanceKey, authentication, physicalTenantId);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toDecisionInstanceGetQueryResponse(decisionInstanceById));
    } catch (final Exception e) {
      return RestErrorMapper.mapErrorToResponse(e);
    }
  }

  private CompletableFuture<ResponseEntity<Object>> batchOperationDeletion(
      final io.camunda.search.filter.DecisionInstanceFilter filter, final String physicalTenantId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () ->
            decisionInstanceServices.deleteDecisionInstancesBatchOperation(
                filter, authentication, physicalTenantId),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }
}
