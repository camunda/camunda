/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import io.camunda.security.configuration.SecurityConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@Profile("pt-security")
public class PhysicalTenantSecurityConfiguration {

  // Walking-skeleton chain for tenant A. Reads the NAMED OIDC provider "tenanta" from
  // camunda.security.authentication.providers.oidc.tenanta.*. The cluster-default
  // authentication.oidc.* slot is reserved for the default tenant (added in Task 5).
  //
  // OAuth2 paths are prefixed with the tenant's URL space so each chain has dedicated
  // authorize/callback URLs (per-chain isolation goal). Spring Security 7's
  // DefaultOAuth2AuthorizationRequestResolver wires an internal PathPatternRequestMatcher
  // from a string baseUri; in this version it silently fails to extract a registration id
  // from a multi-segment prefix path, falling through to the AuthorizationFilter and
  // causing a redirect loop on /oauth2/authorization/{regId}. We bypass it by providing a
  // custom resolver that does prefix matching via String.startsWith and delegates the
  // OAuth2AuthorizationRequest construction to the default resolver via the (request,
  // registrationId) overload.
  private static final String TENANTA_REGISTRATION_ID = "tenanta";
  private static final String TENANTA_PREFIX = "/physical-tenant/tenanta";
  private static final String TENANTA_AUTH_PREFIX = TENANTA_PREFIX + "/oauth2/authorization/";
  private static final String TENANTA_CALLBACK_PREFIX = TENANTA_PREFIX + "/login/oauth2/code/*";

  @Bean
  public SecurityFilterChain ptTenantaWebappChain(
      final HttpSecurity http, final SecurityConfiguration security) throws Exception {

    final var providerConfig =
        security.getAuthentication().getProviders().getOidc().get(TENANTA_REGISTRATION_ID);

    // Spring Security's ClientRegistrations.fromIssuerLocation seeds the builder from
    // the IdP discovery document but does NOT populate default scopes. Without an
    // explicit .scope(...) the OAuth2 authorization request is sent with no scope
    // parameter; Keycloak then issues a token that lacks `openid`/`profile`/`email`,
    // and the post-callback userinfo lookup returns 403. Explicit scopes are required.
    final ClientRegistration registration =
        ClientRegistrations.fromIssuerLocation(providerConfig.getIssuerUri())
            .registrationId(TENANTA_REGISTRATION_ID)
            .clientId(providerConfig.getClientId())
            .clientSecret(providerConfig.getClientSecret())
            .scope("openid", "profile", "email")
            .redirectUri("{baseUrl}/physical-tenant/tenanta/login/oauth2/code/{registrationId}")
            .build();

    final ClientRegistrationRepository repo =
        new InMemoryClientRegistrationRepository(registration);

    final OAuth2AuthorizationRequestResolver resolver = prefixAwareResolver(repo);

    return http.securityMatcher(TENANTA_PREFIX + "/**")
        .authorizeHttpRequests(a -> a.anyRequest().authenticated())
        .oauth2Login(
            l ->
                l.clientRegistrationRepository(repo)
                    .authorizationEndpoint(
                        // baseUri drives entry-point link generation (getLoginLinks);
                        // authorizationRequestResolver replaces the URL→registrationId
                        // matching that Spring Security 7's PathPatternRequestMatcher
                        // mishandles for multi-segment prefixes. Both are needed.
                        ae ->
                            ae.baseUri(TENANTA_PREFIX + "/oauth2/authorization")
                                .authorizationRequestResolver(resolver))
                    .redirectionEndpoint(re -> re.baseUri(TENANTA_CALLBACK_PREFIX)))
        .build();
  }

  /**
   * Manual prefix-matching resolver. Bypasses Spring Security 7's
   * {@link DefaultOAuth2AuthorizationRequestResolver} string-baseUri matcher which does
   * not reliably match the multi-segment {@code /physical-tenant/<id>/oauth2/authorization}
   * prefix.
   */
  private static OAuth2AuthorizationRequestResolver prefixAwareResolver(
      final ClientRegistrationRepository repo) {
    final DefaultOAuth2AuthorizationRequestResolver delegate =
        new DefaultOAuth2AuthorizationRequestResolver(repo, "/oauth2/authorization");
    return new OAuth2AuthorizationRequestResolver() {
      @Override
      public OAuth2AuthorizationRequest resolve(final HttpServletRequest request) {
        final String uri = request.getRequestURI();
        if (!uri.startsWith(TENANTA_AUTH_PREFIX)) {
          return null;
        }
        final String registrationId = uri.substring(TENANTA_AUTH_PREFIX.length());
        return delegate.resolve(request, registrationId);
      }

      @Override
      public OAuth2AuthorizationRequest resolve(
          final HttpServletRequest request, final String registrationId) {
        return delegate.resolve(request, registrationId);
      }
    };
  }
}
