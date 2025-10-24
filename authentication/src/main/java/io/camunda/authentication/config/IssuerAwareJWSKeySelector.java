/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.JWTClaimsSetAwareJWSKeySelector;
import java.security.Key;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

/**
 * A {@link JWSKeySelector} implementation that dynamically selects the appropriate key selector
 * based on the {@code iss} (issuer) claim in a JWT.
 *
 * <p>This is used to support multi-tenant setups where each identity provider (issuer) may have its
 * own JWK Set URI for verifying token signatures.
 */
public class IssuerAwareJWSKeySelector implements JWTClaimsSetAwareJWSKeySelector<SecurityContext> {

  private static final String ERROR_UNKNOWN_ISSUER =
      "Unknown issuer '%s'. No matching client registration found.";
  private static final String ERROR_MISSING_ISSUER =
      "Missing or empty 'iss' (issuer) claim in JWT.";

  private final List<ClientRegistration> clientRegistrations;
  private final JWSKeySelectorFactory jwsKeySelectorFactory;
  private final Map<String, JWSKeySelector<SecurityContext>> selectors;

  public IssuerAwareJWSKeySelector(
      final List<ClientRegistration> clientRegistrations,
      final JWSKeySelectorFactory jwsKeySelectorFactory) {
    this.clientRegistrations = List.copyOf(clientRegistrations);
    this.jwsKeySelectorFactory = jwsKeySelectorFactory;
    selectors = new ConcurrentHashMap<>();
  }

  @Override
  public List<? extends Key> selectKeys(
      final JWSHeader jwsHeader,
      final JWTClaimsSet jwtClaimsSet,
      final SecurityContext securityContext)
      throws KeySourceException {
    final var issuer = jwtClaimsSet.getIssuer();

    if (issuer == null || issuer.isBlank()) {
      throw new KeySourceException(ERROR_MISSING_ISSUER);
    }

    return selectors
        .computeIfAbsent(issuer, this::createJWSKeySelector)
        .selectJWSKeys(jwsHeader, securityContext);
  }

  /**
   * Finds the {@link ClientRegistration} that matches the given issuer URI.
   *
   * @param issuer the issuer URI from the JWT claims
   * @return the matching client registration, or {@code null} if not found
   */
  private ClientRegistration getClientRegistrationByIssuer(final String issuer) {
    return clientRegistrations.stream()
        .filter(c -> issuer.equals(c.getProviderDetails().getIssuerUri()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException(ERROR_UNKNOWN_ISSUER.formatted(issuer)));
  }

  /**
   * Lazily creates a {@link JWSKeySelector} for the given issuer.
   *
   * @param issuer the issuer URI
   * @return a key selector for the issuer
   */
  private JWSKeySelector<SecurityContext> createJWSKeySelector(final String issuer) {
    final var clientRegistration = getClientRegistrationByIssuer(issuer);
    final var providerDetails = clientRegistration.getProviderDetails();
    final var jwkSetUri = providerDetails.getJwkSetUri();
    return jwsKeySelectorFactory.createJWSKeySelector(jwkSetUri);
  }
}
