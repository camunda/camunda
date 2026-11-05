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
import java.util.Collections;
import java.util.HashMap;
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
  private static final String ERROR_CONFLICTING_ADDITIONAL_KEYS =
      "OIDC providers '%2$s' and '%3$s' both resolve to issuer '%1$s' but configure different"
          + " additional-jwk-set-uris. Tokens of that issuer are verified with one set of"
          + " additional keys only. Give both providers the same additional-jwk-set-uris, or set"
          + " them on one provider only.";

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
      final Map<String, List<String>> additionalJwkSetUrisByRegistrationId) {
    this.clientRegistrations = List.copyOf(clientRegistrations);
    this.jwsKeySelectorFactory = jwsKeySelectorFactory;
    additionalJwkSetUrisByIssuer =
        additionalKeysByIssuer(clientRegistrations, additionalJwkSetUrisByRegistrationId);
    selectors = new ConcurrentHashMap<>();
  }

  /**
   * Rejects registrations that share a resolved issuer but configure different, non-empty
   * additional JWK Set URIs. Two providers configured as {@code https://idp/} and {@code
   * https://idp} both resolve to the provider's issuer (see {@link ClientRegistrationFactory}), so
   * this is checked on the resolved issuer, whatever the configuration order. A registration
   * without additional keys does not conflict: it uses the keys of its issuer, as before this
   * check.
   *
   * @throws IllegalStateException naming the issuer and the registrations that conflict
   */
  public static void requireConsistentAdditionalKeys(
      final List<ClientRegistration> clientRegistrations,
      final Map<String, List<String>> additionalJwkSetUrisByRegistrationId) {
    additionalKeysByIssuer(clientRegistrations, additionalJwkSetUrisByRegistrationId);
  }

  /**
   * The additional JWK Set URIs of each resolved issuer. A token is routed by issuer to the first
   * registration that has it, so all registrations of an issuer share one set. The issuer must
   * match exactly, so a provider whose issuer only looks alike never gets these keys.
   */
  private static Map<String, List<String>> additionalKeysByIssuer(
      final List<ClientRegistration> clientRegistrations,
      final Map<String, List<String>> additionalJwkSetUrisByRegistrationId) {
    final Map<String, List<String>> keysByIssuer = new HashMap<>();
    final Map<String, ClientRegistration> ownerByIssuer = new HashMap<>();
    for (final var registration : clientRegistrations) {
      final var issuer = registration.getProviderDetails().getIssuerUri();
      final var keys = additionalKeysOf(registration, additionalJwkSetUrisByRegistrationId);
      if (issuer == null || issuer.isBlank() || keys.isEmpty()) {
        continue;
      }
      final var owner = ownerByIssuer.putIfAbsent(issuer, registration);
      if (owner != null && !keysByIssuer.get(issuer).equals(keys)) {
        throw new IllegalStateException(
            ERROR_CONFLICTING_ADDITIONAL_KEYS.formatted(
                issuer, owner.getRegistrationId(), registration.getRegistrationId()));
      }
      keysByIssuer.put(issuer, keys);
    }
    return Map.copyOf(keysByIssuer);
  }

  private static List<String> additionalKeysOf(
      final ClientRegistration registration, final Map<String, List<String>> byRegistrationId) {
    final var uris =
        byRegistrationId != null ? byRegistrationId.get(registration.getRegistrationId()) : null;
    return uris != null ? uris : List.of();
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
    // By the exact issuer of the registration the token was routed to, so a token never reaches
    // the additional keys of a provider whose issuer merely looks alike.
    final var additionalUris = additionalJwkSetUrisByIssuer.get(providerDetails.getIssuerUri());
    return jwsKeySelectorFactory.createJWSKeySelector(jwkSetUri, additionalUris);
  }
}
