/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A composite {@link JWKSource} that delegates to multiple underlying JWK sources, returning keys
 * from the first source that produces a non-empty result.
 *
 * <p>Sources are tried in order. If a source throws a {@link KeySourceException}, it is logged and
 * the next source is attempted. If all sources return empty or fail, the last exception is rethrown
 * (or an empty list is returned if no exceptions occurred).
 *
 * <p>This class is thread-safe: the source list is immutable and each delegate {@link JWKSource} is
 * responsible for its own internal thread safety.
 *
 * @param <C> the security context type
 */
public class CompositeJWKSource<C extends SecurityContext> implements JWKSource<C> {

  private static final Logger LOG = LoggerFactory.getLogger(CompositeJWKSource.class);

  private final List<JWKSource<C>> sources;

  public CompositeJWKSource(final List<JWKSource<C>> sources) {
    this.sources = List.copyOf(sources);
  }

  @Override
  public List<JWK> get(final JWKSelector jwkSelector, final C context) throws KeySourceException {
    KeySourceException lastException = null;

    for (final JWKSource<C> source : sources) {
      try {
        final List<JWK> keys = source.get(jwkSelector, context);
        if (keys != null && !keys.isEmpty()) {
          return keys;
        }
      } catch (final KeySourceException e) {
        LOG.warn("JWK source failed, trying next source: {}", e.getMessage());
        lastException = e;
      }
    }

    if (lastException != null) {
      throw lastException;
    }

    return List.of();
  }
}
