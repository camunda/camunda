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
import org.springframework.security.oauth2.client.endpoint.DefaultClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequestEntityConverter;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Custom OAuth2AccessTokenResponseClient for Client Credentials flow that supports
 * certificate-based client assertion for Microsoft Entra ID authentication.
 */
public class CertificateBasedClientCredentialsTokenResponseClient
    implements OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> {

  private static final String CLIENT_ASSERTION_TYPE =
      "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

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
    System.out.println(
        "[DEBUG] CertificateBasedClientCredentialsTokenResponseClient.getTokenResponse() called");
    final OAuth2AccessTokenResponse response =
        delegate.getTokenResponse(clientCredentialsGrantRequest);
    System.out.println(
        "[DEBUG] Received OAuth2AccessTokenResponse from MS endpoint: "
            + response.getAccessToken().getTokenValue());
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
            clientCredentialsGrantRequest
                .getClientRegistration()
                .getProviderDetails()
                .getTokenUri();
        final String clientAssertion =
            clientAssertionService.createClientAssertion(oidcConfiguration, tokenUri);

        System.out.println("[DEBUG] Token URI: " + tokenUri);
        System.out.println("[DEBUG] Client ID: " + oidcConfiguration.getClientId());
        System.out.println("[DEBUG] Grant type: " + oidcConfiguration.getGrantType());
        System.out.println("[DEBUG] Generated client assertion JWT: " + clientAssertion + "...");

        formParameters.put(
            "client_assertion_type", Collections.singletonList(CLIENT_ASSERTION_TYPE));
        formParameters.put("client_assertion", Collections.singletonList(clientAssertion));

        System.out.println(
            "[DEBUG] Executing POST request against MS endpoint: " + entity.getUrl());
        System.out.println("[DEBUG] client_assertion_type request param: " + CLIENT_ASSERTION_TYPE);
        System.out.println("[DEBUG] client_assertion request param: " + clientAssertion);
        // Create new request entity with modified parameters
        return RequestEntity.post(entity.getUrl())
            .headers(entity.getHeaders())
            .body(formParameters);
      }

      return entity;
    }
  }
}
