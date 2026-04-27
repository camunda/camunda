/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.filters;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.PhysicalTenantIdpRegistry;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

public class PhysicalTenantAuthorizationFilterTest {

  private final PhysicalTenantIdpRegistry registry =
      new PhysicalTenantIdpRegistry(
          Map.of(
              "default", List.of("default"),
              "risk-production", List.of("default", "provider-a")));

  private final PhysicalTenantAuthorizationFilter filter =
      new PhysicalTenantAuthorizationFilter(registry);

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  public void shouldPassThroughWhenPathIsNotTenantScoped() throws Exception {
    final var req = makeRequest("/api/health");
    final var res = new MockHttpServletResponse();
    final var chain = new MockFilterChain();

    filter.doFilter(req, res, chain);

    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    assertThat(chain.getRequest()).isSameAs(req);
  }

  @Test
  public void shouldPassThroughForLoginPickerEvenIfPathLooksTenantScoped() throws Exception {
    // /login/{tenantId} would have firstPathSegment "login"; "login" is not a tenant id, so the
    // filter passes through and the controller serves the picker.
    final var req = makeRequest("/login/risk-production");
    final var res = new MockHttpServletResponse();
    final var chain = new MockFilterChain();

    filter.doFilter(req, res, chain);

    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    assertThat(chain.getRequest()).isSameAs(req);
  }

  @Test
  public void shouldPassThroughForBearerTokenAuthOnTenantUrl() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("api-client", "jwt", "ROLE_USER"));
    final var req = makeRequest("/risk-production/api/foo");
    final var res = new MockHttpServletResponse();
    final var chain = new MockFilterChain();

    filter.doFilter(req, res, chain);

    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    assertThat(chain.getRequest()).isSameAs(req);
  }

  @Test
  public void shouldReturn401WhenTenantUrlButNoAuthentication() throws Exception {
    final var req = makeRequest("/risk-production/api/foo");
    final var res = new MockHttpServletResponse();
    final var chain = new MockFilterChain();

    filter.doFilter(req, res, chain);

    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  public void shouldReturn403WhenIdpNotAssignedToUrlTenant() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(oauthToken("provider-a"));
    final var req = makeRequest("/default/api/foo");
    req.getSession()
        .setAttribute(PhysicalTenantAuthorizationFilter.BOUND_TENANT_ATTRIBUTE, "risk-production");
    final var res = new MockHttpServletResponse();
    final var chain = new MockFilterChain();

    filter.doFilter(req, res, chain);

    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  public void shouldReturn403WhenSessionTenantDoesNotMatchUrlTenant() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(oauthToken("default"));
    final var req = makeRequest("/risk-production/api/foo");
    req.getSession()
        .setAttribute(PhysicalTenantAuthorizationFilter.BOUND_TENANT_ATTRIBUTE, "default");
    final var res = new MockHttpServletResponse();
    final var chain = new MockFilterChain();

    filter.doFilter(req, res, chain);

    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  public void shouldReturn403WhenSessionHasNoBoundTenant() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(oauthToken("default"));
    final var req = makeRequest("/default/api/foo");
    req.setSession(new MockHttpSession());
    final var res = new MockHttpServletResponse();
    final var chain = new MockFilterChain();

    filter.doFilter(req, res, chain);

    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  public void shouldAllowWhenAuthIdpAllowedAndSessionTenantMatches() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(oauthToken("provider-a"));
    final var req = makeRequest("/risk-production/api/foo");
    req.getSession()
        .setAttribute(PhysicalTenantAuthorizationFilter.BOUND_TENANT_ATTRIBUTE, "risk-production");
    final var res = new MockHttpServletResponse();
    final var chain = new MockFilterChain();

    filter.doFilter(req, res, chain);

    assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    assertThat(chain.getRequest()).isSameAs(req);
  }

  private static MockHttpServletRequest makeRequest(final String uri) {
    final var req = new MockHttpServletRequest("GET", uri);
    req.setRequestURI(uri);
    return req;
  }

  private static OAuth2AuthenticationToken oauthToken(final String registrationId) {
    final var user =
        new DefaultOAuth2User(
            Set.of(new SimpleGrantedAuthority("ROLE_USER")), Map.of("sub", "user1"), "sub");
    return new OAuth2AuthenticationToken(user, user.getAuthorities(), registrationId);
  }
}
