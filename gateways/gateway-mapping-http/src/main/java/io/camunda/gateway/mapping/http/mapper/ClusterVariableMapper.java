/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.mapper;

import static io.camunda.gateway.mapping.http.util.AdvancedSearchFilterUtil.mapToUntypedOperations;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.validator.ClusterVariableRequestValidator;
import io.camunda.gateway.protocol.model.AdvancedMetadataValueFilter;
import io.camunda.gateway.protocol.model.ClusterVariableKindEnum;
import io.camunda.gateway.protocol.model.CreateClusterVariableRequest;
import io.camunda.gateway.protocol.model.UpdateClusterVariableRequest;
import io.camunda.search.entities.ValueTypeEnum;
import io.camunda.search.filter.MetadataValueFilter;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.Operator;
import io.camunda.search.filter.UntypedOperation;
import io.camunda.service.ClusterVariableServices.ClusterVariableRequest;
import io.camunda.zeebe.protocol.record.value.ClusterVariableKind;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
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
        () ->
            new ClusterVariableRequest(
                request.getName(),
                request.getValue(),
                tenantId,
                request.getMetadata(),
                toProtocolKind(request.getKind())));
  }

  public Either<ProblemDetail, ClusterVariableRequest> toGlobalClusterVariableUpdateRequest(
      final String name, final UpdateClusterVariableRequest request) {
    return RequestMapper.getResult(
        clusterVariableRequestValidator.validateGlobalClusterVariableUpdateRequest(name, request),
        () ->
            new ClusterVariableRequest(
                name, request.getValue(), null, request.getMetadata(), null));
  }

  public Either<ProblemDetail, ClusterVariableRequest> toTenantClusterVariableUpdateRequest(
      final String name, final UpdateClusterVariableRequest request, final String tenantId) {
    return RequestMapper.getResult(
        clusterVariableRequestValidator.validateTenantClusterVariableUpdateRequest(
            name, request, tenantId),
        () ->
            new ClusterVariableRequest(
                name, request.getValue(), tenantId, request.getMetadata(), null));
  }

  public Either<ProblemDetail, ClusterVariableRequest> toTenantClusterVariableRequest(
      final String name, final String tenantId) {
    return RequestMapper.getResult(
        clusterVariableRequestValidator.validateTenantClusterVariableRequest(name, tenantId),
        () -> new ClusterVariableRequest(name, null, tenantId, null, null));
  }

  public Either<ProblemDetail, ClusterVariableRequest> toGlobalClusterVariableCreateRequest(
      final CreateClusterVariableRequest request) {
    return RequestMapper.getResult(
        clusterVariableRequestValidator.validateGlobalClusterVariableCreateRequest(request),
        () ->
            new ClusterVariableRequest(
                request.getName(),
                request.getValue(),
                null,
                request.getMetadata(),
                toProtocolKind(request.getKind())));
  }

  public Either<ProblemDetail, ClusterVariableRequest> toGlobalClusterVariableRequest(
      final String name) {
    return RequestMapper.getResult(
        clusterVariableRequestValidator.validateGlobalClusterVariableRequest(name),
        () -> new ClusterVariableRequest(name, null, null, null, null));
  }

  private static @Nullable ClusterVariableKind toProtocolKind(
      final @Nullable ClusterVariableKindEnum kind) {
    if (kind == null) {
      return null;
    }
    return switch (kind) {
      case JSON -> ClusterVariableKind.JSON;
      case SECRET_REFERENCE -> ClusterVariableKind.SECRET_REFERENCE;
    };
  }

  public static List<MetadataValueFilter> toMetadataValueFilters(
      final Map<String, AdvancedMetadataValueFilter> metadata,
      final List<String> validationErrors) {
    final List<MetadataValueFilter> filters = new ArrayList<>();
    for (final var entry : metadata.entrySet()) {
      final String key = entry.getKey();
      if (entry.getValue() == null) {
        validationErrors.add("The filter on metadata key '%s' must not be null.".formatted(key));
        continue;
      }
      final List<Operation<Object>> operations = mapToUntypedOperations().apply(entry.getValue());
      validateMetadataOperations(key, operations, validationErrors);
      final List<UntypedOperation> valueOperations =
          operations.stream().map(ClusterVariableMapper::toMetadataValueOperation).toList();
      filters.add(
          new MetadataValueFilter.Builder().key(key).valueOperations(valueOperations).build());
    }
    return filters;
  }

  /**
   * Builds an {@link UntypedOperation} preserving the JSON scalar type from the value's Java
   * runtime type (as deserialized by Jackson).
   */
  private static UntypedOperation toMetadataValueOperation(final Operation<Object> operation) {
    final ValueTypeEnum type = metadataValueType(operation.value());
    return new UntypedOperation(operation.operator(), operation.values(), type);
  }

  /**
   * Rejects a {@code $exists: false} filter combined with other operations on the same key, which
   * the query transformer cannot represent and would otherwise throw on (surfacing as a 500).
   */
  private static void validateMetadataOperations(
      final String key,
      final List<Operation<Object>> operations,
      final List<String> validationErrors) {
    final boolean hasNotExists =
        operations.stream().anyMatch(op -> op.operator() == Operator.NOT_EXISTS);
    if (hasNotExists && operations.size() > 1) {
      validationErrors.add(
          "The '$exists: false' filter on metadata key '%s' cannot be combined with other operations."
              .formatted(key));
    }
  }

  private static ValueTypeEnum metadataValueType(final @Nullable Object value) {
    if (value == null) {
      return ValueTypeEnum.NULL;
    }
    if (value instanceof Double || value instanceof Float) {
      return ValueTypeEnum.DOUBLE;
    }
    if (value instanceof Number) {
      return ValueTypeEnum.LONG;
    }
    if (value instanceof Boolean) {
      return ValueTypeEnum.BOOLEAN;
    }
    return ValueTypeEnum.STRING;
  }
}
