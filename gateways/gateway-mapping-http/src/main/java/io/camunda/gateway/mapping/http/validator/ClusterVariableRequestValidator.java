/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;

import io.camunda.gateway.mapping.http.search.contract.generated.CreateClusterVariableRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.UpdateClusterVariableRequestContract;
import io.camunda.security.validation.ClusterVariableValidator;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public class ClusterVariableRequestValidator {

  private final ClusterVariableValidator clusterVariableValidator;

  public ClusterVariableRequestValidator(final ClusterVariableValidator clusterVariableValidator) {
    this.clusterVariableValidator = clusterVariableValidator;
  }

  public Optional<ProblemDetail> validateTenantClusterVariableCreateRequest(
      final CreateClusterVariableRequestContract request, final String tenantId) {
    return validate(
        () ->
            clusterVariableValidator.validateTenantClusterVariableRequestWithValue(
                request.name(), request.value(), tenantId));
  }

  public Optional<ProblemDetail> validateGlobalClusterVariableCreateRequest(
      final CreateClusterVariableRequestContract request) {
    return validate(
        () ->
            clusterVariableValidator.validateGlobalClusterVariableRequestWithValue(
                request.name(), request.value()));
  }

  public Optional<ProblemDetail> validateTenantClusterVariableRequest(
      final String name, final String tenantId) {
    return validate(
        () -> clusterVariableValidator.validateTenantClusterVariableRequest(name, tenantId));
  }

  public Optional<ProblemDetail> validateGlobalClusterVariableRequest(final String name) {
    return validate(() -> clusterVariableValidator.validateGlobalClusterVariableRequest(name));
  }

  public Optional<ProblemDetail> validateGlobalClusterVariableUpdateRequest(
      final String name, final UpdateClusterVariableRequestContract request) {
    return validate(
        () ->
            clusterVariableValidator.validateGlobalClusterVariableRequestWithValue(
                name, request.value()));
  }

  public Optional<ProblemDetail> validateTenantClusterVariableUpdateRequest(
      final String name,
      final UpdateClusterVariableRequestContract request,
      final String tenantId) {
    return validate(
        () ->
            clusterVariableValidator.validateTenantClusterVariableRequestWithValue(
                name, request.value(), tenantId));
  }
}
