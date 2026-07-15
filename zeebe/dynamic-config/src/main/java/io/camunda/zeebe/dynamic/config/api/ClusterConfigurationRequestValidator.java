/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

/**
 * Validates a specific type of {@link ClusterConfigurationManagementRequest} before it is applied.
 *
 * <p>Validation is <em>blocking</em>: implementations run synchronously and signal an invalid
 * request by throwing. The thrown exception (and its message) is propagated to the caller by the
 * {@link io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestsHandler
 * handler}. Prefer {@link IllegalArgumentException} for user-facing validation errors so they are
 * surfaced as an invalid-request response.
 *
 * <p>Validators are registered on the {@code ClusterConfigurationManagerService} and are entirely
 * optional: a request type without a registered validator is not validated.
 *
 * @param <T> the concrete request type this validator handles
 * @param <R> the type produced by validation; implementations that don't need to rewrite or resolve
 *     the request into a different shape should use {@code T} and return it unchanged
 */
public interface ClusterConfigurationRequestValidator<
    T extends ClusterConfigurationManagementRequest, R> {

  /** The concrete request type this validator is registered for. */
  Class<T> requestType();

  /**
   * Validates the given request, throwing if it is invalid.
   *
   * @param request the request to validate; guaranteed to be an instance of {@link #requestType()}
   * @return the value to use downstream
   * @throws RuntimeException (typically {@link IllegalArgumentException}) if the request is invalid
   */
  R validate(T request);
}
