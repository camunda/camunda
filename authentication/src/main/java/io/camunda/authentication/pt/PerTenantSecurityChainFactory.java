/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;

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
    // After a successful login, land the user on the PoC's SPA app page (relative to the
    // chain's access path). Without this, Spring Security's default-success-handler falls
    // back to "/" when there's no saved request — and the PoC has no handler at "/", so
    // an entry via the root would 404 right after authentication.
    final String defaultSuccessUrl = prefix + "/app";

    final OAuth2AuthorizationRequestResolver resolver =
        prefixAwareResolver(slice.clientRegistrationRepository(), authPrefix);

    return http.securityMatcher(securityMatcher)
        .addFilterBefore(slice.sessionRepositoryFilter(), SecurityContextHolderFilter.class)
        .authorizeHttpRequests(a -> a.anyRequest().authenticated())
        // Replace the default HttpSessionRequestCache with a NullRequestCache: with
        // alwaysUse=true on defaultSuccessUrl below, we never need to redirect the user
        // back to the URL they originally requested, so there is no reason to save it.
        // The default cache would call request.getSession() to store the saved request,
        // which creates a session and emits a Set-Cookie on the response — even for
        // anonymous background requests like /favicon.ico, /robots.txt, or any URL the
        // chain's matcher happens to claim. With NullRequestCache the chain only creates
        // a session when an actual OAuth2 flow is in progress (state storage at the start
        // of the authorization request, SecurityContext storage at the end of the
        // callback) — and those are the only legitimate reasons for a session here.
        .requestCache(rc -> rc.requestCache(new NullRequestCache()))
        .oauth2Login(
            l ->
                l.clientRegistrationRepository(slice.clientRegistrationRepository())
                    .authorizationEndpoint(
                        // baseUri drives entry-point link generation (getLoginLinks);
                        // authorizationRequestResolver replaces the URL→registrationId
                        // matching that Spring Security 7's PathPatternRequestMatcher
                        // mishandles for multi-segment prefixes. Both are needed.
                        ae -> ae.baseUri(authBaseUri).authorizationRequestResolver(resolver))
                    .redirectionEndpoint(re -> re.baseUri(callbackBaseUri))
                    // alwaysUse=true overrides any saved request — the PoC wants every
                    // login to terminate at /app for the demo, not at whatever URL
                    // triggered the redirect (notably "/" which has no controller).
                    .defaultSuccessUrl(defaultSuccessUrl, true))
        .build();
  }

  /**
   * Builds a session-or-bearer API chain for a tenant. The chain matches BOTH supported API URL
   * schemes (see spec D7):
   *
   * <ul>
   *   <li>{@code /physical-tenant/<tenant>/v2/**} — webapp/SPA addressing. Lives <b>inside</b> the
   *       webapp cookie's {@code Path=/physical-tenant/<t>} scope, so the browser sends the
   *       per-tenant session cookie on these requests automatically.
   *   <li>{@code /v2/physical-tenants/<tenant>/**} — direct API-client addressing (the existing PT
   *       REST infrastructure scheme). <b>Outside</b> the cookie's {@code Path} scope, so cookie
   *       auth does not apply on this URL; clients authenticate with bearer tokens.
   * </ul>
   *
   * The chain accepts <i>either</i> form of authentication:
   *
   * <ul>
   *   <li><b>Session</b>: the tenant's {@link
   *       org.springframework.session.web.http.SessionRepositoryFilter} resolves the cookie against
   *       the same {@code WebSessionRepository} the webapp chain uses; this chain's {@link
   *       HttpSessionSecurityContextRepository} (with {@code allowSessionCreation=false} so the API
   *       chain never creates a session of its own) then loads the {@code SecurityContext} saved by
   *       the webapp chain at OAuth2 login. Authentication arrives as {@link
   *       OAuth2AuthenticationToken} — already validated on this tenant's webapp chain, so
   *       authenticated ⇒ allowed. In practice this branch only fires on the webapp-aligned URL
   *       because the cookie isn't sent to the API-client URL.
   *   <li><b>Bearer</b>: a non-browser API client presents {@code Authorization: Bearer <jwt>}.
   *       {@code oauth2ResourceServer.jwt()} produces a {@link JwtAuthenticationToken}; the
   *       per-chain issuer allowlist still applies (cross-tenant tokens get 403, spec D6). Applies
   *       to both URLs.
   * </ul>
   *
   * <p>The {@link JwtDecoder} is <b>cluster-shared</b> — one issuer-aware decoder built from the
   * union of all tenants' issuer URIs validates signatures for any known token. Per-tenant
   * isolation for bearer flows comes from the {@code allowedIssuers} allowlist enforced by the
   * authorization manager.
   *
   * <p>For unauthenticated requests (no cookie, no Bearer) Spring Security's {@code
   * oauth2ResourceServer} authentication entry point returns 401 with {@code WWW-Authenticate:
   * Bearer} — the right answer for an API surface.
   */
  public SecurityFilterChain buildApiChain(
      final HttpSecurity http,
      final TenantSecuritySlice slice,
      final JwtDecoder sharedDecoder,
      final Set<String> allowedIssuers,
      final Set<String> expectedAudiences)
      throws Exception {
    final String[] securityMatchers;
    if (slice.accessPath() == TenantSecuritySlice.AccessPath.PREFIXED) {
      securityMatchers =
          new String[] {
            // webapp/SPA-aligned URL — inside the per-tenant cookie's Path scope.
            "/physical-tenant/" + slice.tenantId() + "/v2/**",
            // direct API-client URL — outside the cookie scope, bearer-only in practice.
            "/v2/physical-tenants/" + slice.tenantId() + "/**"
          };
    } else {
      securityMatchers = new String[] {"/v2/**"};
    }

    final AuthorizationManager<RequestAuthorizationContext> sessionOrBearer =
        (authSupplier, ctx) -> {
          final var authentication = authSupplier.get();
          if (authentication == null || !authentication.isAuthenticated()) {
            return new AuthorizationDecision(false);
          }
          // Session-derived: the webapp chain already authenticated this user on this tenant.
          // We deliberately do NOT re-check the audience here: the session was created via OAuth2
          // login on a webapp chain whose ClientRegistrationRepository only knows about THIS
          // tenant's assigned providers (per-tenant scoping in OidcOverrideBeansConfiguration),
          // so the underlying access token was already issued by a client this tenant owns and
          // its aud claim was implicitly validated at issuance. (An explicit
          // oauth.getAuthorizedClientRegistrationId() check against this tenant's registration
          // ids would be strictly redundant given that scoping.)
          if (authentication instanceof OAuth2AuthenticationToken) {
            return new AuthorizationDecision(true);
          }
          // Bearer: apply the per-chain issuer allowlist (spec D6) AND the per-tenant audience
          // allowlist (spec D8 — needed when an IdP is shared between PTs, where iss alone
          // can't separate tenants).
          if (authentication instanceof JwtAuthenticationToken jwt) {
            final var token = jwt.getToken();
            final var iss = token.getIssuer();
            final boolean issuerOk = iss != null && allowedIssuers.contains(iss.toString());
            // Empty expectedAudiences -> "skip the audience check" (back-compat).
            // Non-empty -> at least one of the token's aud entries must be allowlisted.
            final List<String> tokenAudiences = token.getAudience();
            final boolean audienceOk =
                expectedAudiences.isEmpty()
                    || (tokenAudiences != null
                        && tokenAudiences.stream().anyMatch(expectedAudiences::contains));
            return new AuthorizationDecision(issuerOk && audienceOk);
          }
          // Anonymous or any other token type: deny (oauth2ResourceServer's entry point
          // will turn that into a 401 with WWW-Authenticate: Bearer).
          return new AuthorizationDecision(false);
        };

    // Read-but-don't-create the session-stored SecurityContext. With SessionCreationPolicy
    // STATELESS, Spring Security installs a NullSecurityContextRepository and ignores the
    // session entirely — even though SessionRepositoryFilter resolves it, the
    // SecurityContextHolderFilter wouldn't load anything from it. Instead we keep the
    // default IF_REQUIRED policy and explicitly install an HttpSessionSecurityContextRepository
    // with allowSessionCreation=false: it reads the SecurityContext saved by the webapp chain
    // at OAuth2 login but never creates a new session for unauthenticated API requests.
    final HttpSessionSecurityContextRepository sessionContextRepository =
        new HttpSessionSecurityContextRepository();
    sessionContextRepository.setAllowSessionCreation(false);

    return http.securityMatcher(securityMatchers)
        .csrf(c -> c.disable())
        .addFilterBefore(slice.sessionRepositoryFilter(), SecurityContextHolderFilter.class)
        .securityContext(sc -> sc.securityContextRepository(sessionContextRepository))
        .authorizeHttpRequests(a -> a.anyRequest().access(sessionOrBearer))
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
