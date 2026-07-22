/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.secret;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.processing.job.SecretResolver.SecretReference;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class CachedSecretResolverTest {

  private final CachedSecretResolver resolver = new CachedSecretResolver();

  @Test
  void shouldResolveCachedSecretsInOneCall() {
    // given
    final var token = new SecretReference("store", "token");
    final var apiKey = new SecretReference("store", "apiKey");
    resolver.put(token, "resolved-token");
    resolver.put(apiKey, "resolved-key");

    // when
    final var values = resolver.resolve(Set.of(token, apiKey));

    // then
    assertThat(values).containsEntry(token, "resolved-token").containsEntry(apiKey, "resolved-key");
  }

  @Test
  void shouldOmitReferenceMissingFromCache() {
    // given - only one of the requested references is cached
    final var token = new SecretReference("store", "token");
    final var missing = new SecretReference("store", "missing");
    resolver.put(token, "resolved-token");

    // when
    final var values = resolver.resolve(Set.of(token, missing));

    // then - only the cached reference is returned
    assertThat(values).containsOnly(Map.entry(token, "resolved-token"));
  }

  @Test
  void shouldDistinguishSecretsByStore() {
    // given - the same reference name cached under a different store
    final var sameStore = new SecretReference("store-a", "token");
    final var otherStore = new SecretReference("store-b", "token");
    resolver.put(sameStore, "value-a");

    // when
    final var values = resolver.resolve(Set.of(sameStore, otherStore));

    // then
    assertThat(values).containsOnly(Map.entry(sameStore, "value-a"));
  }

  @Test
  void shouldNotCollideOnStoreAndReferenceBoundary() {
    // given - two pairs whose naive concatenation would be identical ("ab" + "c" vs "a" + "bc")
    final var first = new SecretReference("ab", "c");
    final var second = new SecretReference("a", "bc");
    resolver.put(first, "first");

    // when
    final var values = resolver.resolve(Set.of(first, second));

    // then - references are compared field by field, so the pairs stay distinct
    assertThat(values).containsOnly(Map.entry(first, "first"));
  }
}
