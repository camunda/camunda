/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.cluster.SecondaryStorageReadiness;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beans.LegacySecondaryStorageInterceptor;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  UnifiedConfigurationHelper.class,
  LegacySecondaryStorageInterceptor.class,
  SecondaryStorageInterceptorOverride.class,
  SecondaryStorageInterceptorOverrideTest.SecondaryStorageReadinessTestConfig.class,
})
class SecondaryStorageInterceptorOverrideTest {

  @Configuration
  static class SecondaryStorageReadinessTestConfig {
    @Bean
    SecondaryStorageReadiness secondaryStorageReadiness() {
      return SecondaryStorageReadiness.ALWAYS_READY;
    }
  }

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

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static HandlerMethod requiresSecondaryStorageHandler() {
    final var handlerMethod = mock(HandlerMethod.class);
    when(handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)).thenReturn(true);
    return handlerMethod;
  }

  @Nested
  class WithDefaultConfiguration {
    @Autowired private SecondaryStorageInterceptor secondaryStorageInterceptor;
    @Autowired private LegacySecondaryStorageInterceptor legacySecondaryStorageInterceptor;

    @Test
    void shouldExposeOverrideAsThePrimaryBean() {
      assertThat(secondaryStorageInterceptor)
          .isNotSameAs(legacySecondaryStorageInterceptor)
          .isNotInstanceOf(LegacySecondaryStorageInterceptor.class);
    }

    @Test
    void shouldDefaultToElasticsearchDatabaseType() {
      final boolean result =
          secondaryStorageInterceptor.preHandle(
              requestDispatch(),
              mock(HttpServletResponse.class),
              requiresSecondaryStorageHandler());

      assertThat(result).isTrue();
    }
  }

  @Nested
  @TestPropertySource(properties = "camunda.data.secondary-storage.type=opensearch")
  class WithOpensearchType {
    @Autowired private SecondaryStorageInterceptor secondaryStorageInterceptor;

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

  @Nested
  @TestPropertySource(properties = "camunda.data.secondary-storage.type=rdbms")
  class WithRdbmsType {
    @Autowired private SecondaryStorageInterceptor secondaryStorageInterceptor;

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

  @Nested
  @TestPropertySource(properties = "camunda.data.secondary-storage.type=none")
  class WithNoneType {
    @Autowired private SecondaryStorageInterceptor secondaryStorageInterceptor;

    @Test
    void shouldRejectRequestsRequiringSecondaryStorage() {
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
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=none",
        "camunda.database.type=none",
      })
  class WithMatchingLegacyAndUnifiedNoneType {
    @Autowired private SecondaryStorageInterceptor secondaryStorageInterceptor;

    @Test
    void shouldResolveDatabaseTypeFromLegacyProperty() {
      assertThatThrownBy(
              () ->
                  secondaryStorageInterceptor.preHandle(
                      requestDispatch(),
                      mock(HttpServletResponse.class),
                      requiresSecondaryStorageHandler()))
          .isInstanceOf(SecondaryStorageUnavailableException.class);
    }
  }
}
