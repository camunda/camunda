/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.interceptor;

import io.camunda.cluster.SecondaryStorageReadiness;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.service.exception.SecondaryStorageDegradedException;
import io.camunda.service.exception.SecondaryStorageTypeNotSupportedException;
import io.camunda.service.exception.SecondaryStorageUnavailableException;
import io.camunda.spring.utils.PhysicalTenantContext;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor that validates secondary storage readiness for endpoints requiring it, i.e. marked
 * with {@link RequiresSecondaryStorage}.
 *
 * <ul>
 *   <li>HTTP 403 Forbidden when secondary storage is not configured at all
 *       (camunda.data.secondary-storage.type = none).
 *   <li>HTTP 403 Forbidden when the endpoint declares the secondary storages it supports and the
 *       tenant's configured one is not among them.
 *   <li>HTTP 503 Service Unavailable, with a {@code Retry-After} hint, when secondary storage is
 *       configured but the request's physical tenant's secondary storage is currently degraded (see
 *       {@link SecondaryStorageReadiness}).
 * </ul>
 */
@Component
public class SecondaryStorageInterceptor implements HandlerInterceptor {

  /**
   * Degradation is expected to be transient (e.g. schema initialization still in progress), so hint
   * callers to retry rather than treat the 503 as terminal.
   */
  private static final String RETRY_AFTER_SECONDS = "5";

  private final Function<String, DatabaseType> databaseTypeProvider;
  private final SecondaryStorageReadiness secondaryStorageReadiness;

  public SecondaryStorageInterceptor(
      final Function<String, DatabaseType> databaseTypeProvider,
      final SecondaryStorageReadiness secondaryStorageReadiness) {
    this.databaseTypeProvider = databaseTypeProvider;
    this.secondaryStorageReadiness = secondaryStorageReadiness;
  }

  @Override
  public boolean preHandle(
      final HttpServletRequest request, final HttpServletResponse response, final Object handler) {

    if (handler instanceof final HandlerMethod handlerMethod) {
      final var annotation = requiresSecondaryStorage(handlerMethod);
      if (annotation != null) {
        final String physicalTenantId = PhysicalTenantContext.current();
        validateSecondaryStorageType(annotation, databaseTypeProvider.apply(physicalTenantId));
        validateSecondaryStorageReady(request, response, physicalTenantId);
      }
    }

    return true;
  }

  private static @Nullable RequiresSecondaryStorage requiresSecondaryStorage(
      final HandlerMethod handlerMethod) {
    final var methodAnnotation = handlerMethod.getMethodAnnotation(RequiresSecondaryStorage.class);
    return methodAnnotation != null
        ? methodAnnotation
        : handlerMethod.getBeanType().getAnnotation(RequiresSecondaryStorage.class);
  }

  private static void validateSecondaryStorageType(
      final RequiresSecondaryStorage annotation, final DatabaseType databaseType) {
    if (databaseType == DatabaseType.NONE) {
      throw new SecondaryStorageUnavailableException();
    }
    final var supported = List.of(annotation.value());
    if (!supported.isEmpty() && !supported.contains(databaseType)) {
      throw new SecondaryStorageTypeNotSupportedException(
          supported.stream().map(DatabaseType::toString).toList(), databaseType.toString());
    }
  }

  private void validateSecondaryStorageReady(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final String physicalTenantId) {
    // Only checked on the initial dispatch: CompletableFuture-returning controllers resume on an
    // ASYNC re-dispatch, at which point the result is already computed and must not be rejected a
    // second time based on the (possibly since-changed) readiness state.
    if (request.getDispatcherType() != DispatcherType.REQUEST) {
      return;
    }

    if (!secondaryStorageReadiness.isReady(physicalTenantId)) {
      // Set before throwing: the exception handler only writes status and problem-detail body, so
      // headers already set on the response survive.
      response.setHeader(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS);
      throw new SecondaryStorageDegradedException(physicalTenantId);
    }
  }
}
