/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.oidc;

import com.nimbusds.jose.jwk.JWK;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;
import org.springframework.security.oauth2.client.endpoint.NimbusJwtClientAuthenticationParametersConverter;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;

/**
 * Customizer for the OAuth2 login token endpoint. Configures the token response client with
 * private_key_jwt client authentication support.
 */
public class OidcTokenEndpointCustomizer
    implements Customizer<OAuth2LoginConfigurer<HttpSecurity>.TokenEndpointConfig> {

  private static final Logger LOG = LoggerFactory.getLogger(OidcTokenEndpointCustomizer.class);
  private final OidcAuthenticationConfigurationRepository oidcConfigRepository;
  private final AssertionJwkProvider assertionJwkProvider;
  private final Map<String, JWK> resolvedJwks;

  public OidcTokenEndpointCustomizer(
      final OidcAuthenticationConfigurationRepository oidcConfigRepository,
      final AssertionJwkProvider assertionJwkProvider) {
    this.oidcConfigRepository = oidcConfigRepository;
    this.assertionJwkProvider = assertionJwkProvider;
    resolvedJwks = new ConcurrentHashMap<>();
  }

  @Override
  public void customize(final OAuth2LoginConfigurer<HttpSecurity>.TokenEndpointConfig config) {
    // Use the default RestClientAuthorizationCodeTokenResponseClient which is preconfigured with
    // OAuth2AccessTokenResponseHttpMessageConverter. Do NOT replace its internal RestClient — doing
    // so strips the required message converter and causes null access token responses.
    final RestClientAuthorizationCodeTokenResponseClient tokenResponseClient =
        new RestClientAuthorizationCodeTokenResponseClient();
    final var jwtClientAuthenticationParametersConverter =
        createJwtClientAuthenticationParametersConverter();

    tokenResponseClient.addParametersConverter(jwtClientAuthenticationParametersConverter);

    config.accessTokenResponseClient(tokenResponseClient);
  }

  private NimbusJwtClientAuthenticationParametersConverter<OAuth2AuthorizationCodeGrantRequest>
      createJwtClientAuthenticationParametersConverter() {
    final var converter =
        new NimbusJwtClientAuthenticationParametersConverter<OAuth2AuthorizationCodeGrantRequest>(
            clientRegistration -> {
              final var clientRegistrationId = clientRegistration.getRegistrationId();
              return resolveJwk(clientRegistrationId);
            });
    converter.setJwtClientAssertionCustomizer(
        ctx -> ctx.getHeaders().algorithm(SignatureAlgorithm.RS256));
    return converter;
  }

  private JWK resolveJwk(final String clientRegistrationId) {
    return resolvedJwks.computeIfAbsent(clientRegistrationId, assertionJwkProvider::createJwk);
  }
}
