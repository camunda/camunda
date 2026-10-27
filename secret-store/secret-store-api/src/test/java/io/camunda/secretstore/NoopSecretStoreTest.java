/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class NoopSecretStoreTest {

  private final NoopSecretStore store = new NoopSecretStore();

  @Test
  void shouldReturnNotFoundForEveryRef() {
    // when
    final var result = store.resolve(Set.of("my-secret"));

    // then — every name gets a Failed(NOT_FOUND) result
    assertThat(result).containsOnlyKeys("my-secret");
    assertThat(result.values())
        .allSatisfy(
            r -> {
              assertThat(r).isInstanceOf(SecretResolutionResult.Failed.class);
              final var failed = (SecretResolutionResult.Failed) r;
              assertThat(failed.code()).isEqualTo(SecretErrorCode.NOT_FOUND);
              assertThat(failed.message()).isNotBlank();
              assertThat(failed.cause()).isNull();
            });
  }

  @Test
  void shouldReturnEmptyMapForEmptyRefSet() {
    // given an empty input set
    // when
    final var result = store.resolve(Set.of());

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnEmptyList() {
    // given
    // when
    final var list = store.list();

    // then
    assertThat(list).isEmpty();
  }
}
