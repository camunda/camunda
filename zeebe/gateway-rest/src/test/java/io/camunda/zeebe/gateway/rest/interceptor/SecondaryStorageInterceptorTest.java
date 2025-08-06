/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.interceptor;

import static io.camunda.spring.utils.DatabaseTypeUtils.CAMUNDA_DATABASE_TYPE_NONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.camunda.service.exception.SecondaryStorageUnavailableException;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

@SuppressWarnings({"unchecked", "rawtypes"})
class SecondaryStorageInterceptorTest {

  private HttpServletRequest request;
  private HttpServletResponse response;
  private HandlerMethod handlerMethod;

  @BeforeEach
  void setUp() {
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
    handlerMethod = mock(HandlerMethod.class);
  }

  @Test
  void shouldAllowWhenNoAnnotation() {
    when(handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)).thenReturn(false);
    when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);

    final var interceptor = new SecondaryStorageInterceptor("elasticsearch");
    final boolean result = interceptor.preHandle(request, response, handlerMethod);
    assertThat(result).isTrue();
  }

  @Test
  void shouldAllowWhenSecondaryStorageEnabled() {
    when(handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)).thenReturn(true);
    when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);

    final var interceptor = new SecondaryStorageInterceptor("elasticsearch");
    final boolean result = interceptor.preHandle(request, response, handlerMethod);
    assertThat(result).isTrue();
  }

  @Test
  void shouldThrowWhenSecondaryStorageDisabledAndAnnotationPresent() {
    when(handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)).thenReturn(true);
    when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);

    final var interceptor = new SecondaryStorageInterceptor(CAMUNDA_DATABASE_TYPE_NONE);
    assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
        .isInstanceOf(SecondaryStorageUnavailableException.class);
  }
}
