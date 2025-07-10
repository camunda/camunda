/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.authentication.oauth.ClientAssertionConstants;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import java.util.Collections;
import org.springframework.http.RequestEntity;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/** Utility class for handling OAuth2 client assertion parameters in token requests. */
public final class ClientAssertionUtils {

  private ClientAssertionUtils() {}

  /**
   * Adds client assertion parameters to the OAuth2 token request, replacing client_secret with
   * certificate-based client assertion when certificate authentication is enabled.
   *
   * @param originalEntity the original request entity
   * @param tokenUri the token endpoint URI
   * @param clientAssertionService service to create client assertions
   * @param oidcConfiguration OIDC configuration
   * @return modified request entity with client assertion parameters
   */
  public static RequestEntity<?> addClientAssertionParameters(
      final RequestEntity<?> originalEntity,
      final String tokenUri,
      final CertificateClientAssertionService clientAssertionService,
      final OidcAuthenticationConfiguration oidcConfiguration) {

    if (!oidcConfiguration.isClientAssertionEnabled()) {
      return originalEntity;
    }

    final MultiValueMap<String, String> formParameters = new LinkedMultiValueMap<>();

    // Copy original parameters
    if (originalEntity.getBody() instanceof MultiValueMap) {
      @SuppressWarnings("unchecked")
      final MultiValueMap<String, String> originalParams =
          (MultiValueMap<String, String>) originalEntity.getBody();
      formParameters.putAll(originalParams);
    }

    // Remove client_secret if present
    formParameters.remove(OAuth2ParameterNames.CLIENT_SECRET);

    // Add client assertion parameters
    final String clientAssertion =
        clientAssertionService.createClientAssertion(oidcConfiguration, tokenUri);

    formParameters.put(
        ClientAssertionConstants.CLIENT_ASSERTION_TYPE_PARAM,
        Collections.singletonList(ClientAssertionConstants.CLIENT_ASSERTION_TYPE_JWT_BEARER));
    formParameters.put(
        ClientAssertionConstants.CLIENT_ASSERTION_PARAM,
        Collections.singletonList(clientAssertion));

    // Create new request entity with modified parameters
    return RequestEntity.post(originalEntity.getUrl())
        .headers(originalEntity.getHeaders())
        .body(formParameters);
  }
}
