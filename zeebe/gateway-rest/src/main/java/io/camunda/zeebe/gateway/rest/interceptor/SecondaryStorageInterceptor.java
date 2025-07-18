/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.interceptor;

import io.camunda.service.validation.SecondaryStorageValidator;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
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

  private final SecondaryStorageValidator secondaryStorageValidator;

  @Autowired
  public SecondaryStorageInterceptor(final SecondaryStorageValidator secondaryStorageValidator) {
    this.secondaryStorageValidator = secondaryStorageValidator;
  }

  @Override
  public boolean preHandle(
      final HttpServletRequest request, final HttpServletResponse response, final Object handler) {

    if (handler instanceof final HandlerMethod handlerMethod) {
      final boolean requiresSecondaryStorage =
          handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)
              || handlerMethod.getBeanType().isAnnotationPresent(RequiresSecondaryStorage.class);

      if (requiresSecondaryStorage) {
        secondaryStorageValidator.validateSecondaryStorageEnabled();
      }
    }

    return true;
  }
}
