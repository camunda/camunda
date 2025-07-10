/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.RequestEntity;
import org.springframework.security.oauth2.client.endpoint.DefaultClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequestEntityConverter;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;

/**
 * Custom OAuth2AccessTokenResponseClient for Client Credentials flow that supports
 * certificate-based client assertion for Microsoft Entra ID authentication.
 */
public class CertificateBasedClientCredentialsTokenResponseClient
    implements OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> {

  private final DefaultClientCredentialsTokenResponseClient delegate;
  private final CertificateClientAssertionService clientAssertionService;
  private final OidcAuthenticationConfiguration oidcConfiguration;

  public CertificateBasedClientCredentialsTokenResponseClient(
      final CertificateClientAssertionService clientAssertionService,
      final OidcAuthenticationConfiguration oidcConfiguration) {
    this.clientAssertionService = clientAssertionService;
    this.oidcConfiguration = oidcConfiguration;
    delegate = new DefaultClientCredentialsTokenResponseClient();

    // Set custom request entity converter that adds client assertion
    delegate.setRequestEntityConverter(new CertificateBasedRequestEntityConverter());
  }

  @Override
  public OAuth2AccessTokenResponse getTokenResponse(
      final OAuth2ClientCredentialsGrantRequest clientCredentialsGrantRequest) {
    final OAuth2AccessTokenResponse response =
        delegate.getTokenResponse(clientCredentialsGrantRequest);
    return response;
  }

  /**
   * Custom converter that modifies the Client Credentials token request to include client assertion
   * instead of client secret when certificate authentication is enabled.
   */
  private class CertificateBasedRequestEntityConverter
      implements Converter<OAuth2ClientCredentialsGrantRequest, RequestEntity<?>> {

    private final OAuth2ClientCredentialsGrantRequestEntityConverter defaultConverter;

    public CertificateBasedRequestEntityConverter() {
      defaultConverter = new OAuth2ClientCredentialsGrantRequestEntityConverter();
    }

    @Override
    public RequestEntity<?> convert(
        final OAuth2ClientCredentialsGrantRequest clientCredentialsGrantRequest) {
      final RequestEntity<?> entity = defaultConverter.convert(clientCredentialsGrantRequest);
      final String tokenUri =
          clientCredentialsGrantRequest.getClientRegistration().getProviderDetails().getTokenUri();

      return ClientAssertionUtils.addClientAssertionParameters(
          entity, tokenUri, clientAssertionService, oidcConfiguration);
    }
  }
}
