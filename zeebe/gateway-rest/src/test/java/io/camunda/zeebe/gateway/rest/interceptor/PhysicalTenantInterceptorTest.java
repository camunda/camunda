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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.camunda.zeebe.gateway.rest.context.PhysicalTenantContext;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

class PhysicalTenantInterceptorTest {

  private MockHttpServletRequest request;
  private HttpServletResponse response;

  @BeforeEach
  void setUp() {
    request = new MockHttpServletRequest();
    response = mock(HttpServletResponse.class);
  }

  @Test
  void shouldDefaultWhenNoUriVariablesAttribute() throws Exception {
    // given: no URI_TEMPLATE_VARIABLES_ATTRIBUTE present at all
    final var interceptor = new PhysicalTenantInterceptor(id -> false);

    // when
    final boolean proceed = interceptor.preHandle(request, response, new Object());

    // then
    assertThat(proceed).isTrue();
    Assertions.assertThat(PhysicalTenantContext.getPhysicalTenantId(request))
        .isEqualTo(PhysicalTenantContext.DEFAULT_PHYSICAL_TENANT_ID);
    verifyNoInteractions(response);
  }

  @Test
  void shouldDefaultWhenUriVariablesMissTenantKey() throws Exception {
    // given
    request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("foo", "bar"));
    final var interceptor = new PhysicalTenantInterceptor(id -> false);

    // when
    final boolean proceed = interceptor.preHandle(request, response, new Object());

    // then
    assertThat(proceed).isTrue();
    assertThat(PhysicalTenantContext.getPhysicalTenantId(request))
        .isEqualTo(PhysicalTenantContext.DEFAULT_PHYSICAL_TENANT_ID);
    verifyNoInteractions(response);
  }

  @Test
  void shouldSetTenantWhenResolverAcceptsId() throws Exception {
    // given
    request.setAttribute(
        HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
        Map.of(PhysicalTenantContext.PATH_VARIABLE_PHYSICAL_TENANT_ID, "tenant-a"));
    final var interceptor = new PhysicalTenantInterceptor("tenant-a"::equals);

    // when
    final boolean proceed = interceptor.preHandle(request, response, new Object());

    // then
    assertThat(proceed).isTrue();
    assertThat(PhysicalTenantContext.getPhysicalTenantId(request)).isEqualTo("tenant-a");
    verifyNoInteractions(response);
  }

  @Test
  void shouldRejectWith404WhenResolverUnknownsId() throws Exception {
    // given
    request.setAttribute(
        HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
        Map.of(PhysicalTenantContext.PATH_VARIABLE_PHYSICAL_TENANT_ID, "ghost"));
    final var interceptor = new PhysicalTenantInterceptor(id -> false);

    // when
    final boolean proceed = interceptor.preHandle(request, response, new Object());

    // then
    assertThat(proceed).isFalse();
    verify(response).sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown physical tenant: ghost");
    // request attribute must not be polluted with the rejected id
    assertThat(PhysicalTenantContext.getPhysicalTenantId(request)).isEqualTo(null);
  }
}
