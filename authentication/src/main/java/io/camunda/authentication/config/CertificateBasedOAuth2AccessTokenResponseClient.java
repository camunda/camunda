/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import java.util.Collections;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.RequestEntity;
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequestEntityConverter;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Custom OAuth2AccessTokenResponseClient that supports certificate-based client assertion for
 * Microsoft Entra ID authentication following the specification:
 * https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-auth-code-flow#request-an-access-token-with-a-certificate-credential
 */
public class CertificateBasedOAuth2AccessTokenResponseClient
    implements OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> {

  private static final String CLIENT_ASSERTION_TYPE =
      "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

  private final DefaultAuthorizationCodeTokenResponseClient delegate;
  private final CertificateClientAssertionService clientAssertionService;
  private final OidcAuthenticationConfiguration oidcConfiguration;

  public CertificateBasedOAuth2AccessTokenResponseClient(
      final CertificateClientAssertionService clientAssertionService,
      final OidcAuthenticationConfiguration oidcConfiguration) {
    this.clientAssertionService = clientAssertionService;
    this.oidcConfiguration = oidcConfiguration;
    delegate = new DefaultAuthorizationCodeTokenResponseClient();

    // Set custom request entity converter that adds client assertion
    delegate.setRequestEntityConverter(new CertificateBasedRequestEntityConverter());
  }

  @Override
  public OAuth2AccessTokenResponse getTokenResponse(
      final OAuth2AuthorizationCodeGrantRequest authorizationGrantRequest) {
    // The certificate-based authentication logic is handled by the custom request entity converter
    // which modifies the token request to include client_assertion instead of client_secret
    // when oidcConfiguration.isClientAssertionEnabled() is true
    return delegate.getTokenResponse(authorizationGrantRequest);
  }

  /**
   * Custom converter that modifies the token request to include client assertion instead of client
   * secret when certificate authentication is enabled.
   */
  private class CertificateBasedRequestEntityConverter
      implements Converter<OAuth2AuthorizationCodeGrantRequest, RequestEntity<?>> {

    private final OAuth2AuthorizationCodeGrantRequestEntityConverter defaultConverter;

    public CertificateBasedRequestEntityConverter() {
      defaultConverter = new OAuth2AuthorizationCodeGrantRequestEntityConverter();
    }

    @Override
    public RequestEntity<?> convert(
        final OAuth2AuthorizationCodeGrantRequest authorizationGrantRequest) {
      final RequestEntity<?> entity = defaultConverter.convert(authorizationGrantRequest);

      if (oidcConfiguration.isClientAssertionEnabled()) {
        // Replace client_secret with client_assertion for certificate authentication
        final MultiValueMap<String, String> formParameters = new LinkedMultiValueMap<>();

        if (entity.getBody() instanceof MultiValueMap) {
          @SuppressWarnings("unchecked")
          final MultiValueMap<String, String> originalParams =
              (MultiValueMap<String, String>) entity.getBody();
          formParameters.putAll(originalParams);
        }

        // Remove client_secret if present
        formParameters.remove(OAuth2ParameterNames.CLIENT_SECRET);

        // Add client assertion parameters
        final String tokenUri =
            authorizationGrantRequest.getClientRegistration().getProviderDetails().getTokenUri();
        final String clientAssertion =
            clientAssertionService.createClientAssertion(oidcConfiguration, tokenUri);

        formParameters.put(
            "client_assertion_type", Collections.singletonList(CLIENT_ASSERTION_TYPE));
        formParameters.put("client_assertion", Collections.singletonList(clientAssertion));

        // Create new request entity with modified parameters
        return RequestEntity.post(entity.getUrl())
            .headers(entity.getHeaders())
            .body(formParameters);
      }

      return entity;
    }
  }
}
