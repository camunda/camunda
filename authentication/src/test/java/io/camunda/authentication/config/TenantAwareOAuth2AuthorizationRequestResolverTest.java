/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.authentication.filters.TenantBindingEnforcementFilter;
import io.camunda.security.configuration.PhysicalTenantIdpRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;

public class TenantAwareOAuth2AuthorizationRequestResolverTest {

  private final OAuth2AuthorizationRequestResolver delegate =
      mock(OAuth2AuthorizationRequestResolver.class);

  @Test
  public void shouldPassThroughWhenRegistryIsEmpty() {
    // given — empty registry means tenant binding is dormant
    final var registry = new PhysicalTenantIdpRegistry(Map.of());
    final var resolver = new TenantAwareOAuth2AuthorizationRequestResolver(delegate, registry);
    final var req = new MockHttpServletRequest("GET", "/oauth2/authorization/default");
    final var authz = authzRequest("default");
    when(delegate.resolve(req)).thenReturn(authz);

    // when
    final var resolved = resolver.resolve(req);

    // then
    assertThat(resolved).isSameAs(authz);
    assertThat(req.getSession(false)).isNull();
  }

  @Test
  public void shouldReturnNullWhenDelegateReturnsNull() {
    // given — delegate doesn't match the URL
    final var resolver =
        new TenantAwareOAuth2AuthorizationRequestResolver(delegate, smallRegistry());
    final var req = new MockHttpServletRequest("GET", "/api/health");
    when(delegate.resolve(req)).thenReturn(null);

    // when
    final var resolved = resolver.resolve(req);

    // then
    assertThat(resolved).isNull();
  }

  @Test
  public void shouldThrowWhenTenantParamMissing() {
    // given
    final var resolver =
        new TenantAwareOAuth2AuthorizationRequestResolver(delegate, smallRegistry());
    final var req = new MockHttpServletRequest("GET", "/oauth2/authorization/default");
    when(delegate.resolve(req)).thenReturn(authzRequest("default"));

    // then
    assertThatThrownBy(() -> resolver.resolve(req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Missing 'tenant'");
  }

  @Test
  public void shouldThrowWhenTenantUnknown() {
    // given
    final var resolver =
        new TenantAwareOAuth2AuthorizationRequestResolver(delegate, smallRegistry());
    final var req = new MockHttpServletRequest("GET", "/oauth2/authorization/default");
    req.setParameter("tenant", "unknown-tenant");
    when(delegate.resolve(req)).thenReturn(authzRequest("default"));

    // then
    assertThatThrownBy(() -> resolver.resolve(req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown physical tenant");
  }

  @Test
  public void shouldThrowWhenIdpNotAssignedToTenant() {
    // given — registry says provider-a only serves risk-production, not default-engine
    final var registry =
        new PhysicalTenantIdpRegistry(
            Map.of(
                "default-engine", List.of("default"),
                "risk-production", List.of("default", "provider-a")));
    final var resolver = new TenantAwareOAuth2AuthorizationRequestResolver(delegate, registry);
    final var req = new MockHttpServletRequest("GET", "/oauth2/authorization/provider-a");
    req.setParameter("tenant", "default-engine");
    when(delegate.resolve(req)).thenReturn(authzRequest("provider-a"));

    // then
    assertThatThrownBy(() -> resolver.resolve(req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not assigned");
  }

  @Test
  public void shouldStampSessionAttributeOnValidPair() {
    // given
    final var resolver =
        new TenantAwareOAuth2AuthorizationRequestResolver(delegate, smallRegistry());
    final var req = new MockHttpServletRequest("GET", "/oauth2/authorization/default");
    req.setParameter("tenant", "default-engine");
    final var authz = authzRequest("default");
    when(delegate.resolve(req)).thenReturn(authz);

    // when
    final var resolved = resolver.resolve(req);

    // then
    assertThat(resolved).isSameAs(authz);
    assertThat(req.getSession().getAttribute(TenantBindingEnforcementFilter.BOUND_TENANT_ATTRIBUTE))
        .isEqualTo("default-engine");
  }

  @Test
  public void shouldDelegateWithExplicitRegistrationId() {
    // given — second resolve(req, idpId) overload
    final var resolver =
        new TenantAwareOAuth2AuthorizationRequestResolver(delegate, smallRegistry());
    final var req = new MockHttpServletRequest("GET", "/oauth2/authorization/default");
    req.setParameter("tenant", "default-engine");
    final var authz = authzRequest("default");
    when(delegate.resolve(req, "default")).thenReturn(authz);

    // when
    final var resolved = resolver.resolve(req, "default");

    // then
    assertThat(resolved).isSameAs(authz);
    assertThat(req.getSession().getAttribute(TenantBindingEnforcementFilter.BOUND_TENANT_ATTRIBUTE))
        .isEqualTo("default-engine");
  }

  private static PhysicalTenantIdpRegistry smallRegistry() {
    return new PhysicalTenantIdpRegistry(Map.of("default-engine", List.of("default")));
  }

  private static OAuth2AuthorizationRequest authzRequest(final String registrationId) {
    return OAuth2AuthorizationRequest.authorizationCode()
        .authorizationUri("https://idp.example.com/authorize")
        .clientId("client-id")
        .redirectUri("https://app.example.com/sso-callback")
        .state("state-1")
        .scopes(java.util.Set.of("openid"))
        .attributes(attrs -> attrs.put(OAuth2ParameterNames.REGISTRATION_ID, registrationId))
        .build();
  }
}
