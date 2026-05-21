/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;
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
