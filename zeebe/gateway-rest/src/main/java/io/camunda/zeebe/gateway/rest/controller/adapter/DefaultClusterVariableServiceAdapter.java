/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.adapter;

import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.mapping.http.mapper.ClusterVariableMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mapping.http.validator.ClusterVariableRequestValidator;
import io.camunda.gateway.protocol.model.ClusterVariableSearchQueryRequest;
import io.camunda.gateway.protocol.model.CreateClusterVariableRequest;
import io.camunda.gateway.protocol.model.UpdateClusterVariableRequest;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.validation.ClusterVariableValidator;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.service.ClusterVariableServices;
import io.camunda.zeebe.gateway.rest.controller.generated.ClusterVariableServiceAdapter;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DefaultClusterVariableServiceAdapter implements ClusterVariableServiceAdapter {

  private final ClusterVariableServices clusterVariableServices;
  private final ClusterVariableMapper clusterVariableMapper;

  public DefaultClusterVariableServiceAdapter(
      final ClusterVariableServices clusterVariableServices,
      final IdentifierValidator identifierValidator) {
    this.clusterVariableServices = clusterVariableServices;
    clusterVariableMapper =
        new ClusterVariableMapper(
            new ClusterVariableRequestValidator(new ClusterVariableValidator(identifierValidator)));
  }

  @Override
  public ResponseEntity<Object> createGlobalClusterVariable(
      final CreateClusterVariableRequest createClusterVariableRequestStrict,
      final CamundaAuthentication authentication) {
    return clusterVariableMapper
        .toGlobalClusterVariableCreateRequest(createClusterVariableRequestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () ->
                        clusterVariableServices.createGloballyScopedClusterVariable(
                            request, authentication),
                    ResponseMapper::toClusterVariableResponse,
                    HttpStatus.OK));
  }

  @Override
  public ResponseEntity<Object> createTenantClusterVariable(
      final String tenantId,
      final CreateClusterVariableRequest createClusterVariableRequestStrict,
      final CamundaAuthentication authentication) {
    return clusterVariableMapper
        .toTenantClusterVariableCreateRequest(createClusterVariableRequestStrict, tenantId)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () ->
                        clusterVariableServices.createTenantScopedClusterVariable(
                            request, authentication),
                    ResponseMapper::toClusterVariableResponse,
                    HttpStatus.OK));
  }

  @Override
  public ResponseEntity<Object> searchClusterVariables(
      final Boolean truncateValues,
      final ClusterVariableSearchQueryRequest clusterVariableSearchQueryStrict,
      final CamundaAuthentication authentication) {
    final boolean truncate = truncateValues == null || truncateValues;
    return SearchQueryRequestMapper.toClusterVariableQueryStrict(clusterVariableSearchQueryStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result = clusterVariableServices.search(query, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toClusterVariableSearchQueryResponse(
                        result, truncate));
              } catch (final Exception e) {
                return RestErrorMapper.mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> getGlobalClusterVariable(
      final String name, final CamundaAuthentication authentication) {
    return clusterVariableMapper
        .toGlobalClusterVariableRequest(name)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request -> {
              try {
                return ResponseEntity.ok()
                    .body(
                        SearchQueryResponseMapper.toClusterVariableResult(
                            clusterVariableServices.getGloballyScopedClusterVariable(
                                request, authentication)));
              } catch (final Exception e) {
                return RestErrorMapper.mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> updateGlobalClusterVariable(
      final String name,
      final UpdateClusterVariableRequest updateClusterVariableRequestStrict,
      final CamundaAuthentication authentication) {
    return clusterVariableMapper
        .toGlobalClusterVariableUpdateRequest(name, updateClusterVariableRequestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () ->
                        clusterVariableServices.updateGloballyScopedClusterVariable(
                            request, authentication),
                    ResponseMapper::toClusterVariableResponse,
                    HttpStatus.OK));
  }

  @Override
  public ResponseEntity<Void> deleteGlobalClusterVariable(
      final String name, final CamundaAuthentication authentication) {
    return clusterVariableMapper
        .toGlobalClusterVariableRequest(name)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () ->
                        clusterVariableServices.deleteGloballyScopedClusterVariable(
                            request, authentication)));
  }

  @Override
  public ResponseEntity<Object> getTenantClusterVariable(
      final String tenantId, final String name, final CamundaAuthentication authentication) {
    return clusterVariableMapper
        .toTenantClusterVariableRequest(name, tenantId)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request -> {
              try {
                return ResponseEntity.ok()
                    .body(
                        SearchQueryResponseMapper.toClusterVariableResult(
                            clusterVariableServices.getTenantScopedClusterVariable(
                                request, authentication)));
              } catch (final Exception e) {
                return RestErrorMapper.mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> updateTenantClusterVariable(
      final String tenantId,
      final String name,
      final UpdateClusterVariableRequest updateClusterVariableRequestStrict,
      final CamundaAuthentication authentication) {
    return clusterVariableMapper
        .toTenantClusterVariableUpdateRequest(name, updateClusterVariableRequestStrict, tenantId)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () ->
                        clusterVariableServices.updateTenantScopedClusterVariable(
                            request, authentication),
                    ResponseMapper::toClusterVariableResponse,
                    HttpStatus.OK));
  }

  @Override
  public ResponseEntity<Void> deleteTenantClusterVariable(
      final String tenantId, final String name, final CamundaAuthentication authentication) {
    return clusterVariableMapper
        .toTenantClusterVariableRequest(name, tenantId)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () ->
                        clusterVariableServices.deleteTenantScopedClusterVariable(
                            request, authentication)));
  }
}
