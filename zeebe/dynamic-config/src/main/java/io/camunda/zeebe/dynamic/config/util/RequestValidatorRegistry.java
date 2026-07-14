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

/**
 * Registry of {@link ClusterConfigurationRequestValidator}s keyed by request type.
 *
 * <p>Validation is optional: {@link #validateRequest(ClusterConfigurationManagementRequest)} is a
 * no-op for request types that have no registered validator. Validators may be registered and
 * deregistered at runtime (e.g. when a broker enters or leaves recovery mode), so the backing map
 * is concurrent.
 */
public final class RequestValidatorRegistry {

  private final Map<
          Class<? extends ClusterConfigurationManagementRequest>,
          ClusterConfigurationRequestValidator<? extends ClusterConfigurationManagementRequest>>
      validators = new HashMap<>();

  public void registerValidator(final ClusterConfigurationRequestValidator<?> validator) {
    validators.put(validator.requestType(), validator);
  }

  public void deregisterValidator(
      final Class<? extends ClusterConfigurationManagementRequest> requestType) {
    validators.remove(requestType);
  }

  /**
   * Validates the request with the validator registered for its type, if any. Blocks until
   * validation completes and propagates any exception thrown by the validator.
   *
   * @param request the request to validate
   */
  public void validateRequest(final ClusterConfigurationManagementRequest request) {
    final var validator = validators.get(request.getClass());
    if (validator != null) {
      validator.validate(request);
    }
  }

  /** Blocking validation hook handed to the request handler. */
  @FunctionalInterface
  public interface RequestValidator {
    void validate(ClusterConfigurationManagementRequest request);
  }
}
