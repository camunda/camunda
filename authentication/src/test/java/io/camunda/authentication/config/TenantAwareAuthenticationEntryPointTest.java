/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.camunda.security.configuration.PhysicalTenantIdpRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

public class TenantAwareAuthenticationEntryPointTest {

  private final AuthenticationEntryPoint delegate = mock(AuthenticationEntryPoint.class);

  @Test
  public void shouldDelegateWhenRegistryEmpty() throws Exception {
    final var entryPoint =
        new TenantAwareAuthenticationEntryPoint(new PhysicalTenantIdpRegistry(Map.of()), delegate);
    final var req = makeRequest("/risk-production/api/foo");
    final var res = new MockHttpServletResponse();
    final var ex = new InsufficientAuthenticationException("nope");

    entryPoint.commence(req, res, ex);

    verify(delegate).commence(req, res, ex);
    assertThat(res.getRedirectedUrl()).isNull();
  }

  @Test
  public void shouldRedirectToPickerForTenantUrl() throws Exception {
    final var registry =
        new PhysicalTenantIdpRegistry(Map.of("riskproduction", List.of("keycloak")));
    final var entryPoint = new TenantAwareAuthenticationEntryPoint(registry, delegate);
    final var req = makeRequest("/riskproduction/api/foo");
    final var res = new MockHttpServletResponse();

    entryPoint.commence(req, res, new InsufficientAuthenticationException("nope"));

    assertThat(res.getRedirectedUrl()).isEqualTo("/admin/riskproduction/login");
    verify(delegate, never()).commence(req, res, null);
  }

  @Test
  public void shouldDelegateForNonTenantUrl() throws Exception {
    final var registry =
        new PhysicalTenantIdpRegistry(Map.of("riskproduction", List.of("keycloak")));
    final var entryPoint = new TenantAwareAuthenticationEntryPoint(registry, delegate);
    final var req = makeRequest("/operate/processes");
    final var res = new MockHttpServletResponse();
    final var ex = new InsufficientAuthenticationException("nope");

    entryPoint.commence(req, res, ex);

    verify(delegate).commence(req, res, ex);
    assertThat(res.getRedirectedUrl()).isNull();
  }

  @Test
  public void shouldHonorContextPathInRedirect() throws Exception {
    final var registry =
        new PhysicalTenantIdpRegistry(Map.of("riskproduction", List.of("keycloak")));
    final var entryPoint = new TenantAwareAuthenticationEntryPoint(registry, delegate);
    final var req = makeRequest("/riskproduction/api/foo");
    req.setContextPath("/identity");
    final var res = new MockHttpServletResponse();

    entryPoint.commence(req, res, new InsufficientAuthenticationException("nope"));

    assertThat(res.getRedirectedUrl()).isEqualTo("/identity/admin/riskproduction/login");
  }

  private static MockHttpServletRequest makeRequest(final String uri) {
    final var req = new MockHttpServletRequest("GET", uri);
    req.setRequestURI(uri);
    return req;
  }
}
