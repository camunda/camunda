/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.camunda.zeebe.util.Either;
import org.jspecify.annotations.NullMarked;

/**
 * Validates a specific type of {@link ClusterConfigurationManagementRequest} before it is applied.
 *
 * <p>Validation is <em>blocking</em>: implementations run synchronously and signal an invalid
 * request with an {@link Either.Left}, typically an {@link
 * ClusterConfigurationRequestFailedException.InvalidRequest}, which is propagated to the caller by
 * the {@link ClusterConfigurationManagementRequestsHandler handler}.
 *
 * <p>Validators are registered per physical tenant on the {@code
 * ClusterConfigurationManagerService} and are entirely optional: a request type without a
 * registered validator for its tenant is not validated.
 *
 * @param <T> the concrete request type this validator handles
 * @param <R> the request type produced by validation; implementations that don't need to rewrite
 *     the request should use {@code T} and return it unchanged
 */
@NullMarked
public interface ClusterConfigurationRequestValidator<
    T extends ClusterConfigurationManagementRequest,
    R extends ClusterConfigurationManagementRequest> {

  /** The concrete request type this validator is registered for. */
  Class<T> requestType();

  /**
   * Validates the given request.
   *
   * @param request the request to validate; guaranteed to be an instance of {@link #requestType()}
   * @return an {@link Either.Right} with the value to use downstream, or an {@link Either.Left}
   *     with the exception to propagate if the request is invalid
   */
  Either<Exception, R> validate(T request);
}
