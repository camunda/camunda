/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.oidc;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Factory for creating {@link JWSKeySelector} instances based on a configured set of allowed {@link
 * JWSAlgorithm} values and a JWK Set URI.
 */
public class JWSKeySelectorFactory {

  private static final String ERROR_MISSING_JWK_SET_URI = "Missing or empty 'jwkSetUri'";
  private static final String ERROR_INVALID_JWK_SET_URI =
      "Invalid 'jwkSetUri' provided: '%s'. It could not be converted to a valid URL. Cause: %s";

  private static final Set<JWSAlgorithm> DEFAULT_JWS_ALGORITHMS =
      Set.of(
          JWSAlgorithm.RS256,
          JWSAlgorithm.RS384,
          JWSAlgorithm.RS512,
          JWSAlgorithm.ES256,
          JWSAlgorithm.ES384,
          JWSAlgorithm.ES512);

  private final Set<JWSAlgorithm> jwsAlgorithms;

  public JWSKeySelectorFactory() {
    this(DEFAULT_JWS_ALGORITHMS);
  }

  public JWSKeySelectorFactory(final Set<JWSAlgorithm> jwsAlgorithms) {
    this.jwsAlgorithms = Set.copyOf(jwsAlgorithms);
  }

  public JWSKeySelector<SecurityContext> createJWSKeySelector(final String jwkSetUri) {
    if (!StringUtils.hasText(jwkSetUri)) {
      throw new IllegalArgumentException(ERROR_MISSING_JWK_SET_URI);
    }

    final var url = toURL(jwkSetUri);
    final var jwkSource = createJWKSource(url);
    final var jwsAlgorithms = getJWSAlgorithms();
    return new JWSVerificationKeySelector<>(jwsAlgorithms, jwkSource);
  }

  public JWSKeySelector<SecurityContext> createJWSKeySelector(
      final String jwkSetUri, final List<String> additionalJwkSetUris) {
    if (CollectionUtils.isEmpty(additionalJwkSetUris)) {
      return createJWSKeySelector(jwkSetUri);
    }

    if (!StringUtils.hasText(jwkSetUri)) {
      throw new IllegalArgumentException(ERROR_MISSING_JWK_SET_URI);
    }

    final var sources =
        Stream.concat(
                Stream.of(jwkSetUri), additionalJwkSetUris.stream().filter(StringUtils::hasText))
            .map(uri -> createJWKSource(toURL(uri)))
            .toList();

    final var compositeSource = new CompositeJWKSource<>(sources);
    return new JWSVerificationKeySelector<>(getJWSAlgorithms(), compositeSource);
  }

  protected URL toURL(final String jwkSetUri) {
    try {
      return URI.create(jwkSetUri).toURL();
    } catch (final MalformedURLException ex) {
      throw new IllegalArgumentException(
          ERROR_INVALID_JWK_SET_URI.formatted(jwkSetUri, ex.getMessage()), ex);
    }
  }

  protected JWKSource<SecurityContext> createJWKSource(final URL jwkSetUri) {
    return JWKSourceBuilder.create(jwkSetUri)
        .refreshAheadCache(false)
        .rateLimited(false)
        .cache(true)
        .build();
  }

  public Set<JWSAlgorithm> getJWSAlgorithms() {
    return jwsAlgorithms;
  }
}
