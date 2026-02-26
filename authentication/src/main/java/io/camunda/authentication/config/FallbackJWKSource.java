/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link JWKSource} that queries multiple underlying sources in order, returning the first
 * non-empty result. This supports scenarios where a single issuer serves keys from multiple JWKS
 * endpoints.
 *
 * <p>Sources are tried in the order provided. If a source returns matching keys, they are returned
 * immediately without querying further sources. If a source returns no keys or throws a {@link
 * KeySourceException}, the next source is tried. If all sources are exhausted, an empty list is
 * returned.
 */
public class FallbackJWKSource implements JWKSource<SecurityContext> {

  private static final Logger LOG = LoggerFactory.getLogger(FallbackJWKSource.class);

  private final List<JWKSource<SecurityContext>> sources;

  public FallbackJWKSource(final List<JWKSource<SecurityContext>> sources) {
    if (sources == null || sources.isEmpty()) {
      throw new IllegalArgumentException("At least one JWKSource must be provided");
    }
    this.sources = List.copyOf(sources);
  }

  @Override
  public List<com.nimbusds.jose.jwk.JWK> get(
      final com.nimbusds.jose.jwk.JWKSelector jwkSelector, final SecurityContext securityContext)
      throws KeySourceException {

    KeySourceException lastException = null;

    for (final var source : sources) {
      try {
        final var keys = source.get(jwkSelector, securityContext);
        if (keys != null && !keys.isEmpty()) {
          return keys;
        }
      } catch (final KeySourceException e) {
        LOG.warn("Failed to retrieve keys from JWK source, trying next source: {}", e.getMessage());
        lastException = e;
      }
    }

    if (lastException != null) {
      LOG.debug("All JWK sources exhausted, last error was: {}", lastException.getMessage());
    }

    return List.of();
  }
}
