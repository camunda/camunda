/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.mapper;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.CreateClusterVariableRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.UpdateClusterVariableRequestContract;
import io.camunda.gateway.mapping.http.validator.ClusterVariableRequestValidator;
import io.camunda.service.ClusterVariableServices.ClusterVariableRequest;
import io.camunda.zeebe.util.Either;
import org.springframework.http.ProblemDetail;

public class ClusterVariableMapper {

  private final ClusterVariableRequestValidator clusterVariableRequestValidator;

  public ClusterVariableMapper(
      final ClusterVariableRequestValidator clusterVariableRequestValidator) {
    this.clusterVariableRequestValidator = clusterVariableRequestValidator;
  }

  public Either<ProblemDetail, ClusterVariableRequest> toTenantClusterVariableRequest(
      final String name, final String tenantId) {
    return RequestMapper.getResult(
        clusterVariableRequestValidator.validateTenantClusterVariableRequest(name, tenantId),
        () -> new ClusterVariableRequest(name, null, tenantId));
  }

  public Either<ProblemDetail, ClusterVariableRequest> toGlobalClusterVariableRequest(
      final String name) {
    return RequestMapper.getResult(
        clusterVariableRequestValidator.validateGlobalClusterVariableRequest(name),
        () -> new ClusterVariableRequest(name, null, null));
  }

  public Either<ProblemDetail, ClusterVariableRequest> toGlobalClusterVariableCreateRequest(
      final CreateClusterVariableRequestContract request) {
    return RequestMapper.getResult(
        clusterVariableRequestValidator.validateGlobalClusterVariableCreateRequest(request),
        () -> new ClusterVariableRequest(request.name(), request.value(), null));
  }

  public Either<ProblemDetail, ClusterVariableRequest> toTenantClusterVariableCreateRequest(
      final CreateClusterVariableRequestContract request, final String tenantId) {
    return RequestMapper.getResult(
        clusterVariableRequestValidator.validateTenantClusterVariableCreateRequest(
            request, tenantId),
        () -> new ClusterVariableRequest(request.name(), request.value(), tenantId));
  }

  public Either<ProblemDetail, ClusterVariableRequest> toGlobalClusterVariableUpdateRequest(
      final String name, final UpdateClusterVariableRequestContract request) {
    return RequestMapper.getResult(
        clusterVariableRequestValidator.validateGlobalClusterVariableUpdateRequest(name, request),
        () -> new ClusterVariableRequest(name, request.value(), null));
  }

  public Either<ProblemDetail, ClusterVariableRequest> toTenantClusterVariableUpdateRequest(
      final String name,
      final UpdateClusterVariableRequestContract request,
      final String tenantId) {
    return RequestMapper.getResult(
        clusterVariableRequestValidator.validateTenantClusterVariableUpdateRequest(
            name, request, tenantId),
        () -> new ClusterVariableRequest(name, request.value(), tenantId));
  }
}
