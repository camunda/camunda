/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.env;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretStore;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EnvVarSecretStoreTest {

  @Test
  void shouldResolveFromSystemEnvironmentSnapshot() {
    // when
    try (final SecretStore store = EnvVarSecretStore.fromSystemEnvironment("SECRET_")) {

      // then
      assertThat(store.resolve(Set.of("PATH")))
          .hasEntrySatisfying(
              "PATH",
              entry ->
                  assertThat(entry)
                      .isInstanceOfSatisfying(
                          SecretResolutionResult.Failed.class,
                          failed ->
                              assertThat(failed.code()).isEqualTo(SecretErrorCode.NOT_FOUND)));
    }
  }

  @Test
  void shouldReturnNoResultsForNoNames() {
    // given
    final var store = new EnvVarSecretStore(Map.of("SECRET_DB_PASSWORD", "s3cr3t"), "SECRET_");

    // when
    final var result = store.resolve(Set.of());

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReportEveryNameAsNotFound() {
    // given
    final var store = new EnvVarSecretStore(Map.of("SECRET_DB_PASSWORD", "s3cr3t"), "SECRET_");

    // when
    final var result = store.resolve(Set.of("DB_PASSWORD", "API_KEY"));

    // then
    assertThat(result)
        .containsOnlyKeys("DB_PASSWORD", "API_KEY")
        .allSatisfy(
            (name, entry) ->
                assertThat(entry)
                    .isInstanceOfSatisfying(
                        SecretResolutionResult.Failed.class,
                        failed -> {
                          assertThat(failed.code()).isEqualTo(SecretErrorCode.NOT_FOUND);
                          assertThat(failed.message()).contains(name).doesNotContain("s3cr3t");
                        }));
  }

  @Test
  void shouldListNoNames() {
    // given
    final var store = new EnvVarSecretStore(Map.of("SECRET_DB_PASSWORD", "s3cr3t"), "SECRET_");

    // when
    final var names = store.list();

    // then
    assertThat(names).isEmpty();
  }
}
