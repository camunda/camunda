/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import io.camunda.authentication.pt.TenantSecuritySlice.AccessPath;
import io.camunda.security.configuration.SecurityConfiguration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.SessionRepositoryFilter;

@Configuration
@EnableWebSecurity
@Profile("pt-security")
public class PhysicalTenantSecurityConfiguration {

  // Two near-symmetric @Bean methods, one per tenant. Each reduces to:
  //   1) sliceFor(...) — resolves the tenant's SecurityConfiguration from the
  //      camunda.physical-tenants.<tenantId>.security.* overlay, builds the
  //      ClientRegistrationRepository via PerTenantOidcRegistry (which consumes the
  //      tenant's providers.assigned list), and wraps it with per-chain session machinery.
  //   2) factory.buildWebappChain(http, slice) — applies the chain shape (security matcher,
  //      filter ordering, oauth2Login with the prefix-aware resolver workaround).
  //
  // Tenant A's providers.assigned list resolves a NAMED OIDC provider "tenanta" under
  //   camunda.physical-tenants.tenanta.security.authentication.providers.oidc.tenanta.*
  // The default tenant's providers.assigned list resolves the cluster-default
  //   camunda.physical-tenants.default.security.authentication.oidc.* (registration id "oidc").
  private static final String TENANTA_TENANT_ID = "tenanta";
  private static final String DEFAULT_TENANT_ID = "default";

  private static final String PHYSICAL_TENANTS_PREFIX = "camunda.physical-tenants";

  private final PerTenantSecurityChainFactory chainFactory = new PerTenantSecurityChainFactory();

  @Bean
  public SecurityFilterChain ptTenantaWebappChain(
      final HttpSecurity http, final Environment environment) throws Exception {
    return chainFactory.buildWebappChain(http, sliceFor(TENANTA_TENANT_ID, environment));
  }

  @Bean
  public SecurityFilterChain ptDefaultWebappChain(
      final HttpSecurity http, final Environment environment) throws Exception {
    return chainFactory.buildWebappChain(http, sliceFor(DEFAULT_TENANT_ID, environment));
  }

  /**
   * Builds the {@link TenantSecuritySlice} for one prefixed tenant.
   *
   * <p>Step 1: bind the tenant's {@code camunda.physical-tenants.<id>.security.*} overlay into a
   * fresh {@link SecurityConfiguration}. We bind directly via Spring's {@link Binder} because the
   * authentication module does not depend on the {@code configuration} module that hosts {@code
   * PhysicalTenantResolver} — and pulling that dependency in just for the PoC is a heavier change
   * than this small binding.
   *
   * <p>Step 2: read the tenant's {@code providers.assigned} list. The external CSL {@code
   * OidcProvidersConfiguration} does not carry an {@code assigned} field, so we bind it as a
   * separate property and hand it to the registry alongside the tenant {@link
   * SecurityConfiguration}.
   *
   * <p>Step 3: hand both to {@link PerTenantOidcRegistry}. The registry resolves each entry in
   * {@code assigned} to either the default {@code authentication.oidc.*} slot (literal name {@code
   * "oidc"}) or a named {@code authentication.providers.oidc.<name>.*} slot, builds one {@link
   * org.springframework.security.oauth2.client.registration.ClientRegistration} per resolved
   * provider via OIDC discovery, and returns the assembled {@code ClientRegistrationRepository}.
   *
   * <p>Step 4: wrap the repository with this tenant's per-chain session filter / cookie resolver
   * and emit the slice.
   */
  private TenantSecuritySlice sliceFor(final String tenantId, final Environment environment) {
    final String prefix = "/physical-tenant/" + tenantId;

    final SecurityConfiguration tenantSecurity = bindTenantSecurity(tenantId, environment);
    final List<String> assigned = bindAssigned(tenantId, environment);

    final ClientRegistrationRepository repo =
        PerTenantOidcRegistry.forTenant(tenantId, tenantSecurity, assigned)
            .clientRegistrationRepository();

    final var cookieAndFilter = perChainSessionFilter("camunda-session-" + tenantId, prefix);

    return new TenantSecuritySlice(
        tenantId,
        AccessPath.PREFIXED,
        repo,
        cookieAndFilter.sessionFilter(),
        cookieAndFilter.idResolver());
  }

  private static SecurityConfiguration bindTenantSecurity(
      final String tenantId, final Environment environment) {
    final var tenantSecurity = new SecurityConfiguration();
    Binder.get(environment)
        .bind(
            PHYSICAL_TENANTS_PREFIX + "." + tenantId + ".security",
            Bindable.ofInstance(tenantSecurity));
    return tenantSecurity;
  }

  @SuppressWarnings("unchecked")
  private static List<String> bindAssigned(final String tenantId, final Environment environment) {
    final var bound =
        Binder.get(environment)
            .bind(
                PHYSICAL_TENANTS_PREFIX
                    + "."
                    + tenantId
                    + ".security.authentication.providers.assigned",
                Bindable.listOf(String.class));
    return bound.isBound() ? (List<String>) bound.get() : List.of();
  }

  /**
   * Builds a per-chain {@link SessionRepositoryFilter} whose cookie is scoped to the given tenant's
   * URL space. The cookie {@code Path} attribute is the entire browser-side isolation primitive
   * (spec D2): a cookie at {@code Path=/physical-tenant/<t>} is never sent to a different tenant's
   * URLs (RFC 6265 path-matching).
   *
   * <p>The session store is an in-memory {@link MapSessionRepository} stub. Task 9 replaces it with
   * a per-tenant {@code WebSessionRepository} bound to each tenant's dedicated secondary storage;
   * for the walking skeleton, in-memory survives one process lifetime and is enough to verify the
   * cookie isolation.
   */
  private static SessionFilterAndResolver perChainSessionFilter(
      final String cookieName, final String cookiePath) {
    final DefaultCookieSerializer serializer = new DefaultCookieSerializer();
    serializer.setCookieName(cookieName);
    serializer.setCookiePath(cookiePath);
    serializer.setUseHttpOnlyCookie(true);
    // Lax (not Strict) so the IdP return leg — a top-level navigation back to the
    // OAuth2 callback URL — carries the session cookie.
    serializer.setSameSite("Lax");

    final CookieHttpSessionIdResolver sessionIdResolver = new CookieHttpSessionIdResolver();
    sessionIdResolver.setCookieSerializer(serializer);

    final SessionRepositoryFilter<MapSession> sessionFilter =
        new SessionRepositoryFilter<>(new MapSessionRepository(new ConcurrentHashMap<>()));
    sessionFilter.setHttpSessionIdResolver(sessionIdResolver);
    return new SessionFilterAndResolver(sessionFilter, sessionIdResolver);
  }

  /** Local pair so the slice carries both the filter and the cookie id-resolver. */
  private record SessionFilterAndResolver(
      SessionRepositoryFilter<?> sessionFilter, CookieHttpSessionIdResolver idResolver) {}
}
