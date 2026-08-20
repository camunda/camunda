/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.cluster.SecondaryStorageReadiness;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.service.exception.SecondaryStorageUnavailableException;
import io.camunda.spring.utils.PhysicalTenantContext;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.interceptor.SecondaryStorageInterceptor;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  UnifiedConfigurationHelper.class,
  DatabaseTypeProviderConfiguration.class,
  SecondaryStorageInterceptor.class,
  SecondaryStorageInterceptorConfigurationTest.TestConfiguration.class,
})
class SecondaryStorageInterceptorConfigurationTest {

  @Autowired private SecondaryStorageInterceptor secondaryStorageInterceptor;

  @AfterEach
  void tearDown() {
    RequestContextHolder.resetRequestAttributes();
  }

  private static HttpServletRequest requestDispatch() {
    final var request = mock(HttpServletRequest.class);
    when(request.getDispatcherType()).thenReturn(DispatcherType.REQUEST);
    when(request.getAttribute(PhysicalTenantContext.REQUEST_ATTRIBUTE_PHYSICAL_TENANT_ID))
        .thenReturn(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    return request;
  }

  private static HandlerMethod requiresSecondaryStorageHandler() throws NoSuchMethodException {
    final var controller = new SecondaryStorageEndpoint();
    return new HandlerMethod(controller, controller.getClass().getMethod("handle"));
  }

  @RequiresSecondaryStorage
  static class SecondaryStorageEndpoint {
    public String handle() {
      return "ok";
    }
  }

  @Configuration
  static class TestConfiguration {
    @Bean
    PhysicalTenantResolver physicalTenantResolver(
        final Environment environment, final Camunda camunda) {
      return PhysicalTenantResolver.of(environment, camunda);
    }

    @Bean
    SecondaryStorageReadiness secondaryStorageReadiness() {
      return SecondaryStorageReadiness.ALWAYS_READY;
    }
  }

  @Nested
  @TestPropertySource(properties = {"camunda.data.secondary-storage.type=rdbms"})
  class WithMatchingLegacyAndUnifiedRdbmsType {
    @Test
    void shouldAllowRequestsRequiringSecondaryStorageWhenBothPropertiesAgree() throws Exception {
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
    void shouldRejectRequestsRequiringSecondaryStorageWhenBothPropertiesAgree() throws Exception {
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
    void shouldAllowRequestsRequiringSecondaryStorage() throws Exception {
      final boolean result =
          secondaryStorageInterceptor.preHandle(
              requestDispatch(),
              mock(HttpServletResponse.class),
              requiresSecondaryStorageHandler());

      assertThat(result).isTrue();
    }
  }
}
