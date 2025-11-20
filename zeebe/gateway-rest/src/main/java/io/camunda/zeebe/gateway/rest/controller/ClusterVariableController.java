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
import io.camunda.service.ClusterVariableServices;
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

@CamundaRestController
@RequiresSecondaryStorage
@RequestMapping("/v2/cluster-variables")
public class ClusterVariableController {

  private final ClusterVariableServices clusterVariableServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public ClusterVariableController(
      final ClusterVariableServices clusterVariableServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.clusterVariableServices = clusterVariableServices;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping
  public CompletableFuture<ResponseEntity<Object>> createClusterVariable(
      @RequestBody final CreateClusterVariableRequest createClusterVariableRequest) {
    return RequestMapper.executeServiceMethod(
        () ->
            switch (createClusterVariableRequest.getScope()) {
              case GLOBAL ->
                  clusterVariableServices
                      .withAuthentication(authenticationProvider.getCamundaAuthentication())
                      .createGloballyScopedClusterVariable(
                          createClusterVariableRequest.getName(),
                          createClusterVariableRequest.getValue());
              case TENANT ->
                  clusterVariableServices
                      .withAuthentication(authenticationProvider.getCamundaAuthentication())
                      .createTenantScopedClusterVariable(
                          createClusterVariableRequest.getName(),
                          createClusterVariableRequest.getValue(),
                          createClusterVariableRequest.getTenantId());
            },
        ResponseMapper::toClusterVariableCreateResponse);
  }

  @CamundaDeleteMapping(path = "/{name}/TENANT/{tenantId}")
  public CompletableFuture<ResponseEntity<Object>> deleteTenantScopedClusterVariable(
      @PathVariable("name") final String name, @PathVariable("tenantId") final String tenantId) {
    return RequestMapper.executeServiceMethod(
        () ->
            clusterVariableServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .deleteTenantScopedClusterVariable(name, tenantId),
        ResponseMapper::toClusterVariableDeleteResponse);
  }

  @CamundaDeleteMapping(path = "/{name}/GLOBAL")
  public CompletableFuture<ResponseEntity<Object>> deleteGloballyScopedClusterVariable(
      @PathVariable("name") final String name) {
    return RequestMapper.executeServiceMethod(
        () ->
            clusterVariableServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .deleteGloballyScopedClusterVariable(name),
        ResponseMapper::toClusterVariableDeleteResponse);
  }

  @CamundaPostMapping(path = "/search")
  private ResponseEntity<Object> search(
      @RequestBody final ClusterVariableSearchQueryRequest query) {
    return SearchQueryRequestMapper.toClusterVariableQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  private ResponseEntity<Object> search(final ClusterVariableQuery query) {
    try {
      final var result =
          clusterVariableServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(query);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toClusterVariableSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @CamundaGetMapping(path = "/{name}/GLOBAL")
  public ResponseEntity<Object> getGloballyScopedClusterVariable(
      @PathVariable("name") final String name) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toClusterVariable(
                  clusterVariableServices
                      .withAuthentication(authenticationProvider.getCamundaAuthentication())
                      .getGloballyScopedClusterVariable(name)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @CamundaGetMapping(path = "/{name}/TENANT/{tenantId}")
  public ResponseEntity<Object> getTenantScopedClusterVariable(
      @PathVariable("name") final String name, @PathVariable("tenantId") final String tenantId) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toClusterVariable(
                  clusterVariableServices
                      .withAuthentication(authenticationProvider.getCamundaAuthentication())
                      .getTenantScopedClusterVariable(name, tenantId)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
