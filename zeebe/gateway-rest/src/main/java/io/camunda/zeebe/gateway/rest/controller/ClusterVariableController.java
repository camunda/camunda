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
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.validation.ClusterVariableValidator;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.service.ClusterVariableServices;
import io.camunda.service.ClusterVariableServices.ClusterVariableRequest;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaDeleteMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPutMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
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
@RequestMapping("/v2/cluster-variables")
public class ClusterVariableController {

  private final ServiceRegistry serviceRegistry;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final ClusterVariableMapper clusterVariableMapper;

  public ClusterVariableController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider,
      final IdentifierValidator identifierValidator) {
    this.serviceRegistry = serviceRegistry;
    this.authenticationProvider = authenticationProvider;
    clusterVariableMapper =
        new ClusterVariableMapper(
            new ClusterVariableRequestValidator(new ClusterVariableValidator(identifierValidator)));
  }

  @CamundaPostMapping(path = "/global")
  public CompletableFuture<ResponseEntity<Object>> createGlobalClusterVariable(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final CreateClusterVariableRequest createClusterVariableRequest) {
    return clusterVariableMapper
        .toGlobalClusterVariableCreateRequest(createClusterVariableRequest)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            mapped ->
                createGlobalClusterVariable(
                    serviceRegistry.clusterVariableServices(physicalTenantId), mapped));
  }

  @CamundaPostMapping(path = "/tenants/{tenantId}")
  public CompletableFuture<ResponseEntity<Object>> createTenantClusterVariable(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("tenantId") final String tenantId,
      @RequestBody final CreateClusterVariableRequest createClusterVariableRequest) {
    return clusterVariableMapper
        .toTenantClusterVariableCreateRequest(createClusterVariableRequest, tenantId)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            mapped ->
                createTenantClusterVariable(
                    serviceRegistry.clusterVariableServices(physicalTenantId), mapped));
  }

  @CamundaDeleteMapping(path = "/global/{name}")
  public CompletableFuture<ResponseEntity<Object>> deleteGlobalClusterVariable(
      @PhysicalTenantId final String physicalTenantId, @PathVariable("name") final String name) {
    return clusterVariableMapper
        .toGlobalClusterVariableRequest(name)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            mapped ->
                deleteGlobalClusterVariable(
                    serviceRegistry.clusterVariableServices(physicalTenantId), mapped));
  }

  @CamundaDeleteMapping(path = "/tenants/{tenantId}/{name}")
  public CompletableFuture<ResponseEntity<Object>> deleteTenantClusterVariable(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("tenantId") final String tenantId,
      @PathVariable("name") final String name) {
    return clusterVariableMapper
        .toTenantClusterVariableRequest(name, tenantId)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            mapped ->
                deleteTenantClusterVariable(
                    serviceRegistry.clusterVariableServices(physicalTenantId), mapped));
  }

  @CamundaPutMapping(path = "/global/{name}")
  public CompletableFuture<ResponseEntity<Object>> updateGlobalClusterVariable(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("name") final String name,
      @RequestBody final UpdateClusterVariableRequest updateClusterVariableRequest) {
    return clusterVariableMapper
        .toGlobalClusterVariableUpdateRequest(name, updateClusterVariableRequest)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            mapped ->
                updateGlobalClusterVariable(
                    serviceRegistry.clusterVariableServices(physicalTenantId), mapped));
  }

  @CamundaPutMapping(path = "/tenants/{tenantId}/{name}")
  public CompletableFuture<ResponseEntity<Object>> updateTenantClusterVariable(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("tenantId") final String tenantId,
      @PathVariable("name") final String name,
      @RequestBody final UpdateClusterVariableRequest updateClusterVariableRequest) {
    return clusterVariableMapper
        .toTenantClusterVariableUpdateRequest(name, updateClusterVariableRequest, tenantId)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            mapped ->
                updateTenantClusterVariable(
                    serviceRegistry.clusterVariableServices(physicalTenantId), mapped));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  public ResponseEntity<Object> search(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final ClusterVariableSearchQueryRequest query,
      @RequestParam(name = "truncateValues", required = false, defaultValue = "true")
          final boolean truncateValues) {
    return SearchQueryRequestMapper.toClusterVariableQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            q ->
                search(
                    serviceRegistry.clusterVariableServices(physicalTenantId), q, truncateValues));
  }

  private ResponseEntity<Object> search(
      final ClusterVariableServices clusterVariableServices,
      final ClusterVariableQuery query,
      final boolean truncateValues) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result = clusterVariableServices.search(query, authentication);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toClusterVariableSearchQueryResponse(result, truncateValues));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/global/{name}")
  public ResponseEntity<Object> getGlobalClusterVariable(
      @PhysicalTenantId final String physicalTenantId, @PathVariable("name") final String name) {
    return clusterVariableMapper
        .toGlobalClusterVariableRequest(name)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            mapped ->
                getGlobalClusterVariable(
                    serviceRegistry.clusterVariableServices(physicalTenantId), mapped));
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/tenants/{tenantId}/{name}")
  public ResponseEntity<Object> getTenantClusterVariable(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("tenantId") final String tenantId,
      @PathVariable("name") final String name) {
    return clusterVariableMapper
        .toTenantClusterVariableRequest(name, tenantId)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            mapped ->
                getTenantClusterVariable(
                    serviceRegistry.clusterVariableServices(physicalTenantId), mapped));
  }

  private ResponseEntity<Object> getGlobalClusterVariable(
      final ClusterVariableServices clusterVariableServices, final ClusterVariableRequest request) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toClusterVariableResult(
                  clusterVariableServices.getGloballyScopedClusterVariable(
                      request, authentication)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<Object> getTenantClusterVariable(
      final ClusterVariableServices clusterVariableServices, final ClusterVariableRequest request) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toClusterVariableResult(
                  clusterVariableServices.getTenantScopedClusterVariable(request, authentication)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private CompletableFuture<ResponseEntity<Object>> createGlobalClusterVariable(
      final ClusterVariableServices clusterVariableServices, final ClusterVariableRequest request) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> clusterVariableServices.createGloballyScopedClusterVariable(request, authentication),
        ResponseMapper::toClusterVariableResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> createTenantClusterVariable(
      final ClusterVariableServices clusterVariableServices, final ClusterVariableRequest request) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> clusterVariableServices.createTenantScopedClusterVariable(request, authentication),
        ResponseMapper::toClusterVariableResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> deleteGlobalClusterVariable(
      final ClusterVariableServices clusterVariableServices, final ClusterVariableRequest request) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () -> clusterVariableServices.deleteGloballyScopedClusterVariable(request, authentication));
  }

  private CompletableFuture<ResponseEntity<Object>> deleteTenantClusterVariable(
      final ClusterVariableServices clusterVariableServices, final ClusterVariableRequest request) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () -> clusterVariableServices.deleteTenantScopedClusterVariable(request, authentication));
  }

  private CompletableFuture<ResponseEntity<Object>> updateGlobalClusterVariable(
      final ClusterVariableServices clusterVariableServices, final ClusterVariableRequest request) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> clusterVariableServices.updateGloballyScopedClusterVariable(request, authentication),
        ResponseMapper::toClusterVariableResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> updateTenantClusterVariable(
      final ClusterVariableServices clusterVariableServices, final ClusterVariableRequest request) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> clusterVariableServices.updateTenantScopedClusterVariable(request, authentication),
        ResponseMapper::toClusterVariableResponse,
        HttpStatus.OK);
  }
}
