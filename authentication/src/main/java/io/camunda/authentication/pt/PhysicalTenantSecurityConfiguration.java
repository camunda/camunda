/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static java.util.stream.Collectors.toUnmodifiableSet;

import io.camunda.authentication.pt.TenantSecuritySlice.AccessPath;
import io.camunda.authentication.session.WebSession;
import io.camunda.authentication.session.WebSessionRepository;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
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
  //      machinery from the map beans injected here (assembled in dist by
  //      PhysicalTenantOidcConfiguration / PhysicalTenantWebSessionRepositoryConfiguration).
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

  public PhysicalTenantSecurityConfiguration(
      final Map<String, WebSessionRepository> ptWebSessionRepositories,
      final Map<String, ClientRegistrationRepository> ptClientRegistrationRepositories,
      final Map<String, Set<String>> ptAllowedIssuersPerTenant) {
    this.ptWebSessionRepositories = ptWebSessionRepositories;
    this.ptClientRegistrationRepositories = ptClientRegistrationRepositories;
    this.ptAllowedIssuersPerTenant = ptAllowedIssuersPerTenant;
  }

  @Bean
  public SecurityFilterChain ptTenantaWebappChain(final HttpSecurity http) throws Exception {
    return chainFactory.buildWebappChain(http, sliceFor(TENANTA_TENANT_ID));
  }

  @Bean
  public SecurityFilterChain ptDefaultWebappChain(final HttpSecurity http) throws Exception {
    return chainFactory.buildWebappChain(http, sliceFor(DEFAULT_TENANT_ID));
  }

  /**
   * Cluster-shared issuer-aware {@link JwtDecoder} for the API chains. One bean serves <b>all</b>
   * tenants' API chains; signature validation routes by the JWT's {@code iss} claim across the
   * union of all assigned issuer URIs. Per-tenant isolation is enforced at the chain level via an
   * issuer allowlist (see {@link PerTenantSecurityChainFactory#buildApiChain}), not via per-tenant
   * decoders.
   *
   * <p>Bean name is deliberately distinct from {@code jwtDecoder} (the consolidated-auth host bean)
   * to avoid type-based autowiring collisions; the API chains pass this bean explicitly to {@code
   * oauth2ResourceServer.jwt().decoder(...)}.
   */
  @Bean
  public JwtDecoder ptIssuerAwareJwtDecoder(
      final Map<String, Set<String>> ptAllowedIssuersPerTenant) {
    final Set<String> allIssuers =
        ptAllowedIssuersPerTenant.values().stream()
            .flatMap(Set::stream)
            .collect(toUnmodifiableSet());
    return new PtIssuerAwareJwtDecoder(allIssuers);
  }

  @Bean
  public SecurityFilterChain ptTenantaApiChain(
      final HttpSecurity http, final JwtDecoder ptIssuerAwareJwtDecoder) throws Exception {
    return chainFactory.buildApiChain(
        http,
        sliceFor(TENANTA_TENANT_ID),
        ptIssuerAwareJwtDecoder,
        allowedIssuersFor(TENANTA_TENANT_ID));
  }

  @Bean
  public SecurityFilterChain ptDefaultPrefixedApiChain(
      final HttpSecurity http, final JwtDecoder ptIssuerAwareJwtDecoder) throws Exception {
    return chainFactory.buildApiChain(
        http,
        sliceFor(DEFAULT_TENANT_ID),
        ptIssuerAwareJwtDecoder,
        allowedIssuersFor(DEFAULT_TENANT_ID));
  }

  /**
   * Builds the {@link TenantSecuritySlice} for one prefixed tenant. Pure lookup: both the
   * per-tenant {@link ClientRegistrationRepository} and the per-tenant {@link WebSessionRepository}
   * are pre-assembled in {@code dist} (see {@code PhysicalTenantOidcConfiguration} and {@code
   * PhysicalTenantWebSessionRepositoryConfiguration}) and injected as map beans.
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
