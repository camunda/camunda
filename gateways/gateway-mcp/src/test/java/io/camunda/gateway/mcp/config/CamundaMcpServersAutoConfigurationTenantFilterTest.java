/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.gateway.mapping.http.physicaltenants.PhysicalTenantContext;
import io.camunda.gateway.mapping.http.physicaltenants.PhysicalTenantIds;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

class CamundaMcpServersAutoConfigurationTenantFilterTest {

  @Test
  void shouldSetDefaultPhysicalTenantIdAndProceed() throws Exception {
    // given
    final var filter = CamundaMcpServersAutoConfiguration.defaultTenantFilter();
    final MockHttpServletRequest servletRequest = new MockHttpServletRequest();
    final ServerRequest request = mock(ServerRequest.class);
    when(request.servletRequest()).thenReturn(servletRequest);
    final ServerResponse expectedResponse = ServerResponse.ok().build();
    final HandlerFunction<ServerResponse> next = mock(HandlerFunction.class);
    when(next.handle(request)).thenReturn(expectedResponse);

    // when
    final ServerResponse response = filter.filter(request, next);

    // then
    assertThat(response).isSameAs(expectedResponse);
    assertThat(PhysicalTenantContext.getPhysicalTenantId(servletRequest))
        .isEqualTo(PhysicalTenantContext.DEFAULT_PHYSICAL_TENANT_ID);
    verify(next).handle(request);
  }

  @Test
  void shouldSetTenantIdAndProceedWhenResolverAcceptsId() throws Exception {
    // given
    final var filter =
        CamundaMcpServersAutoConfiguration.tenantFilter(resolverKnownTenants(Set.of("tenant-a")));
    final MockHttpServletRequest servletRequest = new MockHttpServletRequest();
    final ServerRequest request = mock(ServerRequest.class);
    when(request.pathVariable(PhysicalTenantContext.PATH_VARIABLE_PHYSICAL_TENANT_ID))
        .thenReturn("tenant-a");
    when(request.servletRequest()).thenReturn(servletRequest);
    final ServerResponse expectedResponse = ServerResponse.ok().build();
    final HandlerFunction<ServerResponse> next = mock(HandlerFunction.class);
    when(next.handle(request)).thenReturn(expectedResponse);

    // when
    final ServerResponse response = filter.filter(request, next);

    // then
    assertThat(response).isSameAs(expectedResponse);
    assertThat(PhysicalTenantContext.getPhysicalTenantId(servletRequest)).isEqualTo("tenant-a");
    verify(next).handle(request);
  }

  @Test
  void shouldRejectWithNotFoundWhenResolverDoesNotKnowTenantId() throws Exception {
    // given
    final var filter =
        CamundaMcpServersAutoConfiguration.tenantFilter(resolverKnownTenants(Set.of()));
    final MockHttpServletRequest servletRequest = new MockHttpServletRequest();
    final ServerRequest request = mock(ServerRequest.class);
    when(request.pathVariable(PhysicalTenantContext.PATH_VARIABLE_PHYSICAL_TENANT_ID))
        .thenReturn("ghost");
    when(request.servletRequest()).thenReturn(servletRequest);
    final HandlerFunction<ServerResponse> next = mock(HandlerFunction.class);

    // when
    final ServerResponse response = filter.filter(request, next);

    // then
    assertThat(response.statusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response)
        .isInstanceOfSatisfying(
            org.springframework.web.servlet.function.EntityResponse.class,
            entityResponse ->
                assertThat(entityResponse.entity()).isEqualTo("Unknown physical tenant: ghost"));
    // request attribute must not be polluted with the rejected id
    assertThat(PhysicalTenantContext.getPhysicalTenantId(servletRequest)).isNull();
    verify(next, never()).handle(request);
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldFallBackToDefaultOnlyResolverWhenNoBeanProvided() throws Exception {
    // given: no PhysicalTenantIds bean is available; the fallback only accepts the default id
    final ObjectProvider<PhysicalTenantIds> emptyProvider = mock(ObjectProvider.class);
    when(emptyProvider.getIfAvailable()).thenReturn(null);
    final var filter = CamundaMcpServersAutoConfiguration.tenantFilter(emptyProvider);

    final MockHttpServletRequest servletRequest = new MockHttpServletRequest();
    final ServerRequest request = mock(ServerRequest.class);
    when(request.pathVariable(PhysicalTenantContext.PATH_VARIABLE_PHYSICAL_TENANT_ID))
        .thenReturn(PhysicalTenantContext.DEFAULT_PHYSICAL_TENANT_ID);
    when(request.servletRequest()).thenReturn(servletRequest);
    final ServerResponse expectedResponse = ServerResponse.ok().build();
    final HandlerFunction<ServerResponse> next = mock(HandlerFunction.class);
    when(next.handle(request)).thenReturn(expectedResponse);

    // when
    final ServerResponse response = filter.filter(request, next);

    // then: the fallback accepts only the default tenant id
    assertThat(response).isSameAs(expectedResponse);
    assertThat(PhysicalTenantContext.getPhysicalTenantId(servletRequest))
        .isEqualTo(PhysicalTenantContext.DEFAULT_PHYSICAL_TENANT_ID);
  }

  @SuppressWarnings("unchecked")
  private static ObjectProvider<PhysicalTenantIds> resolverKnownTenants(
      final Set<String> knownTenants) {
    final ObjectProvider<PhysicalTenantIds> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(() -> knownTenants);
    return provider;
  }
}
