/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.aws;

import static io.camunda.secretstore.SecretErrorCode.ACCESS_DENIED;
import static io.camunda.secretstore.SecretErrorCode.INVALID_REF;
import static io.camunda.secretstore.SecretErrorCode.NOT_FOUND;

import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretStoreUnavailableException;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

/** AWS error classification and messaging shared by every {@link AwsSecretResolver}. */
final class AwsSecretsManagerErrors {

  private AwsSecretsManagerErrors() {}

  /**
   * Maps a per-secret AWS Secrets Manager exception (the shape thrown by single-item calls such as
   * {@code GetSecretValue}) to a {@link SecretErrorCode}, via the same AWS error-code string that
   * {@link #classifyErrorCode} uses for batch per-item errors — the code string is present on both
   * shapes ({@link SecretsManagerException#awsErrorDetails()} vs. {@code
   * APIErrorType#errorCode()}), so one classification table serves both instead of a separate
   * exception-type-based mapping.
   *
   * @throws SecretStoreUnavailableException if {@code e} indicates a store-wide failure rather than
   *     a per-secret one (e.g. throttling, service error)
   */
  static SecretErrorCode classify(final SecretsManagerException e) {
    final var code = classifyErrorCode(errorCodeOf(e));
    if (code != null) {
      return code;
    }
    // fallback for a denial that doesn't carry a recognizable error-code string
    if (isAccessDenied(e)) {
      return ACCESS_DENIED;
    }
    throw storeUnavailable(e);
  }

  private static @Nullable String errorCodeOf(final SecretsManagerException e) {
    final var details = e.awsErrorDetails();
    return details != null ? details.errorCode() : null;
  }

  /**
   * Classifies a raw per-item AWS error code from a {@code BatchGetSecretValue} error entry (which
   * carries only a code string, not an exception to throw).
   *
   * @return the classified code, or {@code null} if it isn't one of the known per-secret codes,
   *     meaning it's a store-wide problem the caller must handle separately
   */
  static @Nullable SecretErrorCode classifyErrorCode(final @Nullable String errorCode) {
    if (errorCode == null) {
      return null;
    }
    // defensive: tolerate incidental leading/trailing whitespace AWS shouldn't send but hasn't
    // contractually promised never to
    final var normalized = errorCode.strip();
    return switch (normalized) {
      case "ResourceNotFoundException" -> NOT_FOUND;
      case "InvalidParameterException", "InvalidRequestException" -> INVALID_REF;
      case "DecryptionFailure", "DecryptionFailureException", "AccessDeniedException" ->
          ACCESS_DENIED;
      default -> normalized.contains("AccessDenied") ? ACCESS_DENIED : null;
    };
  }

  static boolean isAccessDenied(final SecretsManagerException e) {
    final var details = e.awsErrorDetails();
    if (details != null && details.errorCode() != null) {
      return details.errorCode().contains("AccessDenied");
    }
    return e.statusCode() == 403;
  }

  /**
   * A human-readable per-secret failure message, shared by every resolver so the wording is
   * consistent regardless of resolution mode.
   *
   * @param what what {@code id} identifies, e.g. {@code "secret"} or {@code "container secret"}
   */
  static String messageFor(final SecretErrorCode code, final String what, final String id) {
    return switch (code) {
      case NOT_FOUND -> capitalize(what) + " not found: " + id;
      case INVALID_REF -> "Invalid " + what + " reference: " + id;
      case ACCESS_DENIED -> "Access denied for " + what + ": " + id;
      case UNREADABLE -> capitalize(what) + " is unreadable: " + id;
    };
  }

  private static String capitalize(final String s) {
    return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  static SecretStoreUnavailableException storeUnavailable(final Exception e) {
    return new SecretStoreUnavailableException(
        "AWS Secrets Manager is unavailable: " + e.getMessage(), e);
  }
}
