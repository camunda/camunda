/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.aws;

import static io.camunda.secretstore.aws.AwsSecretsManagerErrors.classify;
import static io.camunda.secretstore.aws.AwsSecretsManagerErrors.classifyErrorCode;
import static io.camunda.secretstore.aws.AwsSecretsManagerErrors.isAccessDenied;
import static io.camunda.secretstore.aws.AwsSecretsManagerErrors.messageFor;
import static io.camunda.secretstore.aws.AwsSecretsManagerErrors.storeUnavailable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretStoreUnavailableException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

class AwsSecretsManagerErrorsTest {

  @Test
  void shouldReturnNullForNullErrorCode() {
    assertThat(classifyErrorCode(null)).isNull();
  }

  @Test
  void shouldClassifyKnownPerSecretErrorCodes() {
    assertThat(classifyErrorCode("ResourceNotFoundException")).isEqualTo(SecretErrorCode.NOT_FOUND);
    assertThat(classifyErrorCode("InvalidParameterException"))
        .isEqualTo(SecretErrorCode.INVALID_REF);
    assertThat(classifyErrorCode("InvalidRequestException")).isEqualTo(SecretErrorCode.INVALID_REF);
    assertThat(classifyErrorCode("DecryptionFailure")).isEqualTo(SecretErrorCode.ACCESS_DENIED);
    assertThat(classifyErrorCode("DecryptionFailureException"))
        .isEqualTo(SecretErrorCode.ACCESS_DENIED);
    assertThat(classifyErrorCode("AccessDeniedException")).isEqualTo(SecretErrorCode.ACCESS_DENIED);
  }

  @Test
  void shouldClassifyAKnownCodeDespiteIncidentalWhitespace() {
    // given a code AWS shouldn't send with surrounding whitespace, but hasn't contractually
    // promised never to
    assertThat(classifyErrorCode("  ResourceNotFoundException\n"))
        .isEqualTo(SecretErrorCode.NOT_FOUND);
  }

  @Test
  void shouldClassifyUnrecognizedAccessDeniedVariantByNameFallback() {
    // given a code that isn't the exact "AccessDeniedException" string AWS defines today, but
    // still names access-denial (e.g. a future/regional variant)
    final var code = "OrganizationAccessDeniedException";

    // when / then
    assertThat(classifyErrorCode(code)).isEqualTo(SecretErrorCode.ACCESS_DENIED);
  }

  @Test
  void shouldReturnNullRatherThanThrowForAnUnrecognizedCode() {
    // given a code that isn't one of the known per-secret codes and doesn't mention
    // access-denial: this is the batch-per-item call site's signal to mark just that one secret
    // UNREADABLE and keep resolving the rest of the batch (see FlatSecretResolver.mapBatchError)
    // — classifyErrorCode itself must never throw, since the batch call site has no exception to
    // attach as a cause and throwing here would abort every other secret in the same batch
    final var code = "SomeFutureAwsExceptionType";

    // when / then
    assertThat(classifyErrorCode(code)).isNull();
  }

  @Test
  void shouldClassifyAKnownCodeFromARealException() {
    // given
    final var exception = exceptionWithCode("ResourceNotFoundException", null);

    // when / then
    assertThat(classify(exception)).isEqualTo(SecretErrorCode.NOT_FOUND);
  }

  @Test
  void shouldFallBackToAccessDeniedForAnUnrecognizedCodeThatNamesDenial() {
    // given
    final var exception = exceptionWithCode("SomeVendorAccessDeniedVariant", null);

    // when / then
    assertThat(classify(exception)).isEqualTo(SecretErrorCode.ACCESS_DENIED);
  }

  @Test
  void shouldFallBackToAccessDeniedForA403WithNoRecognizableCode() {
    // given — no error-code string at all, but the HTTP status is a plain 403
    final var exception = exceptionWithCode(null, 403);

    // when / then
    assertThat(classify(exception)).isEqualTo(SecretErrorCode.ACCESS_DENIED);
  }

  @Test
  void shouldThrowStoreUnavailableForAnUnrecognizedNonDenialCode() {
    // given a store-wide failure shape (e.g. throttling) that classify() cannot attribute to one
    // secret — unlike classifyErrorCode(), classify() always has the real exception in hand, so it
    // throws here instead of returning null, preserving the original exception as the cause
    final var exception = exceptionWithCode("ThrottlingException", 500);

    // when / then
    assertThatThrownBy(() -> classify(exception))
        .isInstanceOf(SecretStoreUnavailableException.class)
        .hasCause(exception);
  }

  @Test
  void shouldDetectAccessDeniedByErrorCode() {
    assertThat(isAccessDenied(exceptionWithCode("AccessDeniedException", null))).isTrue();
  }

  @Test
  void shouldDetectAccessDeniedByStatusCodeWhenNoErrorCodePresent() {
    assertThat(isAccessDenied(exceptionWithCode(null, 403))).isTrue();
  }

  @Test
  void shouldNotDetectAccessDeniedForAnUnrelatedErrorWithoutA403Status() {
    assertThat(isAccessDenied(exceptionWithCode("ThrottlingException", 500))).isFalse();
  }

  @Test
  void shouldFormatMessagePerErrorCode() {
    assertThat(messageFor(SecretErrorCode.NOT_FOUND, "secret", "db-password"))
        .isEqualTo("Secret not found: db-password");
    assertThat(messageFor(SecretErrorCode.INVALID_REF, "secret", "db-password"))
        .isEqualTo("Invalid secret reference: db-password");
    assertThat(messageFor(SecretErrorCode.ACCESS_DENIED, "secret", "db-password"))
        .isEqualTo("Access denied for secret: db-password");
    assertThat(messageFor(SecretErrorCode.UNREADABLE, "secret", "db-password"))
        .isEqualTo("Secret is unreadable: db-password");
  }

  @Test
  void shouldCapitalizeAMultiWordWhatInMessages() {
    assertThat(messageFor(SecretErrorCode.NOT_FOUND, "container secret", "app-config"))
        .isEqualTo("Container secret not found: app-config");
  }

  @Test
  void shouldWrapTheOriginalExceptionAsStoreUnavailable() {
    // given
    final var exception = exceptionWithCode("ThrottlingException", 500);

    // when
    final var wrapped = storeUnavailable(exception);

    // then
    assertThat(wrapped.getCause()).isSameAs(exception);
    assertThat(wrapped.getMessage())
        .isEqualTo("AWS Secrets Manager is unavailable: " + exception.getMessage());
  }

  private static SecretsManagerException exceptionWithCode(
      final String errorCode, final Integer statusCode) {
    final var builder =
        (SecretsManagerException.Builder)
            SecretsManagerException.builder().message(errorCode == null ? "denied" : errorCode);
    if (errorCode != null) {
      builder.awsErrorDetails(AwsErrorDetails.builder().errorCode(errorCode).build());
    }
    if (statusCode != null) {
      builder.statusCode(statusCode);
    }
    return (SecretsManagerException) builder.build();
  }
}
