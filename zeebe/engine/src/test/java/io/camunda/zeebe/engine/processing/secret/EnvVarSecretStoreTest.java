/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.secret;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class EnvVarSecretStoreTest {

  private static EnvVarSecretStore storeBackedBy(final Map<String, String> env) {
    return new EnvVarSecretStore(env::get);
  }

  @Test
  void shouldResolveDefinedSecret() {
    // given
    final var store = storeBackedBy(Map.of("SLACK_BOT_TOKEN", "xoxb-secret"));

    // when
    final Optional<String> resolved = store.resolve("SLACK_BOT_TOKEN");

    // then
    assertThat(resolved).contains("xoxb-secret");
  }

  @Test
  void shouldReturnEmptyForUndefinedSecret() {
    // given
    final var store = storeBackedBy(Map.of());

    // when / then
    assertThat(store.resolve("MISSING")).isEmpty();
  }

  @Test
  void shouldReturnEmptyForNullName() {
    final var store = storeBackedBy(Map.of("X", "v"));

    assertThat(store.resolve(null)).isEmpty();
  }

  @Test
  void shouldRejectNamesWithIllegalCharacters() {
    // given — the underlying map would happily return values for these names; the store guards.
    final var store =
        storeBackedBy(
            Map.of(
                "with.dot", "v1",
                "with/slash", "v2",
                "with space", "v3",
                "with$dollar", "v4",
                "", "v5"));

    // when / then — any non-identifier-shaped name resolves to empty, never the env value
    assertThat(store.resolve("with.dot")).isEmpty();
    assertThat(store.resolve("with/slash")).isEmpty();
    assertThat(store.resolve("with space")).isEmpty();
    assertThat(store.resolve("with$dollar")).isEmpty();
    assertThat(store.resolve("")).isEmpty();
  }

  @Test
  void shouldAcceptIdentifierShapedNames() {
    // given
    final var store =
        storeBackedBy(
            Map.of(
                "A", "a",
                "_LEADING_UNDERSCORE", "b",
                "snake_case_123", "c",
                "MixedCase42", "d"));

    // when / then
    assertThat(store.resolve("A")).contains("a");
    assertThat(store.resolve("_LEADING_UNDERSCORE")).contains("b");
    assertThat(store.resolve("snake_case_123")).contains("c");
    assertThat(store.resolve("MixedCase42")).contains("d");
  }

  @Test
  void shouldRejectNamesStartingWithDigit() {
    final var store = storeBackedBy(Map.of("1FOO", "v"));

    assertThat(store.resolve("1FOO")).isEmpty();
  }

  @Test
  void emptyStoreShouldResolveNothing() {
    assertThat(SecretStore.EMPTY.resolve("ANY")).isEmpty();
  }
}
