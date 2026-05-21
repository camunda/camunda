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
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
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
  //   1) sliceFor(...) — builds the TenantSecuritySlice (ClientRegistration, session filter,
  //      cookie resolver)
  //   2) factory.buildWebappChain(http, slice) — applies the chain shape (security matcher,
  //      filter ordering, oauth2Login with the prefix-aware resolver workaround)
  //
  // tenant A binds the NAMED OIDC provider "tenanta" under
  //   camunda.security.authentication.providers.oidc.tenanta.*
  // default tenant binds the cluster-default authentication.oidc.* slot (registration id "oidc").
  private static final String TENANTA_TENANT_ID = "tenanta";
  private static final String TENANTA_REGISTRATION_ID = "tenanta";

  private static final String DEFAULT_TENANT_ID = "default";
  private static final String DEFAULT_REGISTRATION_ID = "oidc";

  private final PerTenantSecurityChainFactory chainFactory = new PerTenantSecurityChainFactory();

  @Bean
  public SecurityFilterChain ptTenantaWebappChain(
      final HttpSecurity http, final SecurityConfiguration security) throws Exception {
    final var providerConfig =
        security.getAuthentication().getProviders().getOidc().get(TENANTA_REGISTRATION_ID);
    return chainFactory.buildWebappChain(
        http,
        sliceFor(
            TENANTA_TENANT_ID,
            TENANTA_REGISTRATION_ID,
            providerConfig.getIssuerUri(),
            providerConfig.getClientId(),
            providerConfig.getClientSecret()));
  }

  @Bean
  public SecurityFilterChain ptDefaultWebappChain(
      final HttpSecurity http, final SecurityConfiguration security) throws Exception {
    final var providerConfig = security.getAuthentication().getOidc();
    return chainFactory.buildWebappChain(
        http,
        sliceFor(
            DEFAULT_TENANT_ID,
            DEFAULT_REGISTRATION_ID,
            providerConfig.getIssuerUri(),
            providerConfig.getClientId(),
            providerConfig.getClientSecret()));
  }

  /**
   * Builds the {@link TenantSecuritySlice} for one prefixed tenant: resolves the IdP metadata via
   * OIDC discovery, builds the {@link ClientRegistration} with explicit scopes (Keycloak refuses to
   * issue an OIDC token without {@code openid}; {@code ClientRegistrations.fromIssuerLocation} does
   * NOT seed default scopes), and constructs the per-chain session filter scoped to the tenant's
   * URL space.
   */
  private static TenantSecuritySlice sliceFor(
      final String tenantId,
      final String registrationId,
      final String issuerUri,
      final String clientId,
      final String clientSecret) {
    final String prefix = "/physical-tenant/" + tenantId;

    final ClientRegistration registration =
        ClientRegistrations.fromIssuerLocation(issuerUri)
            .registrationId(registrationId)
            .clientId(clientId)
            .clientSecret(clientSecret)
            .scope("openid", "profile", "email")
            .redirectUri(
                PhysicalTenantRedirectUriRewriter.rewrite(
                    "{baseUrl}/login/oauth2/code/{registrationId}", tenantId))
            .build();

    final ClientRegistrationRepository repo =
        new InMemoryClientRegistrationRepository(registration);

    final var cookieAndFilter = perChainSessionFilter("camunda-session-" + tenantId, prefix);

    return new TenantSecuritySlice(
        tenantId,
        AccessPath.PREFIXED,
        repo,
        cookieAndFilter.sessionFilter(),
        cookieAndFilter.idResolver());
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
