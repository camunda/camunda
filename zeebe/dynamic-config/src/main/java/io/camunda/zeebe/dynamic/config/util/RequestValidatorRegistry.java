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
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Registry of {@link ClusterConfigurationRequestValidator}s keyed by request type and physical
 * tenant.
 *
 * <p>Validation is optional: {@link #validateRequest(ClusterConfigurationManagementRequest)} is a
 * no-op for request types that have no registered validator applicable to their tenant, unless the
 * request declares {@link ClusterConfigurationManagementRequest#requiresValidation()}, in which
 * case a missing validator fails the request instead of silently skipping validation.
 */
public final class RequestValidatorRegistry {

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
    validators.put(new ValidatorKey(validator.requestType(), tenantId), validator);
  }

  /** Deregisters the validator registered for the given request type and tenant, if any. */
  public void deregisterValidator(
      final @Nullable String tenantId,
      final Class<? extends ClusterConfigurationManagementRequest> requestType) {
    validators.remove(new ValidatorKey(requestType, tenantId));
  }

  /**
   * Validates the request with the validator registered for its type and {@link
   * ClusterConfigurationManagementRequest#physicalTenantId()}, if any. Falls back to a validator
   * registered with a {@code null} tenant (applicable to all tenants) if no tenant-specific one is
   * registered. Blocks until validation completes and propagates any exception thrown by the
   * validator.
   *
   * @param request the request to validate
   * @return the value produced by the registered validator (which may be a different type than the
   *     request, e.g. a rewritten request or a resolved downstream value), or the original request
   *     unchanged if none is registered for its type and tenant
   * @throws IllegalArgumentException if the request requires validation but no validator is
   *     registered for its type and tenant
   */
  @SuppressWarnings("unchecked")
  public Object validateRequest(final ClusterConfigurationManagementRequest request) {
    final var requestType = request.getClass();
    var validator = validators.get(new ValidatorKey(requestType, request.physicalTenantId()));
    if (validator == null) {
      validator = validators.get(new ValidatorKey(requestType, null));
    }
    if (validator != null) {
      return ((ClusterConfigurationRequestValidator<ClusterConfigurationManagementRequest, ?>)
              validator)
          .validate(request);
    }
    if (request.requiresValidation()) {
      throw new IllegalArgumentException(
          "Cannot handle %s: validation is required but no validator is registered for tenant '%s'."
              .formatted(requestType.getSimpleName(), request.physicalTenantId()));
    }
    return request;
  }

  private record ValidatorKey(
      Class<? extends ClusterConfigurationManagementRequest> requestType,
      @Nullable String tenantId) {}

  /** Blocking validation hook handed to the request handler. */
  @FunctionalInterface
  public interface RequestValidator {
    Object validate(ClusterConfigurationManagementRequest request);
  }
}
