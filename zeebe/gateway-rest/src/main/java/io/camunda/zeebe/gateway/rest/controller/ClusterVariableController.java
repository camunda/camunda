/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.mapping.http.mapper.ClusterVariableMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mapping.http.validator.ClusterVariableRequestValidator;
import io.camunda.gateway.protocol.model.ClusterVariableSearchQueryRequest;
import io.camunda.gateway.protocol.model.CreateClusterVariableRequest;
import io.camunda.gateway.protocol.model.UpdateClusterVariableRequest;
import io.camunda.search.query.ClusterVariableQuery;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.validation.ClusterVariableValidator;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.service.ClusterVariableServices;
import io.camunda.service.ClusterVariableServices.ClusterVariableRequest;
import io.camunda.zeebe.gateway.rest.annotation.CamundaDeleteMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPatchMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@CamundaRestController
@RequiresSecondaryStorage
@RequestMapping("/v2/cluster-variables")
public class ClusterVariableController {

  private final ClusterVariableServices clusterVariableServices;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final ClusterVariableMapper clusterVariableMapper;

  public ClusterVariableController(
      final ClusterVariableServices clusterVariableServices,
      final CamundaAuthenticationProvider authenticationProvider,
      final IdentifierValidator identifierValidator) {
    this.clusterVariableServices = clusterVariableServices;
    this.authenticationProvider = authenticationProvider;
    clusterVariableMapper =
        new ClusterVariableMapper(
            new ClusterVariableRequestValidator(new ClusterVariableValidator(identifierValidator)));
  }

  @CamundaPostMapping(path = "/global")
  public CompletableFuture<ResponseEntity<Object>> createGlobalClusterVariable(
      @RequestBody final CreateClusterVariableRequest createClusterVariableRequest) {
    return clusterVariableMapper
        .toGlobalClusterVariableCreateRequest(createClusterVariableRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::createGlobalClusterVariable);
  }

  @CamundaPostMapping(path = "/tenants/{tenantId}")
  public CompletableFuture<ResponseEntity<Object>> createTenantClusterVariable(
      @PathVariable("tenantId") final String tenantId,
      @RequestBody final CreateClusterVariableRequest createClusterVariableRequest) {
    return clusterVariableMapper
        .toTenantClusterVariableCreateRequest(createClusterVariableRequest, tenantId)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::createTenantClusterVariable);
  }

  @CamundaDeleteMapping(path = "/global/{name}")
  public CompletableFuture<ResponseEntity<Object>> deleteGlobalClusterVariable(
      @PathVariable("name") final String name) {
    return clusterVariableMapper
        .toGlobalClusterVariableRequest(name)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::deleteGlobalClusterVariable);
  }

  @CamundaDeleteMapping(path = "/tenants/{tenantId}/{name}")
  public CompletableFuture<ResponseEntity<Object>> deleteTenantClusterVariable(
      @PathVariable("tenantId") final String tenantId, @PathVariable("name") final String name) {
    return clusterVariableMapper
        .toTenantClusterVariableRequest(name, tenantId)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::deleteTenantClusterVariable);
  }

  @CamundaPatchMapping(path = "/global/{name}")
  public CompletableFuture<ResponseEntity<Object>> updateGlobalClusterVariable(
      @PathVariable("name") final String name,
      @RequestBody final UpdateClusterVariableRequest updateClusterVariableRequest) {
    return clusterVariableMapper
        .toGlobalClusterVariableUpdateRequest(name, updateClusterVariableRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::updateGlobalClusterVariable);
  }

  @CamundaPatchMapping(path = "/tenants/{tenantId}/{name}")
  public CompletableFuture<ResponseEntity<Object>> updateTenantClusterVariable(
      @PathVariable("tenantId") final String tenantId,
      @PathVariable("name") final String name,
      @RequestBody final UpdateClusterVariableRequest updateClusterVariableRequest) {
    return clusterVariableMapper
        .toTenantClusterVariableUpdateRequest(name, updateClusterVariableRequest, tenantId)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::updateTenantClusterVariable);
  }

  @CamundaPostMapping(path = "/search")
  private ResponseEntity<Object> search(
      @RequestBody final ClusterVariableSearchQueryRequest query,
      @RequestParam(name = "truncateValues", required = false, defaultValue = "true")
          final boolean truncateValues) {
    return SearchQueryRequestMapper.toClusterVariableQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, q -> search(q, truncateValues));
  }

  private ResponseEntity<Object> search(
      final ClusterVariableQuery query, final boolean truncateValues) {
    try {
      final var result =
          clusterVariableServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(query);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toClusterVariableSearchQueryResponse(result, truncateValues));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @CamundaGetMapping(path = "/global/{name}")
  public ResponseEntity<Object> getGlobalClusterVariable(@PathVariable("name") final String name) {
    return clusterVariableMapper
        .toGlobalClusterVariableRequest(name)
        .fold(RestErrorMapper::mapProblemToResponse, this::getGlobalClusterVariable);
  }

  @CamundaGetMapping(path = "/tenants/{tenantId}/{name}")
  public ResponseEntity<Object> getTenantClusterVariable(
      @PathVariable("tenantId") final String tenantId, @PathVariable("name") final String name) {
    return clusterVariableMapper
        .toTenantClusterVariableRequest(name, tenantId)
        .fold(RestErrorMapper::mapProblemToResponse, this::getTenantClusterVariable);
  }

  private ResponseEntity<Object> getGlobalClusterVariable(final ClusterVariableRequest request) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toClusterVariableResult(
                  clusterVariableServices
                      .withAuthentication(authenticationProvider.getCamundaAuthentication())
                      .getGloballyScopedClusterVariable(request)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<Object> getTenantClusterVariable(final ClusterVariableRequest request) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toClusterVariableResult(
                  clusterVariableServices
                      .withAuthentication(authenticationProvider.getCamundaAuthentication())
                      .getTenantScopedClusterVariable(request)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private CompletableFuture<ResponseEntity<Object>> createGlobalClusterVariable(
      final ClusterVariableRequest request) {
    return RequestExecutor.executeServiceMethod(
        () ->
            clusterVariableServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .createGloballyScopedClusterVariable(request),
        ResponseMapper::toClusterVariableResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> createTenantClusterVariable(
      final ClusterVariableRequest request) {
    return RequestExecutor.executeServiceMethod(
        () ->
            clusterVariableServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .createTenantScopedClusterVariable(request),
        ResponseMapper::toClusterVariableResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> deleteGlobalClusterVariable(
      final ClusterVariableRequest request) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            clusterVariableServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .deleteGloballyScopedClusterVariable(request));
  }

  private CompletableFuture<ResponseEntity<Object>> deleteTenantClusterVariable(
      final ClusterVariableRequest request) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            clusterVariableServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .deleteTenantScopedClusterVariable(request));
  }

  private CompletableFuture<ResponseEntity<Object>> updateGlobalClusterVariable(
      final ClusterVariableRequest request) {
    return RequestExecutor.executeServiceMethod(
        () ->
            clusterVariableServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .updateGloballyScopedClusterVariable(request),
        ResponseMapper::toClusterVariableResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> updateTenantClusterVariable(
      final ClusterVariableRequest request) {
    return RequestExecutor.executeServiceMethod(
        () ->
            clusterVariableServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .updateTenantScopedClusterVariable(request),
        ResponseMapper::toClusterVariableResponse,
        HttpStatus.OK);
  }
}
