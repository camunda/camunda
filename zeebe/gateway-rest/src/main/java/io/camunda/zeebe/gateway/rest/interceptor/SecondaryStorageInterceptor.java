/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.interceptor;

<<<<<<< HEAD
import static io.camunda.spring.utils.DatabaseTypeUtils.CAMUNDA_DATABASE_TYPE_NONE;

=======
import io.camunda.cluster.SecondaryStorageReadiness;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.service.exception.SecondaryStorageDegradedException;
>>>>>>> f241add9 (fix: resolve type in SecondaryStorageInterceptor using Unified Configuration)
import io.camunda.service.exception.SecondaryStorageUnavailableException;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
<<<<<<< HEAD
import org.springframework.beans.factory.annotation.Value;
=======
import java.util.function.Function;
import org.springframework.http.HttpHeaders;
>>>>>>> f241add9 (fix: resolve type in SecondaryStorageInterceptor using Unified Configuration)
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
<<<<<<< HEAD
 * Interceptor that validates secondary storage availability for endpoints requiring it. When
 * secondary storage is not configured (camunda.database.type=none), requests to endpoints marked
 * with {@link RequiresSecondaryStorage} will be rejected with HTTP 403 Forbidden.
=======
 * Interceptor that validates secondary storage readiness for endpoints requiring it, i.e. marked
 * with {@link RequiresSecondaryStorage}.
 *
 * <ul>
 *   <li>HTTP 403 Forbidden when secondary storage is not configured at all
 *       (camunda.data.secondary-storage.type = none).
 *   <li>HTTP 503 Service Unavailable, with a {@code Retry-After} hint, when secondary storage is
 *       configured but the request's physical tenant's secondary storage is currently degraded (see
 *       {@link SecondaryStorageReadiness}).
 * </ul>
>>>>>>> f241add9 (fix: resolve type in SecondaryStorageInterceptor using Unified Configuration)
 */
@Component
public class SecondaryStorageInterceptor implements HandlerInterceptor {

<<<<<<< HEAD
  private final boolean secondaryStorageDisabled;

  public SecondaryStorageInterceptor(
      @Value("${camunda.database.type:elasticsearch}") final String databaseType) {
    secondaryStorageDisabled = CAMUNDA_DATABASE_TYPE_NONE.equalsIgnoreCase(databaseType);
=======
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
>>>>>>> f241add9 (fix: resolve type in SecondaryStorageInterceptor using Unified Configuration)
  }

  @Override
  public boolean preHandle(
      final HttpServletRequest request, final HttpServletResponse response, final Object handler) {

    if (handler instanceof final HandlerMethod handlerMethod) {
      final boolean requiresSecondaryStorage =
          handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)
              || handlerMethod.getBeanType().isAnnotationPresent(RequiresSecondaryStorage.class);

      if (requiresSecondaryStorage && secondaryStorageDisabled) {
        throw new SecondaryStorageUnavailableException();
      }
    }

    return true;
  }
<<<<<<< HEAD
=======

  private static boolean requiresSecondaryStorage(final HandlerMethod handlerMethod) {
    return handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)
        || handlerMethod.getBeanType().isAnnotationPresent(RequiresSecondaryStorage.class);
  }

  private void validateSecondaryStorageReady(
      final HttpServletRequest request, final HttpServletResponse response) {
    final String physicalTenantId = PhysicalTenantContext.current();
    if (databaseTypeProvider.apply(physicalTenantId) == DatabaseType.NONE) {
      throw new SecondaryStorageUnavailableException();
    }
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
>>>>>>> f241add9 (fix: resolve type in SecondaryStorageInterceptor using Unified Configuration)
}
