/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestValidator;
import io.camunda.zeebe.util.Either;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry of {@link ClusterConfigurationRequestValidator}s keyed by request type class and
 * physical tenant.
 */
@NullMarked
public final class RequestValidatorRegistry {
  private static final Logger LOG = LoggerFactory.getLogger(RequestValidatorRegistry.class);
  private final Map<ValidatorKey, ClusterConfigurationRequestValidator<?, ?>> validators =
      new HashMap<>();

  /**
   * Registers a validator for the given request type and tenant.
   *
   * @param tenantId the physical tenant this validator applies to, or {@code null} to apply it to
   *     every tenant that has no more specific, tenant-scoped validator registered
   */
  public void registerValidator(
      final @Nullable String tenantId, final ClusterConfigurationRequestValidator<?, ?> validator) {
    if (validators.put(new ValidatorKey(validator.requestType(), tenantId), validator) != null) {
      LOG.warn(
          "Validator for request type {} and tenant {} has been overwritten",
          validator.requestType(),
          tenantId);
    }
  }

  /** Deregisters the validator registered for the given request type and tenant, if any. */
  public void deregisterValidator(
      final @Nullable String tenantId,
      final Class<? extends ClusterConfigurationManagementRequest> requestType) {
    if (validators.remove(new ValidatorKey(requestType, tenantId)) == null) {
      LOG.warn("No validator registered for request type {} and tenant {}", requestType, tenantId);
    }
  }

  @SuppressWarnings("unchecked")
  public <T extends ClusterConfigurationManagementRequest>
      Optional<ClusterConfigurationRequestValidator<T, ?>> getValidator(
          final String physicalTenantId, final Class<T> requestType) {
    final var tenantValidator = new ValidatorKey(requestType, physicalTenantId);
    final var globalValidator = new ValidatorKey(requestType, null);
    return findValidator(tenantValidator)
        .or(() -> findValidator(globalValidator))
        .map(v -> (ClusterConfigurationRequestValidator<T, ?>) v);
  }

  /**
   * Retrieve all validators registered for the given request type. For cluster-wide operations the
   * coordinator should be able to access all validators registered for the given request type.
   *
   * @param requestClass The request type under which the validators are registered
   * @return A set of validators registered for the given request type
   * @param <T> The validator's source request type
   */
  public <T extends ClusterConfigurationManagementRequest>
      Set<ClusterConfigurationRequestValidator<?, ?>> validatorsForRequest(
          final Class<T> requestClass) {
    return validators.values().stream()
        .filter(validator -> requestClass.equals(validator.requestType()))
        .collect(Collectors.toSet());
  }

  private Optional<ClusterConfigurationRequestValidator<?, ?>> findValidator(
      final ValidatorKey key) {
    return Optional.ofNullable(validators.get(key));
  }

  private record ValidatorKey(
      Class<? extends ClusterConfigurationManagementRequest> requestType,
      @Nullable String tenantId) {}

  /** Invoke a ClusterConfigurationManagementRequest validator for a given request */
  @FunctionalInterface
  public interface RequestValidator {
    Either<Exception, ClusterConfigurationManagementRequest> validate(
        ClusterConfigurationManagementRequest request);
  }
}
