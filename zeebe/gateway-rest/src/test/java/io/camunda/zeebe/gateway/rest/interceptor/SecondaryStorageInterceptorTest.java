/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.connect.configuration.DatabaseConfig;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.interceptor.SecondaryStorageInterceptor.DatabaseProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
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

  private SecondaryStorageInterceptor interceptor;
  private DatabaseProperties databaseProperties;
  private StringWriter responseWriter;

  @BeforeEach
  void setUp() throws Exception {
    databaseProperties = new DatabaseProperties();
    interceptor = new SecondaryStorageInterceptor(databaseProperties);
    responseWriter = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
  }

  @Test
  void shouldAllowRequestWhenSecondaryStorageIsEnabled() throws Exception {
    // given
    databaseProperties.setType(DatabaseConfig.ELASTICSEARCH);
    when(handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)).thenReturn(true);

    // when
    final boolean result = interceptor.preHandle(request, response, handlerMethod);

    // then
    assertThat(result).isTrue();
  }

  @Test
  void shouldBlockRequestWhenSecondaryStorageIsDisabledAndAnnotationPresent() throws Exception {
    // given
    databaseProperties.setType(DatabaseConfig.NONE);
    when(handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)).thenReturn(true);

    // when
    final boolean result = interceptor.preHandle(request, response, handlerMethod);

    // then
    assertThat(result).isFalse();
    assertThat(responseWriter.toString()).contains("Secondary Storage Required");
    assertThat(responseWriter.toString()).contains("headless mode");
  }

  @Test
  void shouldAllowRequestWhenSecondaryStorageIsDisabledButAnnotationNotPresent() throws Exception {
    // given
    databaseProperties.setType(DatabaseConfig.NONE);
    when(handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)).thenReturn(false);
    when(handlerMethod.getBeanType()).thenReturn(Object.class);

    // when
    final boolean result = interceptor.preHandle(request, response, handlerMethod);

    // then
    assertThat(result).isTrue();
  }

  @Test
  void shouldBlockRequestWhenSecondaryStorageIsDisabledAndClassAnnotationPresent() throws Exception {
    // given
    databaseProperties.setType(DatabaseConfig.NONE);
    when(handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)).thenReturn(false);
    when(handlerMethod.getBeanType()).thenReturn(AnnotatedTestController.class);

    // when
    final boolean result = interceptor.preHandle(request, response, handlerMethod);

    // then
    assertThat(result).isFalse();
    assertThat(responseWriter.toString()).contains("Secondary Storage Required");
  }

  @Test
  void shouldAllowRequestWhenHandlerIsNotHandlerMethod() throws Exception {
    // given
    databaseProperties.setType(DatabaseConfig.NONE);
    final Object nonHandlerMethod = new Object();

    // when
    final boolean result = interceptor.preHandle(request, response, nonHandlerMethod);

    // then
    assertThat(result).isTrue();
  }

  @RequiresSecondaryStorage
  static class AnnotatedTestController {
    public void testMethod() {}
  }
}