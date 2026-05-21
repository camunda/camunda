/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import io.camunda.authentication.pt.TenantSecuritySlice.AccessPath;
import io.camunda.authentication.session.WebSession;
import io.camunda.authentication.session.WebSessionRepository;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.SessionRepositoryFilter;

@Configuration
@EnableWebSecurity
@Profile("pt-security")
public class PhysicalTenantSecurityConfiguration {

  // Two near-symmetric @Bean methods, one per tenant. Each reduces to:
  //   1) sliceFor(...) — looks up the tenant's ClientRegistrationRepository and per-chain session
  //      machinery from the map beans injected here (assembled by the pt-security profile gates
  //      inside OidcOverrideBeansConfiguration and WebSessionRepositoryConfiguration).
  //   2) factory.buildWebappChain(http, slice) — applies the chain shape (security matcher,
  //      filter ordering, oauth2Login with the prefix-aware resolver workaround).
  //
  // Tenant A's providers.assigned list resolves a NAMED OIDC provider "tenanta" under
  //   camunda.physical-tenants.tenanta.security.authentication.providers.oidc.tenanta.*
  // The default tenant's providers.assigned list resolves the cluster-default
  //   camunda.physical-tenants.default.security.authentication.oidc.* (registration id "oidc").
  private static final String TENANTA_TENANT_ID = "tenanta";
  private static final String DEFAULT_TENANT_ID = "default";

  private final PerTenantSecurityChainFactory chainFactory = new PerTenantSecurityChainFactory();
  private final Map<String, WebSessionRepository> ptWebSessionRepositories;
  private final Map<String, ClientRegistrationRepository> ptClientRegistrationRepositories;
  private final Map<String, Set<String>> ptAllowedIssuersPerTenant;
  // Single-entry map ({"default" -> repo}) — same shape as ptClientRegistrationRepositories so
  // Spring's by-type autowire never sees a second bare ClientRegistrationRepository bean. See
  // OidcOverrideBeansConfiguration#ptUnprefixedDefaultClientRegistrationRepositories.
  private final Map<String, ClientRegistrationRepository>
      ptUnprefixedDefaultClientRegistrationRepositories;

  public PhysicalTenantSecurityConfiguration(
      final Map<String, WebSessionRepository> ptWebSessionRepositories,
      @Qualifier("ptClientRegistrationRepositories")
          final Map<String, ClientRegistrationRepository> ptClientRegistrationRepositories,
      final Map<String, Set<String>> ptAllowedIssuersPerTenant,
      @Qualifier("ptUnprefixedDefaultClientRegistrationRepositories")
          final Map<String, ClientRegistrationRepository>
              ptUnprefixedDefaultClientRegistrationRepositories) {
    this.ptWebSessionRepositories = ptWebSessionRepositories;
    this.ptClientRegistrationRepositories = ptClientRegistrationRepositories;
    this.ptAllowedIssuersPerTenant = ptAllowedIssuersPerTenant;
    this.ptUnprefixedDefaultClientRegistrationRepositories =
        ptUnprefixedDefaultClientRegistrationRepositories;
  }

  // Chain registration order is significant. Spring Security evaluates SecurityFilterChain
  // beans in the order Spring resolves them; the API chain matcher
  // /physical-tenant/<id>/v2/** is a sub-pattern of the webapp chain matcher
  // /physical-tenant/<id>/**, so the API chain MUST be matched first — otherwise the broader
  // webapp chain would swallow API requests and try to drive an OAuth2 redirect. @Order makes
  // the precedence explicit and stable regardless of bean-resolution order.
  //
  // Six chains in total (Task 12): the four prefixed chains above, plus two unprefixed-default
  // chains for the alternate access path. Unprefixed-default comes LAST by @Order so the more
  // specific prefixed matchers always win — only requests that miss every prefixed matcher
  // fall through to /v2/** or /** on the unprefixed default chains.

  @Bean
  @Order(1)
  public SecurityFilterChain ptTenantaApiChain(final HttpSecurity http, final JwtDecoder jwtDecoder)
      throws Exception {
    return chainFactory.buildApiChain(
        http, sliceFor(TENANTA_TENANT_ID), jwtDecoder, allowedIssuersFor(TENANTA_TENANT_ID));
  }

  @Bean
  @Order(2)
  public SecurityFilterChain ptDefaultPrefixedApiChain(
      final HttpSecurity http, final JwtDecoder jwtDecoder) throws Exception {
    return chainFactory.buildApiChain(
        http, sliceFor(DEFAULT_TENANT_ID), jwtDecoder, allowedIssuersFor(DEFAULT_TENANT_ID));
  }

  @Bean
  @Order(3)
  public SecurityFilterChain ptTenantaWebappChain(final HttpSecurity http) throws Exception {
    return chainFactory.buildWebappChain(http, sliceFor(TENANTA_TENANT_ID));
  }

  @Bean
  @Order(4)
  public SecurityFilterChain ptDefaultWebappChain(final HttpSecurity http) throws Exception {
    return chainFactory.buildWebappChain(http, sliceFor(DEFAULT_TENANT_ID));
  }

  @Bean
  @Order(5)
  public SecurityFilterChain ptDefaultUnprefixedApiChain(
      final HttpSecurity http, final JwtDecoder jwtDecoder) throws Exception {
    return chainFactory.buildApiChain(
        http, sliceForUnprefixedDefault(), jwtDecoder, allowedIssuersFor(DEFAULT_TENANT_ID));
  }

