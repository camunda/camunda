/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.service.exception.SecondaryStorageUnavailableException;
import io.camunda.service.validation.SecondaryStorageValidator;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.method.HandlerMethod;

@ExtendWith(MockitoExtension.class)
public class SecondaryStorageInterceptorTest {

  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private HandlerMethod handlerMethod;
  @Mock private SecondaryStorageValidator validator;

  private SecondaryStorageInterceptor interceptor;

  @BeforeEach
  void setUp() {
    interceptor = new SecondaryStorageInterceptor(validator);
  }

  @Test
  void shouldAllowRequestWhenSecondaryStorageIsEnabled() throws Exception {
    // given
    when(handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)).thenReturn(true);
    when(handlerMethod.getBeanType()).thenReturn(Object.class);
    // validator doesn't throw exception when secondary storage is enabled

    // when
    final boolean result = interceptor.preHandle(request, response, handlerMethod);

    // then
    assertThat(result).isTrue();
    verify(validator).validateSecondaryStorageEnabled();
  }

  @Test
  void shouldThrowExceptionWhenSecondaryStorageIsDisabled() throws Exception {
    // given
    when(handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)).thenReturn(true);
    when(handlerMethod.getBeanType()).thenReturn(Object.class);
    doThrow(new SecondaryStorageUnavailableException()).when(validator).validateSecondaryStorageEnabled();

    // when/then
    assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
        .isInstanceOf(SecondaryStorageUnavailableException.class);
  }

  @Test
  void shouldAllowRequestWhenAnnotationNotPresent() throws Exception {
    // given
    when(handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)).thenReturn(false);
    when(handlerMethod.getBeanType()).thenReturn(Object.class);

    // when
    final boolean result = interceptor.preHandle(request, response, handlerMethod);

    // then
    assertThat(result).isTrue();
    verify(validator, never()).validateSecondaryStorageEnabled();
  }

  @Test
  void shouldCheckControllerLevelAnnotation() throws Exception {
    // given
    when(handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)).thenReturn(false);
    when(handlerMethod.getBeanType()).thenReturn(AnnotatedController.class);

    // when
    final boolean result = interceptor.preHandle(request, response, handlerMethod);

    // then
    assertThat(result).isTrue();
    verify(validator).validateSecondaryStorageEnabled();
  }

  @Test
  void shouldAllowRequestWhenHandlerIsNotHandlerMethod() throws Exception {
    // given
    final Object notHandlerMethod = new Object();

    // when
    final boolean result = interceptor.preHandle(request, response, notHandlerMethod);

    // then
    assertThat(result).isTrue();
    verify(validator, never()).validateSecondaryStorageEnabled();
  }

  @RequiresSecondaryStorage
  private static class AnnotatedController {
    // Test class with annotation
  }
}