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
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretStore;
import java.util.List;
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

  @Test
  void shouldRejectMissingPrefix() {
    // given an unset prefix, which binds as empty, and a whitespace-only one
    final var missingPrefixes = List.of("", "   ");

    // when / then
    assertThat(missingPrefixes)
        .allSatisfy(
            prefix ->
                assertThatIllegalArgumentException()
                    .isThrownBy(() -> new EnvVarSecretStore(Map.of(), prefix))
                    .withMessageContaining("prefix is missing"));
  }

  @Test
  void shouldRejectEveryReservedPrefix() {
    // when / then
    assertThat(EnvVarSecretStore.RESERVED_PREFIXES)
        .allSatisfy(
            reserved ->
                assertThatIllegalArgumentException()
                    .isThrownBy(() -> new EnvVarSecretStore(Map.of(), reserved))
                    .withMessageContaining("reserved prefix '" + reserved + "'"));
  }

  @Test
  void shouldRejectPrefixOverlappingAReservedPrefix() {
    // given prefixes that differ from CAMUNDA_ only in case, extend it, or are cut short of it, and
    // so would each read CAMUNDA_* variables
    final var overlappingPrefixes = List.of("camunda_", "Camunda_", "CAMUNDA_APP_", "CAMUNDA", "C");

    // when / then
    assertThat(overlappingPrefixes)
        .allSatisfy(
            prefix ->
                assertThatIllegalArgumentException()
                    .isThrownBy(() -> new EnvVarSecretStore(Map.of(), prefix))
                    .withMessageContaining("'" + prefix + "'")
                    .withMessageContaining("reserved prefix 'CAMUNDA_'"));
  }

  @Test
  void shouldAcceptPrefixOutsideTheReservedOnes() {
    // given prefixes that share leading letters with a reserved prefix but neither starts the other
    final var validPrefixes = List.of("SECRET_", "APP_SECRET_", "ERP_", "ZB_", "CAM_");

    // when / then
    assertThat(validPrefixes)
        .allSatisfy(
            prefix ->
                assertThatNoException().isThrownBy(() -> new EnvVarSecretStore(Map.of(), prefix)));
  }

  @Test
  void shouldRejectInvalidPrefixWhenCreatedFromSystemEnvironment() {
    // when / then
    assertThatIllegalArgumentException()
        .isThrownBy(() -> EnvVarSecretStore.fromSystemEnvironment("ZEEBE_"))
        .withMessageContaining("reserved prefix 'ZEEBE_'");
  }
}
