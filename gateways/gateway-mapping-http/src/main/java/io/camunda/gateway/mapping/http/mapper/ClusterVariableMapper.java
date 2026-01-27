/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.mapper;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.validator.ClusterVariableRequestValidator;
import io.camunda.gateway.protocol.model.CreateClusterVariableRequest;
import io.camunda.gateway.protocol.model.UpdateClusterVariableRequest;
import io.camunda.service.ClusterVariableServices.ClusterVariableRequest;
import io.camunda.zeebe.util.Either;
import org.springframework.http.ProblemDetail;

public class ClusterVariableMapper {

  private final ClusterVariableRequestValidator clusterVariableRequestValidator;

  public ClusterVariableMapper(
      final ClusterVariableRequestValidator clusterVariableRequestValidator) {
    this.clusterVariableRequestValidator = clusterVariableRequestValidator;
  }

  public Either<ProblemDetail, ClusterVariableRequest> toTenantClusterVariableCreateRequest(
      final CreateClusterVariableRequest request, final String tenantId) {
    return RequestMapper.getResult(
        clusterVariableRequestValidator.validateTenantClusterVariableCreateRequest(
            request, tenantId),
        () -> new ClusterVariableRequest(request.getName(), request.getValue(), tenantId));
  }

  public Either<ProblemDetail, ClusterVariableRequest> toGlobalClusterVariableUpdateRequest(
      final String name, final UpdateClusterVariableRequest request) {
    return RequestMapper.getResult(
        clusterVariableRequestValidator.validateGlobalClusterVariableUpdateRequest(name, request),
        () -> new ClusterVariableRequest(name, request.getValue(), null));
  }

  public Either<ProblemDetail, ClusterVariableRequest> toTenantClusterVariableUpdateRequest(
      final String name, final UpdateClusterVariableRequest request, final String tenantId) {
    return RequestMapper.getResult(
        clusterVariableRequestValidator.validateTenantClusterVariableUpdateRequest(
            name, request, tenantId),
        () -> new ClusterVariableRequest(name, request.getValue(), tenantId));
  }

  public Either<ProblemDetail, ClusterVariableRequest> toTenantClusterVariableRequest(
      final String name, final String tenantId) {
    return RequestMapper.getResult(
        clusterVariableRequestValidator.validateTenantClusterVariableRequest(name, tenantId),
        () -> new ClusterVariableRequest(name, null, tenantId));
  }

  public Either<ProblemDetail, ClusterVariableRequest> toGlobalClusterVariableCreateRequest(
      final CreateClusterVariableRequest request) {
    return RequestMapper.getResult(
        clusterVariableRequestValidator.validateGlobalClusterVariableCreateRequest(request),
        () -> new ClusterVariableRequest(request.getName(), request.getValue(), null));
  }

  public Either<ProblemDetail, ClusterVariableRequest> toGlobalClusterVariableRequest(
      final String name) {
    return RequestMapper.getResult(
        clusterVariableRequestValidator.validateGlobalClusterVariableRequest(name),
        () -> new ClusterVariableRequest(name, null, null));
  }
}
