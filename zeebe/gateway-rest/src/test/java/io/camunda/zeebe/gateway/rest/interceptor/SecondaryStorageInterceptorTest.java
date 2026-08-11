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
import static org.mockito.Mockito.*;

<<<<<<< HEAD
=======
import io.camunda.cluster.SecondaryStorageReadiness;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.service.exception.SecondaryStorageDegradedException;
>>>>>>> f241add9 (fix: resolve type in SecondaryStorageInterceptor using Unified Configuration)
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

<<<<<<< HEAD
    final var interceptor = new SecondaryStorageInterceptor("elasticsearch");
=======
    final var interceptor =
        new SecondaryStorageInterceptor(
            t -> DatabaseType.ELASTICSEARCH, SecondaryStorageReadiness.ALWAYS_READY);
>>>>>>> f241add9 (fix: resolve type in SecondaryStorageInterceptor using Unified Configuration)
    final boolean result = interceptor.preHandle(request, response, handlerMethod);
    assertThat(result).isTrue();
  }

  @Test
<<<<<<< HEAD
  void shouldAllowWhenSecondaryStorageEnabled() {
=======
  void shouldNotConsultReadinessWhenNoAnnotation() {
    when(handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)).thenReturn(false);
    when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);
    final var readiness = mock(SecondaryStorageReadiness.class);

    final var interceptor =
        new SecondaryStorageInterceptor(t -> DatabaseType.ELASTICSEARCH, readiness);
    interceptor.preHandle(request, response, handlerMethod);

    verifyNoInteractions(readiness);
  }

  @Test
  void shouldAllowWhenSecondaryStorageEnabledAndTenantServiceable() {
>>>>>>> f241add9 (fix: resolve type in SecondaryStorageInterceptor using Unified Configuration)
    when(handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)).thenReturn(true);
    when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);

<<<<<<< HEAD
    final var interceptor = new SecondaryStorageInterceptor("elasticsearch");
=======
    final var interceptor =
        new SecondaryStorageInterceptor(
            t -> DatabaseType.ELASTICSEARCH, SecondaryStorageReadiness.ALWAYS_READY);
>>>>>>> f241add9 (fix: resolve type in SecondaryStorageInterceptor using Unified Configuration)
    final boolean result = interceptor.preHandle(request, response, handlerMethod);
    assertThat(result).isTrue();
  }

  @Test
  void shouldThrowWhenSecondaryStorageDisabledAndAnnotationPresent() {
    when(handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)).thenReturn(true);
    when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);
    bindPhysicalTenant(PHYSICAL_TENANT_ID);

<<<<<<< HEAD
    final var interceptor = new SecondaryStorageInterceptor(CAMUNDA_DATABASE_TYPE_NONE);
    assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
        .isInstanceOf(SecondaryStorageUnavailableException.class);
  }
=======
    final var interceptor =
        new SecondaryStorageInterceptor(
            t -> DatabaseType.NONE, SecondaryStorageReadiness.ALWAYS_READY);
    assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
        .isInstanceOf(SecondaryStorageUnavailableException.class);
  }

  @Test
  void shouldRejectWithForbiddenEvenWhenTenantDegradedAndSecondaryStorageDisabled() {
    when(handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)).thenReturn(true);
    when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);
    bindPhysicalTenant(PHYSICAL_TENANT_ID);

    final var interceptor =
        new SecondaryStorageInterceptor(t -> DatabaseType.NONE, degraded(PHYSICAL_TENANT_ID));
    assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
        .isInstanceOf(SecondaryStorageUnavailableException.class);
  }

  @Test
  void shouldThrowWhenPhysicalTenantDegraded() {
    when(handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)).thenReturn(true);
    when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);
    bindPhysicalTenant(PHYSICAL_TENANT_ID);

    final var interceptor =
        new SecondaryStorageInterceptor(
            t -> DatabaseType.ELASTICSEARCH, degraded(PHYSICAL_TENANT_ID));
    assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
        .isInstanceOf(SecondaryStorageDegradedException.class);
  }

  @Test
  void shouldHintRetryAfterWhenPhysicalTenantDegraded() {
    when(handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)).thenReturn(true);
    when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);
    bindPhysicalTenant(PHYSICAL_TENANT_ID);

    final var interceptor =
        new SecondaryStorageInterceptor(
            t -> DatabaseType.ELASTICSEARCH, degraded(PHYSICAL_TENANT_ID));
    assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
        .isInstanceOf(SecondaryStorageDegradedException.class);

    verify(response).setHeader(HttpHeaders.RETRY_AFTER, "5");
  }

  @Test
  void shouldNotHintRetryAfterWhenSecondaryStorageDisabled() {
    when(handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)).thenReturn(true);
    when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);
    bindPhysicalTenant(PHYSICAL_TENANT_ID);

    final var interceptor =
        new SecondaryStorageInterceptor(
            t -> DatabaseType.NONE, SecondaryStorageReadiness.ALWAYS_READY);
    assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
        .isInstanceOf(SecondaryStorageUnavailableException.class);

    verifyNoInteractions(response);
  }

  @Test
  void shouldSkipDegradedCheckOnAsyncDispatch() {
    when(handlerMethod.hasMethodAnnotation(RequiresSecondaryStorage.class)).thenReturn(true);
    when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);
    when(request.getDispatcherType()).thenReturn(DispatcherType.ASYNC);
    bindPhysicalTenant(PHYSICAL_TENANT_ID);

    final var interceptor =
        new SecondaryStorageInterceptor(
            t -> DatabaseType.ELASTICSEARCH, degraded(PHYSICAL_TENANT_ID));
    final boolean result = interceptor.preHandle(request, response, handlerMethod);
    assertThat(result).isTrue();
  }

  private static void bindPhysicalTenant(final String physicalTenantId) {
    final var mockRequest = mock(HttpServletRequest.class);
    when(mockRequest.getAttribute(PhysicalTenantContext.REQUEST_ATTRIBUTE_PHYSICAL_TENANT_ID))
        .thenReturn(physicalTenantId);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));
  }

  private static SecondaryStorageReadiness degraded(final String physicalTenantId) {
    final var readiness = mock(SecondaryStorageReadiness.class);
    when(readiness.isReady(physicalTenantId)).thenReturn(false);
    return readiness;
  }
>>>>>>> f241add9 (fix: resolve type in SecondaryStorageInterceptor using Unified Configuration)
}
