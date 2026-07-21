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

import io.camunda.cluster.SecondaryStorageAvailability;
import io.camunda.service.exception.SecondaryStorageDegradedException;
import io.camunda.service.exception.SecondaryStorageUnavailableException;
import io.camunda.spring.utils.PhysicalTenantContext;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;

@SuppressWarnings({"unchecked", "rawtypes"})
class SecondaryStorageInterceptorTest {

  private static final String PHYSICAL_TENANT_ID = "tenant-a";

  private HttpServletRequest request;
  private HttpServletResponse response;
  private HandlerMethod handlerMethod;

  @BeforeEach
  void setUp() {
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
    handlerMethod = mock(HandlerMethod.class);
    when(request.getDispatcherType()).thenReturn(DispatcherType.REQUEST);
  }

  @AfterEach
  void tearDown() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void shouldAllowWhenNoAnnotation() {
    when(handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)).thenReturn(false);
    when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);

    final var interceptor =
        new SecondaryStorageInterceptor(
            "elasticsearch", SecondaryStorageAvailability.ALWAYS_AVAILABLE);
    final boolean result = interceptor.preHandle(request, response, handlerMethod);
    assertThat(result).isTrue();
  }

  @Test
  void shouldNotConsultAvailabilityWhenNoAnnotation() {
    when(handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)).thenReturn(false);
    when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);
    final var availability = mock(SecondaryStorageAvailability.class);

    final var interceptor = new SecondaryStorageInterceptor("elasticsearch", availability);
    interceptor.preHandle(request, response, handlerMethod);

    verifyNoInteractions(availability);
  }

  @Test
  void shouldAllowWhenSecondaryStorageEnabledAndTenantServiceable() {
    when(handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)).thenReturn(true);
    when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);
    bindPhysicalTenant(PHYSICAL_TENANT_ID);

    final var interceptor =
        new SecondaryStorageInterceptor(
            "elasticsearch", SecondaryStorageAvailability.ALWAYS_AVAILABLE);
    final boolean result = interceptor.preHandle(request, response, handlerMethod);
    assertThat(result).isTrue();
  }

  @Test
  void shouldThrowWhenSecondaryStorageDisabledAndAnnotationPresent() {
    when(handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)).thenReturn(true);
    when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);

    final var interceptor =
        new SecondaryStorageInterceptor(
            CAMUNDA_DATABASE_TYPE_NONE, SecondaryStorageAvailability.ALWAYS_AVAILABLE);
    assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
        .isInstanceOf(SecondaryStorageUnavailableException.class);
  }

  @Test
  void shouldRejectWithForbiddenEvenWhenTenantDegradedAndSecondaryStorageDisabled() {
    when(handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)).thenReturn(true);
    when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);
    bindPhysicalTenant(PHYSICAL_TENANT_ID);

    final var interceptor =
        new SecondaryStorageInterceptor(CAMUNDA_DATABASE_TYPE_NONE, degraded(PHYSICAL_TENANT_ID));
    assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
        .isInstanceOf(SecondaryStorageUnavailableException.class);
  }

  @Test
  void shouldThrowWhenPhysicalTenantDegraded() {
    when(handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)).thenReturn(true);
    when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);
    bindPhysicalTenant(PHYSICAL_TENANT_ID);

    final var interceptor =
        new SecondaryStorageInterceptor("elasticsearch", degraded(PHYSICAL_TENANT_ID));
    assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
        .isInstanceOf(SecondaryStorageDegradedException.class);
  }

  @Test
  void shouldSkipDegradedCheckOnAsyncDispatch() {
    when(handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)).thenReturn(true);
    when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);
    when(request.getDispatcherType()).thenReturn(DispatcherType.ASYNC);

    final var interceptor =
        new SecondaryStorageInterceptor("elasticsearch", degraded(PHYSICAL_TENANT_ID));
    final boolean result = interceptor.preHandle(request, response, handlerMethod);
    assertThat(result).isTrue();
  }

  private static void bindPhysicalTenant(final String physicalTenantId) {
    final var mockRequest = mock(HttpServletRequest.class);
    when(mockRequest.getAttribute(PhysicalTenantContext.REQUEST_ATTRIBUTE_PHYSICAL_TENANT_ID))
        .thenReturn(physicalTenantId);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));
  }

  private static SecondaryStorageAvailability degraded(final String physicalTenantId) {
    final var availability = mock(SecondaryStorageAvailability.class);
    when(availability.isAvailable(physicalTenantId)).thenReturn(false);
    return availability;
  }
}
