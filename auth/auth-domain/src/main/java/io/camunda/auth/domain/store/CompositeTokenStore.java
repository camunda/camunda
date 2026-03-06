/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.store;

import io.camunda.auth.domain.model.TokenMetadata;
import io.camunda.auth.domain.port.outbound.TokenStorePort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Composite implementation of {@link TokenStorePort} that delegates to multiple underlying stores.
 * Stores are treated as informational audit sinks: errors during {@link #store} are logged but not
 * propagated, while reads return the first successful result from the delegate list.
 */
public class CompositeTokenStore implements TokenStorePort {

  private static final Logger LOG = LoggerFactory.getLogger(CompositeTokenStore.class);

  private final List<TokenStorePort> delegates;

  public CompositeTokenStore(final List<TokenStorePort> delegates) {
    this.delegates = List.copyOf(delegates);
  }

  @Override
  public void store(final TokenMetadata metadata) {
    for (final TokenStorePort delegate : delegates) {
      try {
        delegate.store(metadata);
      } catch (final Exception e) {
        LOG.warn(
            "Failed to store token exchange audit record in delegate={}: {}",
            delegate.getClass().getSimpleName(),
            e.getMessage(),
            e);
      }
    }
  }

  @Override
  public Optional<TokenMetadata> findByExchangeId(final String exchangeId) {
    for (final TokenStorePort delegate : delegates) {
      try {
        final Optional<TokenMetadata> result = delegate.findByExchangeId(exchangeId);
        if (result.isPresent()) {
          return result;
        }
      } catch (final Exception e) {
        LOG.warn(
            "Failed to find by exchangeId={} in delegate={}: {}",
            exchangeId,
            delegate.getClass().getSimpleName(),
            e.getMessage(),
            e);
      }
    }
    return Optional.empty();
  }

  @Override
  public List<TokenMetadata> findBySubjectPrincipalId(
      final String subjectPrincipalId, final Instant from, final Instant to) {
    for (final TokenStorePort delegate : delegates) {
      try {
        final List<TokenMetadata> results =
            delegate.findBySubjectPrincipalId(subjectPrincipalId, from, to);
        if (!results.isEmpty()) {
          return results;
        }
      } catch (final Exception e) {
        LOG.warn(
            "Failed to find by subjectPrincipalId={} in delegate={}: {}",
            subjectPrincipalId,
            delegate.getClass().getSimpleName(),
            e.getMessage(),
            e);
      }
    }
    return List.of();
  }
}
