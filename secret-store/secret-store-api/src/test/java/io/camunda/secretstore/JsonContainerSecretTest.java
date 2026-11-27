/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import static io.camunda.secretstore.SecretErrorCode.INVALID_REF;
import static io.camunda.secretstore.SecretErrorCode.NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JsonContainerSecretTest {

  private static final String CONTAINER = "camunda-container";

  @Test
  void shouldResolveEachRequestedFieldWhateverItsName() {
    // given
    final var json = "{\"user\": \"u\", \"API_KEY\": \"k\", \"unused\": \"x\"}";

    // when
    final var results = JsonContainerSecret.resolve(CONTAINER, json, Set.of("user", "API_KEY"));

    // then
    assertThat(results)
        .containsOnly(entry("user", new Resolved("u")), entry("API_KEY", new Resolved("k")));
  }

  @Test
  void shouldReportAMissingOrNullFieldAsNotFound() {
    // given
    final var json = "{\"nulled\": null}";

    // when
    final var results = JsonContainerSecret.resolve(CONTAINER, json, Set.of("missing", "nulled"));

    // then
    assertThat(results.values())
        .hasSize(2)
        .allSatisfy(result -> assertThat(((Failed) result).code()).isEqualTo(NOT_FOUND));
  }

  @Test
  void shouldRefuseAFieldThatIsNotAString() {
    // given
    final var json = "{\"number\": 1, \"object\": {\"a\": \"b\"}, \"array\": [\"c\"]}";

    // when
    final var results =
        JsonContainerSecret.resolve(CONTAINER, json, Set.of("number", "object", "array"));

    // then
    assertThat(results.values())
        .hasSize(3)
        .allSatisfy(
            result -> {
              assertThat(((Failed) result).code()).isEqualTo(INVALID_REF);
              assertThat(((Failed) result).message()).doesNotContain("\"b\"", "\"c\"");
            });
  }

  @Test
  void shouldFailEveryNameWhenTheContainerIsNotAJsonObjectWithoutQuotingIt() {
    // given
    final var invalid = List.of("{\"a\": \"top-secret", "[\"top-secret\"]", "\"top-secret\"", "");

    for (final var json : invalid) {
      // when
      final var results = JsonContainerSecret.resolve(CONTAINER, json, Set.of("a", "b"));

      // then
      assertThat(results).as(json).containsOnlyKeys("a", "b");
      assertThat(results.values())
          .as(json)
          .allSatisfy(
              result -> {
                final var failure = (Failed) result;
                assertThat(failure.code()).isEqualTo(INVALID_REF);
                assertThat(failure.message()).contains(CONTAINER).doesNotContain("top-secret");
                assertThat(failure.cause()).isNull();
              });
    }
  }

  @Test
  void shouldListEveryTopLevelFieldName() {
    // when
    final var names = JsonContainerSecret.fieldNames(CONTAINER, "{\"a\": \"1\", \"b\": null}");

    // then
    assertThat(names).containsExactly("a", "b");
  }

  @Test
  void shouldNotListAContainerThatIsNotAJsonObject() {
    for (final var json : List.of("{\"a\": \"top-secret", "[\"top-secret\"]")) {
      // when / then
      assertThatThrownBy(() -> JsonContainerSecret.fieldNames(CONTAINER, json))
          .as(json)
          .isInstanceOf(SecretStoreUnavailableException.class)
          .hasMessageContaining(CONTAINER)
          .hasMessageNotContaining("top-secret");
    }
  }
}
