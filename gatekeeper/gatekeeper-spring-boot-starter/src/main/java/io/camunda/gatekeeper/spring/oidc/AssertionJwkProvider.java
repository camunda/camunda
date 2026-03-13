/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.oidc;

import com.nimbusds.jose.jwk.JWK;

/**
 * Creates JWK keys for private_key_jwt client authentication.
 *
 * <p>The full assertion/keystore configuration support requires additional fields on {@link
 * io.camunda.gatekeeper.config.OidcConfig} (keystore path, alias, password, kid generation
 * settings). This class provides the interface that will be completed when those domain types are
 * available.
 */
public final class AssertionJwkProvider {

  private final OidcAuthenticationConfigurationRepository oidcConfigRepository;

  public AssertionJwkProvider(
      final OidcAuthenticationConfigurationRepository oidcConfigRepository) {
    this.oidcConfigRepository = oidcConfigRepository;
  }

  /**
   * Creates a {@link JWK} for the given client registration ID using the assertion configuration
   * from the OIDC provider settings.
   *
   * @param clientRegistrationId the client registration ID
   * @return a JWK for use in private_key_jwt client authentication
   * @throws UnsupportedOperationException until keystore configuration is added to OidcConfig
   */
  public JWK createJwk(final String clientRegistrationId) {
    final var oidcConfig = oidcConfigRepository.getOidcConfigById(clientRegistrationId);
    if (oidcConfig == null) {
      throw new IllegalArgumentException(
          "No OIDC configuration found for registration ID: " + clientRegistrationId);
    }
    // Keystore-based assertion configuration is not yet represented in OidcConfig.
    // This will be completed when assertion/keystore fields are added to the domain model.
    throw new UnsupportedOperationException(
        "private_key_jwt assertion is not yet supported in gatekeeper. "
            + "Keystore configuration must be added to OidcConfig.");
  }
}
