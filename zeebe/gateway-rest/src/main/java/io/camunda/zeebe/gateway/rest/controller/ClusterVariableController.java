/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.search.query.ClusterVariableQuery;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.ClusterVariableServices;
import io.camunda.service.ClusterVariableServices.ClusterVariableRequest;
import io.camunda.zeebe.gateway.protocol.rest.ClusterVariableSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.CreateClusterVariableRequest;
import io.camunda.zeebe.gateway.rest.annotation.CamundaDeleteMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.RequestMapper;
import io.camunda.zeebe.gateway.rest.mapper.ResponseMapper;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.mapper.search.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.mapper.search.SearchQueryResponseMapper;
import java.util.concurrent.CompletableFuture;
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
  private final SecurityConfiguration securityConfiguration;

  public ClusterVariableController(
      final ClusterVariableServices clusterVariableServices,
      final CamundaAuthenticationProvider authenticationProvider,
      final SecurityConfiguration securityConfiguration) {
    this.clusterVariableServices = clusterVariableServices;
    this.authenticationProvider = authenticationProvider;
    this.securityConfiguration = securityConfiguration;
  }

  @CamundaPostMapping(path = "/global")
  public CompletableFuture<ResponseEntity<Object>> createGlobalClusterVariable(
      @RequestBody final CreateClusterVariableRequest createClusterVariableRequest) {
    return RequestMapper.toGlobalClusterVariableCreateRequest(
            createClusterVariableRequest, securityConfiguration.getCompiledIdValidationPattern())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::createGlobalClusterVariable);
  }

  @CamundaPostMapping(path = "/tenants/{tenantId}")
  public CompletableFuture<ResponseEntity<Object>> createTenantClusterVariable(
      @PathVariable("tenantId") final String tenantId,
      @RequestBody final CreateClusterVariableRequest createClusterVariableRequest) {
    return RequestMapper.toTenantClusterVariableCreateRequest(
            createClusterVariableRequest,
            tenantId,
            securityConfiguration.getCompiledIdValidationPattern())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::createTenantClusterVariable);
  }

  @CamundaDeleteMapping(path = "/global/{name}")
  public CompletableFuture<ResponseEntity<Object>> deleteGlobalClusterVariable(
      @PathVariable("name") final String name) {
    return RequestMapper.toGlobalClusterVariableRequest(
            name, securityConfiguration.getCompiledIdValidationPattern())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::deleteGlobalClusterVariable);
  }

  @CamundaDeleteMapping(path = "/tenants/{tenantId}/{name}")
  public CompletableFuture<ResponseEntity<Object>> deleteTenantClusterVariable(
      @PathVariable("tenantId") final String tenantId, @PathVariable("name") final String name) {
    return RequestMapper.toTenantClusterVariableRequest(
            name, tenantId, securityConfiguration.getCompiledIdValidationPattern())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::deleteTenantClusterVariable);
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
    return RequestMapper.toGlobalClusterVariableRequest(
            name, securityConfiguration.getCompiledIdValidationPattern())
        .fold(RestErrorMapper::mapProblemToResponse, this::getGlobalClusterVariable);
  }

  @CamundaGetMapping(path = "/tenants/{tenantId}/{name}")
  public ResponseEntity<Object> getTenantClusterVariable(
      @PathVariable("tenantId") final String tenantId, @PathVariable("name") final String name) {
    return RequestMapper.toTenantClusterVariableRequest(
            name, tenantId, securityConfiguration.getCompiledIdValidationPattern())
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
    return RequestMapper.executeServiceMethod(
        () ->
            clusterVariableServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .createGloballyScopedClusterVariable(request),
        ResponseMapper::toClusterVariableCreateResponse);
  }

  private CompletableFuture<ResponseEntity<Object>> createTenantClusterVariable(
      final ClusterVariableRequest request) {
    return RequestMapper.executeServiceMethod(
        () ->
            clusterVariableServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .createTenantScopedClusterVariable(request),
        ResponseMapper::toClusterVariableCreateResponse);
  }

  private CompletableFuture<ResponseEntity<Object>> deleteGlobalClusterVariable(
      final ClusterVariableRequest request) {
    return RequestMapper.executeServiceMethod(
        () ->
            clusterVariableServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .deleteGloballyScopedClusterVariable(request),
        ResponseMapper::toClusterVariableDeleteResponse);
  }

  private CompletableFuture<ResponseEntity<Object>> deleteTenantClusterVariable(
      final ClusterVariableRequest request) {
    return RequestMapper.executeServiceMethod(
        () ->
            clusterVariableServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .deleteTenantScopedClusterVariable(request),
        ResponseMapper::toClusterVariableDeleteResponse);
  }
}
