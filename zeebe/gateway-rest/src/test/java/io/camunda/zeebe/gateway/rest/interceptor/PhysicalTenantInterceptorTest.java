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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.camunda.zeebe.gateway.rest.annotation.ClusterScoped;
import io.camunda.zeebe.gateway.rest.context.PhysicalTenantContext;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;

class PhysicalTenantInterceptorTest {

  private MockHttpServletRequest request;
  private HttpServletResponse response;

  @BeforeEach
  void setUp() {
    request = new MockHttpServletRequest();
    response = mock(HttpServletResponse.class);
  }

  // ---- non-prefixed requests -------------------------------------------

  @Test
  void shouldDefaultWhenNotPhysicalTenantPrefixed() throws Exception {
    // given: IS_PHYSICAL_TENANT_PREFIXED attribute absent (no tenant prefix in original URL)
    final var interceptor = new PhysicalTenantInterceptor(id -> true);

    // when
    final boolean proceed = interceptor.preHandle(request, response, new Object());

    // then
    assertThat(proceed).isTrue();
    assertThat(PhysicalTenantContext.getPhysicalTenantId(request))
        .isEqualTo(PhysicalTenantContext.DEFAULT_PHYSICAL_TENANT_ID);
    verifyNoInteractions(response);
  }

  @Test
  void shouldAllowClusterScopedControllerWhenNotPrefixed() throws Exception {
    // given
    final var interceptor = new PhysicalTenantInterceptor(id -> true);
    final var handler = handlerMethod(new ClusterScopedController());

    // when
    final boolean proceed = interceptor.preHandle(request, response, handler);

    // then
    assertThat(proceed).isTrue();
    assertThat(PhysicalTenantContext.getPhysicalTenantId(request))
        .isEqualTo(PhysicalTenantContext.DEFAULT_PHYSICAL_TENANT_ID);
    verifyNoInteractions(response);
  }

  // ---- tenant-prefixed requests ----------------------------------------

  @Test
  void shouldProceedWhenRegistryAcceptsTenant() throws Exception {
    // given
    request.setAttribute(PhysicalTenantContext.REQUEST_ATTRIBUTE_IS_PHYSICAL_TENANT_PREFIXED, true);
    PhysicalTenantContext.setPhysicalTenantId(request, "tenant-a");
    final var interceptor = new PhysicalTenantInterceptor("tenant-a"::equals);

    // when
    final boolean proceed = interceptor.preHandle(request, response, new Object());

    // then
    assertThat(proceed).isTrue();
    assertThat(PhysicalTenantContext.getPhysicalTenantId(request)).isEqualTo("tenant-a");
    verifyNoInteractions(response);
  }

  @Test
  void shouldThrow404WhenRegistryUnknownId() {
    // given
    request.setAttribute(PhysicalTenantContext.REQUEST_ATTRIBUTE_IS_PHYSICAL_TENANT_PREFIXED, true);
    PhysicalTenantContext.setPhysicalTenantId(request, "ghost");
    final var interceptor = new PhysicalTenantInterceptor(id -> false);

    // when / then — ResponseStatusException flows through GlobalControllerExceptionHandler
    // and produces a CamundaProblemDetail, matching the error format of the rest of the API
    assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            ex -> {
              final var rse = (ResponseStatusException) ex;
              assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(rse.getReason()).isEqualTo("Unknown physical tenant: ghost");
            });
    verifyNoInteractions(response);
  }

  @Test
  void shouldThrow404WhenClusterScopedControllerAccessedViaTenantPrefix() throws Exception {
    // given
    request.setAttribute(PhysicalTenantContext.REQUEST_ATTRIBUTE_IS_PHYSICAL_TENANT_PREFIXED, true);
    PhysicalTenantContext.setPhysicalTenantId(request, "tenant-a");
    final var interceptor = new PhysicalTenantInterceptor(id -> true);
    final var handler = handlerMethod(new ClusterScopedController());

    // when / then
    assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            ex ->
                assertThat(((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND));
    verifyNoInteractions(response);
  }

  // ---- helpers ---------------------------------------------------------

  private static HandlerMethod handlerMethod(final Object controller) throws NoSuchMethodException {
    return new HandlerMethod(controller, controller.getClass().getDeclaredMethod("handle"));
  }

  // ---- fixtures --------------------------------------------------------

  @CamundaRestController
  @ClusterScoped
  private static final class ClusterScopedController {
    @SuppressWarnings("unused")
    public void handle() {}
  }
}
