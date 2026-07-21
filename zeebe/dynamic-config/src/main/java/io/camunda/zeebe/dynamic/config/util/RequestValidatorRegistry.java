/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestValidator;
import io.camunda.zeebe.util.Either;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry of {@link ClusterConfigurationRequestValidator}s keyed by request type and physical
 * tenant.
 *
 * <p>Validation is optional: {@link #validateRequest(ClusterConfigurationManagementRequest)} is a
 * no-op for request types that have no registered validator applicable to their tenant, unless the
 * request declares {@link ClusterConfigurationManagementRequest#requiresValidation()}, in which
 * case a missing validator fails the request instead of silently skipping validation. The internal
 * map registry is not thread-safe and should be accessed within a managed actor's context.
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

  /**
   * Validates the request with the validator registered for its type and {@link
   * ClusterConfigurationManagementRequest#physicalTenantId()}, if any. Falls back to a validator
   * registered with a {@code null} tenant (applicable to all tenants) if no tenant-specific one is
   * registered. Blocks until validation completes and propagates any exception thrown by the
   * validator.
   *
   * @param request the request to validate
   * @return an {@link Either.Right} with the request produced by the registered validator (which
   *     may be a rewritten request of a different concrete type than the input), or the original
   *     request unchanged if none is registered for its type and tenant; an {@link Either.Left}
   *     with an {@link InvalidRequest} if the request requires validation but no validator is
   *     registered for its type and tenant, or if the registered validator rejects it
   */
  @SuppressWarnings("unchecked")
  public Either<ClusterConfigurationRequestFailedException, ClusterConfigurationManagementRequest>
      validateRequest(final ClusterConfigurationManagementRequest request) {
    final var requestType = request.getClass();
    final var validator =
        findValidator(new ValidatorKey(requestType, request.physicalTenantId()))
            .or(() -> findValidator(new ValidatorKey(requestType, null)));

    if (validator.isPresent()) {
      return ((ClusterConfigurationRequestValidator<
                  ClusterConfigurationManagementRequest, ClusterConfigurationManagementRequest>)
              validator.get())
          .validate(request);
    }
    if (request.requiresValidation()) {
      return Either.left(
          new InvalidRequest(
              "Cannot handle %s: validation is required but no validator is registered for tenant '%s'."
                  .formatted(requestType.getSimpleName(), request.physicalTenantId())));
    }
    return Either.right(request);
  }

  private Optional<ClusterConfigurationRequestValidator<?, ?>> findValidator(
      final ValidatorKey key) {
    return Optional.ofNullable(validators.get(key));
  }

  private record ValidatorKey(
      Class<? extends ClusterConfigurationManagementRequest> requestType,
      @Nullable String tenantId) {}

  /** Blocking validation hook handed to the request handler. */
  @FunctionalInterface
  public interface RequestValidator {
    Either<ClusterConfigurationRequestFailedException, ClusterConfigurationManagementRequest>
        validate(ClusterConfigurationManagementRequest request);
  }
}
