/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.env;

import static io.camunda.secretstore.SecretErrorCode.INVALID_REF;
import static io.camunda.secretstore.SecretErrorCode.NOT_FOUND;
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
  void shouldNotResolveAnUnprefixedVariableFromTheSystemEnvironment() {
    // given a store over the real environment, where PATH is set but carries no prefix
    try (final SecretStore store =
        EnvVarSecretStore.fromConfig(new EnvVarSecretStoreConfig("SECRET_", System.getenv()))) {

      // when
      final var result = store.resolve(Set.of("PATH"));

      // then
      assertFailed(result, "PATH", NOT_FOUND);
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
  void shouldResolveANameFromItsPrefixedVariable() {
    // given
    final var store = new EnvVarSecretStore(Map.of("SECRET_API_KEY", "s3cr3t"), "SECRET_");

    // when
    final var result = store.resolve(Set.of("API_KEY"));

    // then
    assertResolved(result, "API_KEY", "s3cr3t");
  }

  @Test
  void shouldReportAnEmptyValueAsNotFound() {
    // given
    final var store = new EnvVarSecretStore(Map.of("SECRET_API_KEY", ""), "SECRET_");

    // when
    final var result = store.resolve(Set.of("API_KEY"));

    // then
    assertFailed(result, "API_KEY", NOT_FOUND);
  }

  @Test
  void shouldMatchNamesExactly() {
    // given only a lower-case variant of the name
    final var store = new EnvVarSecretStore(Map.of("SECRET_api_key", "s3cr3t"), "SECRET_");

    // when
    final var result = store.resolve(Set.of("API_KEY"));

    // then
    assertFailed(result, "API_KEY", NOT_FOUND);
  }

  @Test
  void shouldNotResolveAVariableWhosePrefixDiffersInCase() {
    // given the variable carries the prefix in lower case only
    final var store = new EnvVarSecretStore(Map.of("secret_API_KEY", "s3cr3t"), "SECRET_");

    // when
    final var result = store.resolve(Set.of("API_KEY"));

    // then
    assertFailed(result, "API_KEY", NOT_FOUND);
  }

  @Test
  void shouldReturnMultiLineAndJsonValuesUnchanged() {
    // given
    final var pem = "-----BEGIN KEY-----\nMIIBVgIBADAN\nBgkqhkiG9w0B\n-----END KEY-----";
    final var json = "{\"user\": \"admin\", \"password\": \"p@ss \\\"quoted\\\"\"}";
    final var store =
        new EnvVarSecretStore(Map.of("SECRET_TLS_KEY", pem, "SECRET_CREDENTIALS", json), "SECRET_");

    // when
    final var result = store.resolve(Set.of("TLS_KEY", "CREDENTIALS"));

    // then
    assertResolved(result, "TLS_KEY", pem);
    assertResolved(result, "CREDENTIALS", json);
  }

  @Test
  void shouldKeepATrailingNewline() {
    // given
    final var store = new EnvVarSecretStore(Map.of("SECRET_TOKEN", "s3cr3t\n"), "SECRET_");

    // when
    final var result = store.resolve(Set.of("TOKEN"));

    // then
    assertResolved(result, "TOKEN", "s3cr3t\n");
  }

  @Test
  void shouldReturnANonAsciiValueUnchanged() {
    // given
    final var store = new EnvVarSecretStore(Map.of("SECRET_PASSWORD", "pässwörd-密码"), "SECRET_");

    // when
    final var result = store.resolve(Set.of("PASSWORD"));

    // then
    assertResolved(result, "PASSWORD", "pässwörd-密码");
  }

  @Test
  void shouldNotResolveAVariableWithoutThePrefix() {
    // given
    final var store = new EnvVarSecretStore(Map.of("API_KEY", "s3cr3t"), "SECRET_");

    // when
    final var result = store.resolve(Set.of("API_KEY"));

    // then
    assertFailed(result, "API_KEY", NOT_FOUND);
  }

  @Test
  void shouldResolveADashedName() {
    // given a name Docker --env-file and Kubernetes envFrom pass through unchanged
    final var store = new EnvVarSecretStore(Map.of("SECRET_db-password", "s3cr3t"), "SECRET_");

    // when
    final var result = store.resolve(Set.of("db-password"));

    // then
    assertResolved(result, "db-password", "s3cr3t");
  }

  @Test
  void shouldRefuseANameOutsideTheReferenceCharset() {
    // given variables whose names a backtick-escaped reference in a job can carry, but the secrets
    // API cannot
    final var store =
        new EnvVarSecretStore(
            Map.of("SECRET_has.dot", "s3cr3t", "SECRET_tokén", "s3cr3t", "SECRET_", "s3cr3t"),
            "SECRET_");

    // when
    final var result = store.resolve(Set.of("has.dot", "tokén", ""));

    // then
    assertFailed(result, "has.dot", INVALID_REF);
    assertFailed(result, "tokén", INVALID_REF);
    assertFailed(result, "", INVALID_REF);
  }

  @Test
  void shouldResolveEachNameOfAMixedRequestOnItsOwn() {
    // given
    final var store =
        new EnvVarSecretStore(
            Map.of("SECRET_API_KEY", "s3cr3t", "SECRET_has.dot", "s3cr3t"), "SECRET_");

    // when
    final var result = store.resolve(Set.of("API_KEY", "MISSING", "has.dot"));

    // then
    assertThat(result).containsOnlyKeys("API_KEY", "MISSING", "has.dot");
    assertResolved(result, "API_KEY", "s3cr3t");
    assertFailed(result, "MISSING", NOT_FOUND);
    assertFailed(result, "has.dot", INVALID_REF);
  }

  @Test
  void shouldResolveFromStoreAsResolveDoes() {
    // given
    final var store = new EnvVarSecretStore(Map.of("SECRET_API_KEY", "s3cr3t"), "SECRET_");

    // when
    final var result = store.resolveFromStore(Set.of("API_KEY", "MISSING"));

    // then
    assertResolved(result, "API_KEY", "s3cr3t");
    assertFailed(result, "MISSING", NOT_FOUND);
  }

  @Test
  void shouldLookUpAValueLocally() {
    // given
    final var store = new EnvVarSecretStore(Map.of("SECRET_API_KEY", "s3cr3t"), "SECRET_");

    // when
    final var value = store.lookupLocal("API_KEY");

    // then
    assertThat(value).contains("s3cr3t");
  }

  @Test
  void shouldLookUpNothingLocallyForAMissingOrRefusedName() {
    // given a refused name that is nevertheless held, which the local lookup must not hand out
    final var store =
        new EnvVarSecretStore(Map.of("SECRET_has.dot", "s3cr3t", "SECRET_EMPTY", ""), "SECRET_");

    // when / then empty rather than an exception, so the job is parked and the background
    // resolution raises the incident
    assertThat(store.lookupLocal("MISSING")).isEmpty();
    assertThat(store.lookupLocal("EMPTY")).isEmpty();
    assertThat(store.lookupLocal("has.dot")).isEmpty();
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
  void shouldRejectMalformedPrefix() {
    // given prefixes with whitespace, without a trailing underscore (including ones cut short of a
    // reserved prefix), not starting with a letter, or with characters no variable name carries
    final var malformedPrefixes =
        List.of(
            " SECRET_", "SECRET_ ", "SECRET", "CAMUNDA", "C", "_SECRET_", "1SECRET_", "SE-CRET_");

    // when / then
    assertThat(malformedPrefixes)
        .allSatisfy(
            prefix ->
                assertThatIllegalArgumentException()
                    .isThrownBy(() -> new EnvVarSecretStore(Map.of(), prefix))
                    .withMessageContaining("'" + prefix + "' is malformed"));
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
  void shouldRejectPrefixStartingWithAReservedPrefix() {
    // given prefixes that differ from CAMUNDA_ only in case or extend it, and so would each read
    // CAMUNDA_* variables
    final var overlappingPrefixes = List.of("camunda_", "Camunda_", "CAMUNDA_APP_");

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
    // given prefixes that share leading letters with a reserved prefix but do not start with one,
    // in either case
    final var validPrefixes = List.of("SECRET_", "APP_SECRET_", "ERP_", "ZB_", "CAM_", "a_", "X1_");

    // when / then
    assertThat(validPrefixes)
        .allSatisfy(
            prefix ->
                assertThatNoException().isThrownBy(() -> new EnvVarSecretStore(Map.of(), prefix)));
  }

  @Test
  void shouldRejectInvalidPrefixWhenCreatedFromConfig() {
    // when / then
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                EnvVarSecretStore.fromConfig(
                    new EnvVarSecretStoreConfig("ZEEBE_", System.getenv())))
        .withMessageContaining("reserved prefix 'ZEEBE_'");
  }

  private static void assertResolved(
      final Map<String, SecretResolutionResult> result, final String name, final String value) {
    assertThat(result)
        .hasEntrySatisfying(
            name,
            entry ->
                assertThat(entry)
                    .isInstanceOfSatisfying(
                        SecretResolutionResult.Resolved.class,
                        resolved -> assertThat(resolved.value()).isEqualTo(value)));
  }

  private static void assertFailed(
      final Map<String, SecretResolutionResult> result,
      final String name,
      final SecretErrorCode code) {
    assertThat(result)
        .hasEntrySatisfying(
            name,
            entry ->
                assertThat(entry)
                    .isInstanceOfSatisfying(
                        SecretResolutionResult.Failed.class,
                        failed -> {
                          assertThat(failed.code()).isEqualTo(code);
                          assertThat(failed.message()).contains(name).doesNotContain("s3cr3t");
                          assertThat(failed.cause()).isNull();
                        }));
  }
}
