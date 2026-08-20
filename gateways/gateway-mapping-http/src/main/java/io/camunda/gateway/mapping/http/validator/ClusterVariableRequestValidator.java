/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_INVALID_METADATA_VALUE_TYPE;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_METADATA_TOO_LARGE;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_NULL_METADATA_KEY;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_TOO_MANY_METADATA_ENTRIES;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.gateway.protocol.model.CreateClusterVariableRequest;
import io.camunda.gateway.protocol.model.UpdateClusterVariableRequest;
import io.camunda.security.validation.ClusterVariableValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public class ClusterVariableRequestValidator {

  /** Maximum number of entries allowed in a cluster variable's metadata bag. */
  public static final int MAX_METADATA_ENTRIES = 100;

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final ClusterVariableValidator clusterVariableValidator;
  private final int maxMetadataSize;

  public ClusterVariableRequestValidator(
      final ClusterVariableValidator clusterVariableValidator, final int maxMetadataSize) {
    this.clusterVariableValidator = clusterVariableValidator;
    this.maxMetadataSize = maxMetadataSize;
  }

  public Optional<ProblemDetail> validateTenantClusterVariableCreateRequest(
      final CreateClusterVariableRequest request, final String tenantId) {
    return validate(
        violations -> {
          violations.addAll(
              clusterVariableValidator.validateTenantClusterVariableRequestWithValue(
                  request.getName(), request.getValue(), tenantId));
          violations.addAll(validateMetadata(request.getMetadata()));
        });
  }

  public Optional<ProblemDetail> validateGlobalClusterVariableCreateRequest(
      final CreateClusterVariableRequest request) {
    return validate(
        violations -> {
          violations.addAll(
              clusterVariableValidator.validateGlobalClusterVariableRequestWithValue(
                  request.getName(), request.getValue()));
          violations.addAll(validateMetadata(request.getMetadata()));
        });
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
      final String name, final UpdateClusterVariableRequest request) {
    return validate(
        violations -> {
          violations.addAll(
              clusterVariableValidator.validateGlobalClusterVariableRequestWithValue(
                  name, request.getValue()));
          violations.addAll(validateMetadata(request.getMetadata()));
        });
  }

  public Optional<ProblemDetail> validateTenantClusterVariableUpdateRequest(
      final String name, final UpdateClusterVariableRequest request, final String tenantId) {
    return validate(
        violations -> {
          violations.addAll(
              clusterVariableValidator.validateTenantClusterVariableRequestWithValue(
                  name, request.getValue(), tenantId));
          violations.addAll(validateMetadata(request.getMetadata()));
        });
  }

  private List<String> validateMetadata(final Map<String, Object> metadata) {
    final List<String> violations = new ArrayList<>();
    if (metadata == null || metadata.isEmpty()) {
      return violations;
    }

    if (metadata.size() > MAX_METADATA_ENTRIES) {
      violations.add(
          ERROR_MESSAGE_TOO_MANY_METADATA_ENTRIES.formatted(metadata.size(), MAX_METADATA_ENTRIES));
      return violations;
    }

    metadata.forEach(
        (key, value) -> {
          if (key == null) {
            violations.add(ERROR_MESSAGE_NULL_METADATA_KEY);
          }
          if (!(value instanceof String) && !(value instanceof Number)) {
            violations.add(
                ERROR_MESSAGE_INVALID_METADATA_VALUE_TYPE.formatted(
                    key, value == null ? "null" : value.getClass().getSimpleName()));
          }
        });
    final int serializedSize = serializedMetadataLength(metadata);
    if (serializedSize > maxMetadataSize) {
      violations.add(ERROR_MESSAGE_METADATA_TOO_LARGE.formatted(maxMetadataSize));
    }
    return violations;
  }

  private int serializedMetadataLength(final Map<String, Object> metadata) {
    try {
      return OBJECT_MAPPER.writeValueAsBytes(metadata).length;
    } catch (final Exception e) {
      // metadata values are only strings/numbers by this point in most cases, but if
      // serialization still fails, treat it as oversized rather than throwing.
      return Integer.MAX_VALUE;
    }
  }
}
