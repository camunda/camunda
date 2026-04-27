/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.authentication.filters.TenantBindingEnforcementFilter;
import io.camunda.security.configuration.PhysicalTenantIdpRegistry;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

public class TenantBindingAuthenticationSuccessHandlerTest {

  private final PhysicalTenantIdpRegistry registry =
      new PhysicalTenantIdpRegistry(
          Map.of(
              "default-engine", List.of("default"),
              "risk-production", List.of("default", "provider-a")));

  private final TenantBindingAuthenticationSuccessHandler handler =
      new TenantBindingAuthenticationSuccessHandler(registry);

  @Test
  public void shouldDelegateToDefaultWhenRegistryEmpty() throws Exception {
    // given — empty registry means tenant binding is dormant (BC)
    final var emptyHandler =
        new TenantBindingAuthenticationSuccessHandler(new PhysicalTenantIdpRegistry(Map.of()));
    final var req = new MockHttpServletRequest();
    final var res = new MockHttpServletResponse();

    // when
    emptyHandler.onAuthenticationSuccess(req, res, oauthToken("default"));

    // then — default handler runs (non-403)
    assertThat(res.getStatus()).isNotEqualTo(HttpServletResponse.SC_FORBIDDEN);
  }

  @Test
  public void shouldDelegateForNonOAuth2Auth() throws Exception {
    // given — anonymous / basic / custom: not our concern
    final var req = new MockHttpServletRequest();
    final var res = new MockHttpServletResponse();
    final var auth = new TestingAuthenticationToken("user", "pw", "ROLE_USER");

    // when
    handler.onAuthenticationSuccess(req, res, auth);

    // then
    assertThat(res.getStatus()).isNotEqualTo(HttpServletResponse.SC_FORBIDDEN);
  }

  @Test
  public void shouldReturn403WhenSessionHasNoBoundTenant() throws Exception {
    // given — user authenticated but bypassed the picker (no session attribute)
    final var req = new MockHttpServletRequest();
    req.getSession(true); // create session, no attribute
    final var res = new MockHttpServletResponse();

    // when
    handler.onAuthenticationSuccess(req, res, oauthToken("default"));

    // then
    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    assertThat(req.getSession(false)).isNull(); // session invalidated
  }

  @Test
  public void shouldReturn403WhenBoundTenantNoLongerInRegistry() throws Exception {
    // given — registry no longer contains the bound tenant (e.g. config change mid-flow)
    final var req = new MockHttpServletRequest();
    req.getSession()
        .setAttribute(TenantBindingEnforcementFilter.BOUND_TENANT_ATTRIBUTE, "removed-tenant");
    final var res = new MockHttpServletResponse();

    // when
    handler.onAuthenticationSuccess(req, res, oauthToken("default"));

    // then
    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
  }

  @Test
  public void shouldReturn403OnMultiTabRaceWhereIdpDoesNotServeBoundTenant() throws Exception {
    // given — multi-tab race: tab1 wrote tenant="default-engine" then tab2 (idp=provider-a)
    // overwrote bound tenant with "risk-production"; tab1's callback completes with idp="default"
    // — but suppose the session ended up bound to a tenant the just-authenticated IdP doesn't serve
    final var req = new MockHttpServletRequest();
    req.getSession()
        .setAttribute(TenantBindingEnforcementFilter.BOUND_TENANT_ATTRIBUTE, "default-engine");
    final var res = new MockHttpServletResponse();

    // when — user authenticated via provider-a (which is NOT in default-engine's allow list)
    handler.onAuthenticationSuccess(req, res, oauthToken("provider-a"));

    // then
    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    assertThat(req.getSession(false)).isNull(); // session invalidated
  }

  @Test
  public void shouldDelegateOnHappyPath() throws Exception {
    // given — bound tenant matches the just-authenticated IdP's allow list
    final var req = new MockHttpServletRequest();
    req.getSession()
        .setAttribute(TenantBindingEnforcementFilter.BOUND_TENANT_ATTRIBUTE, "risk-production");
    final var res = new MockHttpServletResponse();

    // when
    handler.onAuthenticationSuccess(req, res, oauthToken("provider-a"));

    // then — default handler runs (no 403)
    assertThat(res.getStatus()).isNotEqualTo(HttpServletResponse.SC_FORBIDDEN);
    assertThat(req.getSession(false)).isNotNull(); // session preserved
    assertThat(req.getSession().getAttribute(TenantBindingEnforcementFilter.BOUND_TENANT_ATTRIBUTE))
        .isEqualTo("risk-production");
  }

  private static OAuth2AuthenticationToken oauthToken(final String registrationId) {
    final var user =
        new DefaultOAuth2User(
            Set.of(new SimpleGrantedAuthority("ROLE_USER")), Map.of("sub", "user1"), "sub");
    return new OAuth2AuthenticationToken(user, user.getAuthorities(), registrationId);
  }
}
