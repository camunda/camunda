/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.interceptor;

import static io.camunda.spring.utils.DatabaseTypeUtils.CAMUNDA_DATABASE_TYPE_NONE;

import io.camunda.cluster.PhysicalTenantAvailability;
import io.camunda.service.exception.PhysicalTenantUnavailableException;
import io.camunda.service.exception.SecondaryStorageUnavailableException;
import io.camunda.spring.utils.PhysicalTenantContext;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor that validates secondary storage availability for endpoints requiring it, i.e. marked
 * with {@link RequiresSecondaryStorage}.
 *
 * <ul>
 *   <li>HTTP 403 Forbidden when secondary storage is not configured at all (camunda.database.type =
 *       none).
 *   <li>HTTP 503 Service Unavailable when secondary storage is configured but the request's
 *       physical tenant is currently degraded (see {@link PhysicalTenantAvailability}).
 * </ul>
 */
@Component
public class SecondaryStorageInterceptor implements HandlerInterceptor {

  private final boolean secondaryStorageDisabled;
  private final PhysicalTenantAvailability physicalTenantAvailability;

  public SecondaryStorageInterceptor(
      @Value("${camunda.database.type:elasticsearch}") final String databaseType,
      final PhysicalTenantAvailability physicalTenantAvailability) {
    secondaryStorageDisabled = CAMUNDA_DATABASE_TYPE_NONE.equalsIgnoreCase(databaseType);
    this.physicalTenantAvailability = physicalTenantAvailability;
  }

  @Override
  public boolean preHandle(
      final HttpServletRequest request, final HttpServletResponse response, final Object handler) {

    if (handler instanceof final HandlerMethod handlerMethod
        && requiresSecondaryStorage(handlerMethod)) {
      validateSecondaryStorageAvailable(request);
    }

    return true;
  }

  private static boolean requiresSecondaryStorage(final HandlerMethod handlerMethod) {
    return handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)
        || handlerMethod.getBeanType().isAnnotationPresent(RequiresSecondaryStorage.class);
  }

  private void validateSecondaryStorageAvailable(final HttpServletRequest request) {
    if (secondaryStorageDisabled) {
      throw new SecondaryStorageUnavailableException();
    }
    // Only checked on the initial dispatch: CompletableFuture-returning controllers resume on an
    // ASYNC re-dispatch, at which point the result is already computed and must not be rejected a
    // second time based on the (possibly since-changed) availability state.
    if (request.getDispatcherType() != DispatcherType.REQUEST) {
      return;
    }
    final String physicalTenantId = PhysicalTenantContext.current();
    if (!physicalTenantAvailability.isServiceable(physicalTenantId)) {
      throw new PhysicalTenantUnavailableException(physicalTenantId);
    }
  }
}
