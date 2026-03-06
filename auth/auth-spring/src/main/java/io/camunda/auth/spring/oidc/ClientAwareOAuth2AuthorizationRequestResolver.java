/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.oidc;

import io.camunda.auth.domain.config.OidcAuthenticationConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest.Builder;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * An implementation of {@link OAuth2AuthorizationRequestResolver} that is aware of individual OIDC
 * client configurations and dynamically resolves authorization requests based on the registration
 * ID extracted from the request URI.
 */
public class ClientAwareOAuth2AuthorizationRequestResolver
    implements OAuth2AuthorizationRequestResolver {

  private static final String ERROR_INVALID_CLIENT_REGISTRATION_ID =
      "Invalid Client Registration with ID '%s'";
  private static final String AUTHORIZATION_REQUEST_BASE_URI = "/oauth2/authorization";
  private static final String REGISTRATION_ID = "registrationId";
  private final ClientRegistrationRepository clientRegistrationRepository;
  private final OidcAuthenticationConfigurationRepository oidcProviderRepository;
  private final Map<String, OAuth2AuthorizationRequestResolver> authorizationRequestResolvers;
  private final RequestMatcher authorizationRequestMatcher;

  public ClientAwareOAuth2AuthorizationRequestResolver(
      final ClientRegistrationRepository clientRegistrationRepository,
      final OidcAuthenticationConfigurationRepository oidcProviderRepository) {
    this.clientRegistrationRepository = clientRegistrationRepository;
    this.oidcProviderRepository = oidcProviderRepository;
    authorizationRequestResolvers = new ConcurrentHashMap<>();
    authorizationRequestMatcher =
        PathPatternRequestMatcher.withDefaults()
            .matcher("%s/{%s}".formatted(AUTHORIZATION_REQUEST_BASE_URI, REGISTRATION_ID));
  }

  @Override
  public OAuth2AuthorizationRequest resolve(final HttpServletRequest request) {
    final var clientRegistrationId = resolveRegistrationId(request);
    return resolve(clientRegistrationId, r -> r.resolve(request));
  }

  @Override
  public OAuth2AuthorizationRequest resolve(
      final HttpServletRequest request, final String clientRegistrationId) {
    return resolve(clientRegistrationId, r -> r.resolve(request, clientRegistrationId));
  }

  protected OAuth2AuthorizationRequest resolve(
      final String clientRegistrationId,
      final Function<OAuth2AuthorizationRequestResolver, OAuth2AuthorizationRequest>
          requestSupplier) {
    if (clientRegistrationId == null || clientRegistrationId.isBlank()) {
      return null;
    }

    ensureClientRegistrationExistsOrThrow(clientRegistrationId);
    return Optional.of(getOrCreateAuthorizationRequestResolver(clientRegistrationId))
        .map(requestSupplier)
        .orElse(null);
  }

  protected void ensureClientRegistrationExistsOrThrow(final String clientRegistrationId) {
    final var clientRegistration =
        clientRegistrationRepository.findByRegistrationId(clientRegistrationId);
    if (clientRegistration == null) {
      throw new IllegalArgumentException(
          ERROR_INVALID_CLIENT_REGISTRATION_ID.formatted(clientRegistrationId));
    }
  }

  protected OAuth2AuthorizationRequestResolver getOrCreateAuthorizationRequestResolver(
      final String clientRegistrationId) {
    return authorizationRequestResolvers.computeIfAbsent(
        clientRegistrationId, this::createAuthorizationRequestResolver);
  }

  protected OAuth2AuthorizationRequestResolver createAuthorizationRequestResolver(
      final String clientRegistrationId) {
    final var resolver =
        new DefaultOAuth2AuthorizationRequestResolver(
            clientRegistrationRepository, AUTHORIZATION_REQUEST_BASE_URI);
    final var oidcProvider =
        oidcProviderRepository.getOidcAuthenticationConfigurationById(clientRegistrationId);
    final var customizer = createAuthorizationRequestCustomizer(oidcProvider);
    resolver.setAuthorizationRequestCustomizer(customizer);
    return resolver;
  }

  protected Consumer<Builder> createAuthorizationRequestCustomizer(
      final OidcAuthenticationConfiguration oidcProvider) {
    return customizer -> {
      final var additionalParameters = oidcProvider.getAuthorizeRequest().getAdditionalParameters();
      if (additionalParameters != null && !additionalParameters.isEmpty()) {
        customizer.additionalParameters(additionalParameters);
      }

      final var resource = oidcProvider.getResource();
      if (resource != null && !resource.isEmpty()) {
        customizer.additionalParameters(Map.of(OAuth2ParameterNames.RESOURCE, resource));
      }
    };
  }

  protected String resolveRegistrationId(final HttpServletRequest request) {
    if (!authorizationRequestMatcher.matches(request)) {
      return null;
    }
    return authorizationRequestMatcher.matcher(request).getVariables().get(REGISTRATION_ID);
  }
}
