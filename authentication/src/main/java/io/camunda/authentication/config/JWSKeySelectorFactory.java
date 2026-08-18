/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
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
 *
 * <p>This class provides a default set of secure algorithms (RSA and EC families).
 */
public class JWSKeySelectorFactory {

  private static final String ERROR_MISSING_JWK_SET_URI = "Missing or empty 'jwkSetUri'";
  private static final String ERROR_INVALID_JWK_SET_URI =
      "Invalid 'jwkSetUri' provided: '%s'. It could not be converted to a valid URL. Cause: %s";

  private static final Set<JWSAlgorithm> DEFAULT_JWS_ALGORITHMS =
      Set.of(
          // JWS Algorithm Family: RSA
          JWSAlgorithm.RS256,
          JWSAlgorithm.RS384,
          JWSAlgorithm.RS512,
          // JWS Algorithm Family: EC
          JWSAlgorithm.ES256,
          JWSAlgorithm.ES384,
          JWSAlgorithm.ES512);

  // Nimbus's own default is 500ms for both, tuned for fast, responsive IdPs; this tolerates a
  // realistically slower external IdP while still failing fast on genuine unavailability.
  private static final int JWK_SOURCE_HTTP_CONNECT_TIMEOUT_MS = 2000;
  private static final int JWK_SOURCE_HTTP_READ_TIMEOUT_MS = 2000;

  // How long a caller blocks waiting for another thread's in-flight synchronous refresh before
  // giving up. Nimbus's own default is 15,000ms; this still leaves comfortable headroom over the
  // connect+read timeouts above while failing much faster when the IdP is genuinely down.
  private static final long JWK_SOURCE_CACHE_REFRESH_TIMEOUT_MS = 5000;

  private final Set<JWSAlgorithm> jwsAlgorithms;

  public JWSKeySelectorFactory() {
    this(DEFAULT_JWS_ALGORITHMS);
  }

  public JWSKeySelectorFactory(final Set<JWSAlgorithm> jwsAlgorithms) {
    this.jwsAlgorithms = Set.copyOf(jwsAlgorithms);
  }

  /**
   * Creates a {@link JWSKeySelector} for the given JWK Set URI.
   *
   * @param jwkSetUri the URI of the JWK Set used to verify token signatures
   * @return a {@link JWSVerificationKeySelector} configured with the allowed algorithms
   * @throws IllegalArgumentException if the URI is malformed
   */
  public JWSKeySelector<SecurityContext> createJWSKeySelector(final String jwkSetUri) {
    if (!StringUtils.hasText(jwkSetUri)) {
      throw new IllegalArgumentException(ERROR_MISSING_JWK_SET_URI);
    }

    final var url = toURL(jwkSetUri);
    final var jwkSource = createJWKSource(url);
    final var jwsAlgorithms = getJWSAlgorithms();
    return new JWSVerificationKeySelector<>(jwsAlgorithms, jwkSource);
  }

  /**
   * Creates a {@link JWSKeySelector} for the given primary JWK Set URI and optional additional
   * URIs. When additional URIs are provided, a {@link CompositeJWKSource} is used to aggregate keys
   * from all sources.
   *
   * @param jwkSetUri the primary JWK Set URI
   * @param additionalJwkSetUris additional JWK Set URIs to query for key resolution
   * @return a {@link JWSVerificationKeySelector} configured with the allowed algorithms
   * @throws IllegalArgumentException if the primary URI is malformed
   */
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

  /**
   * Converts a JWK Set URI to a {@link URL}, with validation.
   *
   * @param jwkSetUri the URI as a string
   * @return the corresponding {@link URL}
   * @throws IllegalArgumentException if the URI is not a valid URL
   */
  protected URL toURL(final String jwkSetUri) {
    try {
      return URI.create(jwkSetUri).toURL();
    } catch (final MalformedURLException ex) {
      throw new IllegalArgumentException(
          ERROR_INVALID_JWK_SET_URI.formatted(jwkSetUri, ex.getMessage()), ex);
    }
  }

  /**
   * Creates a {@link JWKSource} for the given JWK Set URL.
   *
   * @see org.springframework.security.oauth2.jwt.NimbusJwtDecoder.JwkSetUriJwtDecoderBuilder
   * @param jwkSetUri the JWK Set URI
   * @return a {@link JWKSource} for use in verifying JWT signatures
   */
  protected JWKSource<SecurityContext> createJWKSource(final URL jwkSetUri) {
    final var retriever =
        new DefaultResourceRetriever(
            JWK_SOURCE_HTTP_CONNECT_TIMEOUT_MS,
            JWK_SOURCE_HTTP_READ_TIMEOUT_MS,
            JWKSourceBuilder.DEFAULT_HTTP_SIZE_LIMIT);
    // refreshAheadCache(true): refresh happens ahead of expiry, off the request path, so
    // concurrent lookups are served the still-valid cached keys instead of blocking on a
    // synchronous refetch. outageTolerant is deliberately left at Nimbus's default (false): a
    // cache that is genuinely expired with no successful refresh still fails closed.
    return JWKSourceBuilder.create(jwkSetUri, retriever)
        .refreshAheadCache(true)
        .rateLimited(false)
        .cache(JWKSourceBuilder.DEFAULT_CACHE_TIME_TO_LIVE, JWK_SOURCE_CACHE_REFRESH_TIMEOUT_MS)
        .build();
  }

  /**
   * Returns the set of supported JWS algorithms.
   *
   * @return the configured set of allowed {@link JWSAlgorithm} values
   */
  public Set<JWSAlgorithm> getJWSAlgorithms() {
    return jwsAlgorithms;
  }
}
