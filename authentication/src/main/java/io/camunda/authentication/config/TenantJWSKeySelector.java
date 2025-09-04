/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.JWTClaimsSetAwareJWSKeySelector;
import java.net.MalformedURLException;
import java.net.URI;
import java.security.Key;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.StreamSupport;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistration.ProviderDetails;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

public class TenantJWSKeySelector implements JWTClaimsSetAwareJWSKeySelector<SecurityContext> {

  private final ClientRegistrationRepository clientRegistrationRepository;
  private final Map<String, JWSKeySelector<SecurityContext>> selectors;

  public TenantJWSKeySelector(final ClientRegistrationRepository clientRegistrationRepository) {
    this.clientRegistrationRepository = clientRegistrationRepository;
    selectors = new ConcurrentHashMap<>();
  }

  @Override
  public List<? extends Key> selectKeys(
      final JWSHeader jwsHeader,
      final JWTClaimsSet jwtClaimsSet,
      final SecurityContext securityContext)
      throws KeySourceException {
    final var issuer = jwtClaimsSet.getIssuer();
    return selectors
        .computeIfAbsent(issuer, this::createJWSKeySelector)
        .selectJWSKeys(jwsHeader, securityContext);
  }

  private ClientRegistration getClientRegistrationByIssuer(final String issuer) {
    final var repository = (Iterable<ClientRegistration>) clientRegistrationRepository;
    return StreamSupport.stream(repository.spliterator(), false)
        .filter(c -> issuer.equals(c.getProviderDetails().getIssuerUri()))
        .findFirst()
        .orElse(null);
  }

  private JWSKeySelector<SecurityContext> createJWSKeySelector(final String issuer) {
    return Optional.ofNullable(getClientRegistrationByIssuer(issuer))
        .map(ClientRegistration::getProviderDetails)
        .map(ProviderDetails::getJwkSetUri)
        .map(this::create)
        .orElseThrow(() -> new IllegalArgumentException("unknown tenant"));
  }

  private JWSKeySelector<SecurityContext> create(final String jwkSetUri) {
    try {
      final var url = URI.create(jwkSetUri).toURL();
      final var jwkSource = JWKSourceBuilder.create(url).build();
      final var jwsAlgorithms =
          Set.of(
              // JWS Algorithm Family: RSA
              JWSAlgorithm.RS256,
              JWSAlgorithm.RS384,
              JWSAlgorithm.RS512,
              // JWS Algorithm Family: EC
              JWSAlgorithm.ES256,
              JWSAlgorithm.ES384,
              JWSAlgorithm.ES512);
      return new JWSVerificationKeySelector<>(jwsAlgorithms, jwkSource);
    } catch (final MalformedURLException ex) {
      throw new IllegalArgumentException(ex);
    }
  }
}
