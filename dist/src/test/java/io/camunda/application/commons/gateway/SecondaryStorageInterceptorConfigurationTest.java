/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.application.commons.configuration.DatabaseTypeSupplierConfiguration;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.service.exception.SecondaryStorageUnavailableException;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.interceptor.SecondaryStorageInterceptor;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  UnifiedConfigurationHelper.class,
  SecondaryStorageInterceptor.class,
  DatabaseTypeSupplierConfiguration.class,
})
class SecondaryStorageInterceptorConfigurationTest {

  @Autowired private SecondaryStorageInterceptor secondaryStorageInterceptor;
  @Autowired private Environment environment;

  @BeforeEach
  void setUp() {
    // The UnifiedConfigurationHelper pins the environment in a static field. Each nested test uses
    // its own Spring context (and thus its own property sources), so we must re-pin the current
    // context's environment before each test to avoid leaking a sibling context's environment.
    UnifiedConfigurationHelper.setCustomEnvironment(environment);
  }

  @AfterEach
  void tearDown() {
    RequestContextHolder.resetRequestAttributes();
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  private static HttpServletRequest requestDispatch() {
    final var request = mock(HttpServletRequest.class);
    when(request.getDispatcherType()).thenReturn(DispatcherType.REQUEST);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    return request;
  }

  private static HandlerMethod requiresSecondaryStorageHandler() {
    final var handlerMethod = mock(HandlerMethod.class);
    when(handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)).thenReturn(true);
    return handlerMethod;
  }

  @Nested
  @TestPropertySource(properties = {"camunda.data.secondary-storage.type=rdbms"})
  class WithMatchingLegacyAndUnifiedRdbmsType {
    @Test
    void shouldAllowRequestsRequiringSecondaryStorageWhenBothPropertiesAgree() {
      final boolean result =
          secondaryStorageInterceptor.preHandle(
              requestDispatch(),
              mock(HttpServletResponse.class),
              requiresSecondaryStorageHandler());

      assertThat(result).isTrue();
    }
  }

  @Nested
  @TestPropertySource(properties = {"camunda.data.secondary-storage.type=none"})
  class WithMatchingLegacyAndUnifiedNoneType {
    @Test
    void shouldRejectRequestsRequiringSecondaryStorageWhenBothPropertiesAgree() {
      assertThatThrownBy(
              () ->
                  secondaryStorageInterceptor.preHandle(
                      requestDispatch(),
                      mock(HttpServletResponse.class),
                      requiresSecondaryStorageHandler()))
          .isInstanceOf(SecondaryStorageUnavailableException.class);
    }
  }

  @Nested
  @TestPropertySource(properties = "camunda.database.type=elasticsearch")
  class WithOnlyLegacySetMatchingUnifiedDefault {
    @Test
    void shouldAllowRequestsRequiringSecondaryStorage() {
      final boolean result =
          secondaryStorageInterceptor.preHandle(
              requestDispatch(),
              mock(HttpServletResponse.class),
              requiresSecondaryStorageHandler());

      assertThat(result).isTrue();
    }
  }
}
