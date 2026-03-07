/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.oidc;

import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.JWTClaimsSetAwareJWSKeySelector;
import java.security.Key;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

/**
 * A {@link JWSKeySelector} implementation that dynamically selects the appropriate key selector
 * based on the {@code iss} (issuer) claim in a JWT.
 */
public class IssuerAwareJWSKeySelector implements JWTClaimsSetAwareJWSKeySelector<SecurityContext> {

  private static final String ERROR_UNKNOWN_ISSUER =
      "Unknown issuer '%s'. No matching client registration found.";
  private static final String ERROR_MISSING_ISSUER =
      "Missing or empty 'iss' (issuer) claim in JWT.";

  private final List<ClientRegistration> clientRegistrations;
  private final JWSKeySelectorFactory jwsKeySelectorFactory;
  private final Map<String, List<String>> additionalJwkSetUrisByIssuer;
  private final Map<String, JWSKeySelector<SecurityContext>> selectors;

  public IssuerAwareJWSKeySelector(
      final List<ClientRegistration> clientRegistrations,
      final JWSKeySelectorFactory jwsKeySelectorFactory) {
    this(clientRegistrations, jwsKeySelectorFactory, Collections.emptyMap());
  }

  public IssuerAwareJWSKeySelector(
      final List<ClientRegistration> clientRegistrations,
      final JWSKeySelectorFactory jwsKeySelectorFactory,
      final Map<String, List<String>> additionalJwkSetUrisByIssuer) {
    this.clientRegistrations = List.copyOf(clientRegistrations);
    this.jwsKeySelectorFactory = jwsKeySelectorFactory;
    this.additionalJwkSetUrisByIssuer =
        additionalJwkSetUrisByIssuer != null
            ? Map.copyOf(additionalJwkSetUrisByIssuer)
            : Collections.emptyMap();
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

  private ClientRegistration getClientRegistrationByIssuer(final String issuer) {
    return clientRegistrations.stream()
        .filter(c -> issuer.equals(c.getProviderDetails().getIssuerUri()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException(ERROR_UNKNOWN_ISSUER.formatted(issuer)));
  }

  private JWSKeySelector<SecurityContext> createJWSKeySelector(final String issuer) {
    final var clientRegistration = getClientRegistrationByIssuer(issuer);
    final var providerDetails = clientRegistration.getProviderDetails();
    final var jwkSetUri = providerDetails.getJwkSetUri();
    final var additionalUris = additionalJwkSetUrisByIssuer.get(issuer);
    return jwsKeySelectorFactory.createJWSKeySelector(jwkSetUri, additionalUris);
  }
}
