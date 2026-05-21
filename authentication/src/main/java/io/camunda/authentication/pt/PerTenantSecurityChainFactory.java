/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.context.SecurityContextHolderFilter;

/**
 * Builds a per-tenant {@link SecurityFilterChain} from a {@link TenantSecuritySlice}. Replaces the
 * two near-duplicate {@code @Bean} method bodies that previously lived in {@link
 * PhysicalTenantSecurityConfiguration}.
 *
 * <p>The chain shape:
 *
 * <ul>
 *   <li>{@code securityMatcher} bound to the tenant's path prefix
 *   <li>per-chain {@link org.springframework.session.web.http.SessionRepositoryFilter} added before
 *       {@link SecurityContextHolderFilter} so the {@code HttpSession} backing the security context
 *       is the tenant-scoped one
 *   <li>{@code oauth2Login} wired with a custom prefix-aware {@link
 *       OAuth2AuthorizationRequestResolver} — see {@link #prefixAwareResolver}
 * </ul>
 */
@NullMarked
public final class PerTenantSecurityChainFactory {

  public SecurityFilterChain buildWebappChain(
      final HttpSecurity http, final TenantSecuritySlice slice) throws Exception {
    final String prefix = slice.webappPathPrefix();
    final String securityMatcher = prefix.isEmpty() ? "/**" : prefix + "/**";
    final String authBaseUri = prefix + "/oauth2/authorization";
    final String authPrefix = authBaseUri + "/";
    final String callbackBaseUri = prefix + "/login/oauth2/code/*";

    final OAuth2AuthorizationRequestResolver resolver =
        prefixAwareResolver(slice.clientRegistrationRepository(), authPrefix);

    return http.securityMatcher(securityMatcher)
        .addFilterBefore(slice.sessionRepositoryFilter(), SecurityContextHolderFilter.class)
        .authorizeHttpRequests(a -> a.anyRequest().authenticated())
        .oauth2Login(
            l ->
                l.clientRegistrationRepository(slice.clientRegistrationRepository())
                    .authorizationEndpoint(
                        // baseUri drives entry-point link generation (getLoginLinks);
                        // authorizationRequestResolver replaces the URL→registrationId
                        // matching that Spring Security 7's PathPatternRequestMatcher
                        // mishandles for multi-segment prefixes. Both are needed.
                        ae -> ae.baseUri(authBaseUri).authorizationRequestResolver(resolver))
                    .redirectionEndpoint(re -> re.baseUri(callbackBaseUri)))
        .build();
  }

  /**
   * Builds a stateless bearer-token API chain for {@code /v2/physical-tenants/<tenant>/**}.
   *
   * <p>The {@link JwtDecoder} is <b>cluster-shared</b> — one issuer-aware decoder built from the
   * union of all tenants' issuer URIs validates signatures for any known token. Per-tenant
   * isolation comes from the {@code allowedIssuers} <b>allowlist</b>: each chain's authorization
   * rule rejects authenticated tokens whose {@code iss} claim is not in that tenant's assigned
   * issuer set. Cross-tenant valid tokens get 403, not 401 — they authenticated successfully but
   * lack authorization for this tenant's API surface (spec D6).
   *
   * <p>The slice's {@code clientRegistrationRepository} / {@code sessionRepositoryFilter} / {@code
   * httpSessionIdResolver} fields are intentionally unused here — API chains are stateless ({@link
   * SessionCreationPolicy#NEVER}) and don't run an OAuth2 login flow. We accept the slice anyway so
   * the chain factory has one input shape; the unused fields are a known and acceptable cost.
   */
  public SecurityFilterChain buildApiChain(
      final HttpSecurity http,
      final TenantSecuritySlice slice,
      final JwtDecoder sharedDecoder,
      final Set<String> allowedIssuers)
      throws Exception {
    final String securityMatcher =
        slice.accessPath() == TenantSecuritySlice.AccessPath.PREFIXED
            ? "/v2/physical-tenants/" + slice.tenantId() + "/**"
            : "/v2/**";

    final AuthorizationManager<RequestAuthorizationContext> issuerAllowed =
        (auth, ctx) -> {
          final var a = auth.get();
          if (!(a instanceof JwtAuthenticationToken jwt)) {
            return new AuthorizationDecision(false);
          }
          final var iss = jwt.getToken().getIssuer();
          return new AuthorizationDecision(iss != null && allowedIssuers.contains(iss.toString()));
        };

    return http.securityMatcher(securityMatcher)
        .csrf(c -> c.disable())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.NEVER))
        .authorizeHttpRequests(a -> a.anyRequest().access(issuerAllowed))
        .oauth2ResourceServer(o -> o.jwt(j -> j.decoder(sharedDecoder)))
        .build();
  }

  /**
   * Manual prefix-matching resolver. Bypasses Spring Security 7's {@link
   * DefaultOAuth2AuthorizationRequestResolver} string-baseUri matcher which does not reliably match
   * the multi-segment {@code /physical-tenant/<id>/oauth2/authorization} prefix; we do the
   * URL→registrationId split via {@link String#startsWith} and delegate the actual {@link
   * OAuth2AuthorizationRequest} construction to a default resolver bound to the canonical
   * single-segment baseUri.
   */
  static OAuth2AuthorizationRequestResolver prefixAwareResolver(
      final ClientRegistrationRepository repo, final String authPrefix) {
    final DefaultOAuth2AuthorizationRequestResolver delegate =
        new DefaultOAuth2AuthorizationRequestResolver(repo, "/oauth2/authorization");
    return new OAuth2AuthorizationRequestResolver() {
      @Override
      public @Nullable OAuth2AuthorizationRequest resolve(final HttpServletRequest request) {
        final String uri = request.getRequestURI();
        if (!uri.startsWith(authPrefix)) {
          return null;
        }
        final String registrationId = uri.substring(authPrefix.length());
        return delegate.resolve(request, registrationId);
      }

      @Override
      public @Nullable OAuth2AuthorizationRequest resolve(
          final HttpServletRequest request, final String registrationId) {
        return delegate.resolve(request, registrationId);
      }
    };
  }
}
