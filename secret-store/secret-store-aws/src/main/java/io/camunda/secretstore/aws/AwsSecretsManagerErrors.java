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
import software.amazon.awssdk.services.secretsmanager.model.DecryptionFailureException;
import software.amazon.awssdk.services.secretsmanager.model.InvalidParameterException;
import software.amazon.awssdk.services.secretsmanager.model.InvalidRequestException;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

/** AWS error classification shared by every {@link AwsSecretResolver} implementation. */
final class AwsSecretsManagerErrors {

  private AwsSecretsManagerErrors() {}

  /**
   * Maps a per-secret AWS Secrets Manager exception to a {@link SecretErrorCode}.
   *
   * @throws SecretStoreUnavailableException if {@code e} indicates a store-wide failure rather than
   *     a per-secret one (e.g. throttling, service error)
   */
  static SecretErrorCode classify(final SecretsManagerException e) {
    if (e instanceof ResourceNotFoundException) {
      return NOT_FOUND;
    }
    if (e instanceof InvalidParameterException || e instanceof InvalidRequestException) {
      return INVALID_REF;
    }
    if (e instanceof DecryptionFailureException || isAccessDenied(e)) {
      return ACCESS_DENIED;
    }
    throw storeUnavailable(e);
  }

  private static boolean isAccessDenied(final SecretsManagerException e) {
    final var details = e.awsErrorDetails();
    if (details != null && details.errorCode() != null) {
      return details.errorCode().contains("AccessDenied");
    }
    return e.statusCode() == 403;
  }

  static SecretStoreUnavailableException storeUnavailable(final Exception e) {
    return new SecretStoreUnavailableException(
        "AWS Secrets Manager is unavailable: " + e.getMessage(), e);
  }
}
