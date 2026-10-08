/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.env;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EnvVarSecretStoreTest {

  private static final String PREFIX = "APP_SECRET_";

  @Test
  void shouldResolvePrefixedVariable() {
    // given
    final var store = exact(Map.of("APP_SECRET_token", "token-value"));

    // when
    final var results = store.resolve(Set.of("token"));

    // then
    assertThat(results).containsExactly(Map.entry("token", new Resolved("token-value")));
  }

  @Test
  void shouldNotResolveVariableWithoutThePrefix() {
    // given a variable a process must never reach, named exactly like the secret it asks for
    final var store = exact(Map.of("DB_PASSWORD", "platform-credential"));

    // when
    final var results = store.resolve(Set.of("DB_PASSWORD"));

    // then
    assertThat(results.get("DB_PASSWORD"))
        .isInstanceOfSatisfying(
            Failed.class, failed -> assertThat(failed.code()).isEqualTo(SecretErrorCode.NOT_FOUND));
  }

  @Test
  void shouldResolveEveryRequestedNameIndividually() {
    // given
    final var store = exact(Map.of("APP_SECRET_a", "a-value"));

    // when
    final var results = store.resolve(Set.of("a", "missing", " "));

    // then one missing or invalid name does not fail the others
    assertThat(results).hasSize(3).containsEntry("a", new Resolved("a-value"));
    assertThat(((Failed) results.get("missing")).code()).isEqualTo(SecretErrorCode.NOT_FOUND);
    assertThat(((Failed) results.get(" ")).code()).isEqualTo(SecretErrorCode.INVALID_REF);
  }

  @Test
  void shouldResolveEmptyValue() {
    // given
    final var store = exact(Map.of("APP_SECRET_empty", ""));

    // when / then
    assertThat(store.resolve(Set.of("empty"))).containsEntry("empty", new Resolved(""));
  }

  @Test
  void shouldNotMatchSeparatorOrCaseVariantsWhenExact() {
    // given
    final var store = exact(Map.of("APP_SECRET_DB_PASSWORD", "value"));

    // when
    final var results = store.resolve(Set.of("db.password", "db-password", "DB_PASSWORD"));

    // then
    assertThat(results.get("DB_PASSWORD")).isEqualTo(new Resolved("value"));
    assertThat(results.get("db.password")).isInstanceOf(Failed.class);
    assertThat(results.get("db-password")).isInstanceOf(Failed.class);
  }

  @Test
  void shouldMatchSeparatorAndCaseVariantsWhenConnectorsCompatible() {
    // given a variable in the shape Connectors users already have
    final var store =
        new EnvVarSecretStore(
            Map.of("APP_SECRET_DB_PASSWORD", "value"), PREFIX, NameMatching.CONNECTORS_COMPATIBLE);

    // when
    final var results = store.resolve(Set.of("db.password", "db-password", "DB_PASSWORD"));

    // then every spelling Spring's environment lookup accepts reaches it
    assertThat(results.values()).containsOnly(new Resolved("value"));
  }

  @Test
  void shouldPreferTheExactNameWhenConnectorsCompatible() {
    // given two variables both candidates for the same name
    final var store =
        new EnvVarSecretStore(
            Map.of("APP_SECRET_db_password", "exact", "APP_SECRET_DB_PASSWORD", "upper"),
            PREFIX,
            NameMatching.CONNECTORS_COMPATIBLE);

    // when / then Spring's order wins: as-is before upper-cased
    assertThat(store.resolve(Set.of("db_password")))
        .containsEntry("db_password", new Resolved("exact"));
  }

  @Test
  void shouldTryCandidatesInSpringOrder() {
    assertThat(EnvVarSecretStore.connectorsCandidates("P_a.b-c"))
        .containsExactly(
            "P_a.b-c", "P_a_b-c", "P_a.b_c", "P_a_b_c", "P_A.B-C", "P_A_B-C", "P_A.B_C", "P_A_B_C");
  }

  @Test
  void shouldListNamesWithThePrefixRemoved() {
    // given
    final var store =
        exact(
            Map.of("APP_SECRET_a", "1", "APP_SECRET_b", "2", "APP_SECRET_", "3", "HOME", "/root"));

    // when / then
    assertThat(store.list()).containsExactlyInAnyOrder("a", "b");
  }

  @Test
  void shouldSnapshotTheEnvironmentAtConstruction() {
    // given
    final var environment = new java.util.HashMap<>(Map.of("APP_SECRET_a", "before"));
    final var store = exact(environment);

    // when
    environment.put("APP_SECRET_a", "after");

    // then
    assertThat(store.resolve(Set.of("a"))).containsEntry("a", new Resolved("before"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  "})
  void shouldRejectBlankPrefix(final String prefix) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new EnvVarSecretStore(Map.of(), prefix, NameMatching.EXACT))
        .withMessageContaining("non-blank prefix");
  }

  @ParameterizedTest
  @ValueSource(strings = {"APP-SECRET", "APP.SECRET", "APP SECRET"})
  void shouldRejectPrefixWithInvalidCharacters(final String prefix) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new EnvVarSecretStore(Map.of(), prefix, NameMatching.EXACT))
        .withMessageContaining("letters, digits and underscores");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "CAMUNDA_",
        "camunda_secrets_",
        "CAMUNDA",
        "ZEEBE_BROKER_",
        "SPRING",
        "S",
        "AWS_",
        "A"
      })
  void shouldRejectPrefixOverlappingAReservedPrefix(final String prefix) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new EnvVarSecretStore(Map.of(), prefix, NameMatching.EXACT))
        .withMessageContaining("overlaps the reserved prefix");
  }

  @Test
  void shouldDetectOverlappingPrefixesCaseInsensitively() {
    assertThat(EnvVarSecretStore.overlap("TENANT_A_", "tenant_a_db_")).isTrue();
    assertThat(EnvVarSecretStore.overlap("TENANT_A_", "TENANT_B_")).isFalse();
  }

  private static EnvVarSecretStore exact(final Map<String, String> environment) {
    return new EnvVarSecretStore(environment, PREFIX, NameMatching.EXACT);
  }
}
