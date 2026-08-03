/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.interceptor;

import static io.camunda.spring.utils.DatabaseTypeUtils.CAMUNDA_DATABASE_TYPE_NONE;

import io.camunda.cluster.SecondaryStorageReadiness;
import io.camunda.service.exception.SecondaryStorageDegradedException;
import io.camunda.service.exception.SecondaryStorageUnavailableException;
import io.camunda.spring.utils.PhysicalTenantContext;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor that validates secondary storage readiness for endpoints requiring it, i.e. marked
 * with {@link RequiresSecondaryStorage}.
 *
 * <ul>
 *   <li>HTTP 403 Forbidden when secondary storage is not configured at all (camunda.database.type =
 *       none).
 *   <li>HTTP 503 Service Unavailable, with a {@code Retry-After} hint, when secondary storage is
 *       configured but the request's physical tenant's secondary storage is currently degraded (see
 *       {@link SecondaryStorageReadiness}).
 * </ul>
 *
 * NOTE: This is not a @Component. The actual bean is created with the Unified Configuration system
 * in `SecondaryStorageInterceptorOverride.java`.
 */
public class SecondaryStorageInterceptor implements HandlerInterceptor {

  /**
   * Degradation is expected to be transient (e.g. schema initialization still in progress), so hint
   * callers to retry rather than treat the 503 as terminal.
   */
  private static final String RETRY_AFTER_SECONDS = "5";

  private static final String DEFAULT_DATABASE_TYPE = "elasticsearch";

  private final SecondaryStorageReadiness secondaryStorageReadiness;

  private String databaseType;
  private boolean secondaryStorageDisabled;

  public SecondaryStorageInterceptor(final SecondaryStorageReadiness secondaryStorageReadiness) {
    setDatabaseType(DEFAULT_DATABASE_TYPE);
    this.secondaryStorageReadiness = secondaryStorageReadiness;
  }

  @Override
  public boolean preHandle(
      final HttpServletRequest request, final HttpServletResponse response, final Object handler) {

    if (handler instanceof final HandlerMethod handlerMethod
        && requiresSecondaryStorage(handlerMethod)) {
      validateSecondaryStorageReady(request, response);
    }

    return true;
  }

  public void setDatabaseType(final String databaseType) {
    this.databaseType = databaseType;
    secondaryStorageDisabled = CAMUNDA_DATABASE_TYPE_NONE.equalsIgnoreCase(databaseType);
  }

  private static boolean requiresSecondaryStorage(final HandlerMethod handlerMethod) {
    return handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)
        || handlerMethod.getBeanType().isAnnotationPresent(RequiresSecondaryStorage.class);
  }

  private void validateSecondaryStorageReady(
      final HttpServletRequest request, final HttpServletResponse response) {
    if (secondaryStorageDisabled) {
      throw new SecondaryStorageUnavailableException();
    }
    // Only checked on the initial dispatch: CompletableFuture-returning controllers resume on an
    // ASYNC re-dispatch, at which point the result is already computed and must not be rejected a
    // second time based on the (possibly since-changed) readiness state.
    if (request.getDispatcherType() != DispatcherType.REQUEST) {
      return;
    }
    final String physicalTenantId = PhysicalTenantContext.current();
    if (!secondaryStorageReadiness.isReady(physicalTenantId)) {
      // Set before throwing: the exception handler only writes status and problem-detail body, so
      // headers already set on the response survive.
      response.setHeader(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS);
      throw new SecondaryStorageDegradedException(physicalTenantId);
    }
  }
}
