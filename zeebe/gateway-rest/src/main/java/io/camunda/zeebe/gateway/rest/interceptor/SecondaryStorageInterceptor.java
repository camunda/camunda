/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.interceptor;

import io.camunda.service.exception.SecondaryStorageUnavailableException;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor that validates secondary storage availability for endpoints requiring it. When
 * secondary storage is not configured (camunda.database.type=none), requests to endpoints marked
 * with {@link RequiresSecondaryStorage} will be rejected with HTTP 403 Forbidden.
 */
@Component
public class SecondaryStorageInterceptor implements HandlerInterceptor {

  private final boolean secondaryStorageDisabled;

  public SecondaryStorageInterceptor(
      @Value("${camunda.database.type:elasticsearch}") final String databaseType) {
    secondaryStorageDisabled = "none".equalsIgnoreCase(databaseType);
  }

  @Override
  public boolean preHandle(
      final HttpServletRequest request, final HttpServletResponse response, final Object handler) {

    System.out.println(
        "*** SecondaryStorageInterceptor.preHandle() - Request: " + request.getRequestURI());
    System.out.println("*** Secondary storage disabled: " + secondaryStorageDisabled);

    if (handler instanceof final HandlerMethod handlerMethod) {
      final boolean requiresSecondaryStorage =
          handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)
              || handlerMethod.getBeanType().isAnnotationPresent(RequiresSecondaryStorage.class);

      System.out.println("*** Handler: " + handlerMethod.getMethod().getName());
      System.out.println("*** Requires secondary storage: " + requiresSecondaryStorage);

      if (requiresSecondaryStorage && secondaryStorageDisabled) {
        System.out.println("*** THROWING SecondaryStorageUnavailableException!");
        throw new SecondaryStorageUnavailableException();
      }
    }

    System.out.println("*** SecondaryStorageInterceptor allowing request");
    return true;
  }
}