  @Bean
  @Order(6)
  public SecurityFilterChain ptDefaultUnprefixedWebappChain(final HttpSecurity http)
      throws Exception {
    return chainFactory.buildWebappChain(http, sliceForUnprefixedDefault());
  }

  /**
   * Builds the {@link TenantSecuritySlice} for one prefixed tenant. Pure lookup: both the
   * per-tenant {@link ClientRegistrationRepository} and the per-tenant {@link WebSessionRepository}
   * are pre-assembled by the pt-security profile gates inside {@code
   * OidcOverrideBeansConfiguration} and {@code WebSessionRepositoryConfiguration}, then injected as
   * map beans.
   */
  private TenantSecuritySlice sliceFor(final String tenantId) {
    final ClientRegistrationRepository repo = ptClientRegistrationRepositories.get(tenantId);
    if (repo == null) {
      throw new IllegalStateException(
          "No ClientRegistrationRepository bean for physical tenant '" + tenantId + "'");
    }
    final var cookieAndFilter = perChainSessionFilter(tenantId);
    return new TenantSecuritySlice(
        tenantId,
        AccessPath.PREFIXED,
        repo,
        cookieAndFilter.sessionFilter(),
        cookieAndFilter.idResolver());
  }

  /**
   * Builds the {@link TenantSecuritySlice} for the default tenant on the <b>unprefixed</b> access
   * path (Task 12). Reuses the default tenant's {@link WebSessionRepository} — the different cookie
   * (name {@code camunda-session-default-root}, {@code Path=/}) yields a different session id, so
   * the prefixed and unprefixed access paths hold independent sessions in the same backing map.
   * Cookie isolation forces a fresh OAuth2 login when the user switches access paths (spec OQ-2).
   * The {@link ClientRegistrationRepository} is the unprefixed-default repo from {@code
   * OidcOverrideBeansConfiguration}: it skips the redirect-URI rewrite so the IdP redirects back to
   * {@code /login/oauth2/code/{registrationId}} which the unprefixed webapp chain handles.
   */
  private TenantSecuritySlice sliceForUnprefixedDefault() {
    final WebSessionRepository repository = ptWebSessionRepositories.get(DEFAULT_TENANT_ID);
    if (repository == null) {
      throw new IllegalStateException(
          "No WebSessionRepository bean for physical tenant '" + DEFAULT_TENANT_ID + "'");
    }
    final ClientRegistrationRepository unprefixedRepo =
        ptUnprefixedDefaultClientRegistrationRepositories.get(DEFAULT_TENANT_ID);
    if (unprefixedRepo == null) {
      throw new IllegalStateException(
          "No unprefixed-default ClientRegistrationRepository bean for physical tenant '"
              + DEFAULT_TENANT_ID
              + "'");
    }
    final var serializer = PhysicalTenantCookieSerializer.forUnprefixedDefaultChain();
    final CookieHttpSessionIdResolver resolver =
        PhysicalTenantCookieSerializer.resolver(serializer);
    final SessionRepositoryFilter<WebSession> sessionFilter =
        new SessionRepositoryFilter<>(repository);
    sessionFilter.setHttpSessionIdResolver(resolver);
    return new TenantSecuritySlice(
        DEFAULT_TENANT_ID, AccessPath.UNPREFIXED_DEFAULT, unprefixedRepo, sessionFilter, resolver);
  }

  private Set<String> allowedIssuersFor(final String tenantId) {
    final Set<String> issuers = ptAllowedIssuersPerTenant.get(tenantId);
    if (issuers == null) {
      throw new IllegalStateException(
          "No allowed-issuers entry for physical tenant '" + tenantId + "'");
    }
    return issuers;
  }

  /**
   * Builds a per-chain {@link SessionRepositoryFilter} whose cookie is scoped to the given tenant's
   * URL space. The cookie {@code Path} attribute is the entire browser-side isolation primitive
   * (spec D2): a cookie at {@code Path=/physical-tenant/<t>} is never sent to a different tenant's
   * URLs (RFC 6265 path-matching).
   *
   * <p>The session store is the tenant's own {@link WebSessionRepository} bean from {@link
   * #ptWebSessionRepositories} — each tenant has its own instance with its own private backing
   * store. Storage isolation is structural; there is no shared backend and no key-prefixing
   * decorator.
   */
  private SessionFilterAndResolver perChainSessionFilter(final String tenantId) {
    // Lax (not Strict) so the IdP return leg — a top-level navigation back to the
    // OAuth2 callback URL — carries the session cookie. See PhysicalTenantCookieSerializer.
    final var serializer = PhysicalTenantCookieSerializer.forPrefixedChain(tenantId);
    final CookieHttpSessionIdResolver sessionIdResolver =
        PhysicalTenantCookieSerializer.resolver(serializer);

    final WebSessionRepository repository = ptWebSessionRepositories.get(tenantId);
    if (repository == null) {
      throw new IllegalStateException(
          "No WebSessionRepository bean for physical tenant '" + tenantId + "'");
    }
    final SessionRepositoryFilter<WebSession> sessionFilter =
        new SessionRepositoryFilter<>(repository);
    sessionFilter.setHttpSessionIdResolver(sessionIdResolver);
    return new SessionFilterAndResolver(sessionFilter, sessionIdResolver);
  }

  /** Local pair so the slice carries both the filter and the cookie id-resolver. */
  private record SessionFilterAndResolver(
      SessionRepositoryFilter<?> sessionFilter, CookieHttpSessionIdResolver idResolver) {}
}
