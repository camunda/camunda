/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.gcp;

import static io.camunda.secretstore.SecretErrorCode.ACCESS_DENIED;
import static io.camunda.secretstore.SecretErrorCode.INVALID_REF;
import static io.camunda.secretstore.SecretErrorCode.NOT_FOUND;

import com.google.api.gax.rpc.ApiException;
import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretStoreUnavailableException;

/** GCP error classification and messaging shared by every {@link GcpSecretResolver}. */
final class GcpSecretManagerErrors {

  private GcpSecretManagerErrors() {}

  /**
   * Maps a per-secret GCP Secret Manager exception to a {@link SecretErrorCode}.
   *
   * @throws SecretStoreUnavailableException if {@code e} indicates a store-wide failure rather than
   *     a per-secret one (e.g. throttling, unavailable, internal service error)
   */
  static SecretErrorCode classify(final ApiException e) {
    return switch (e.getStatusCode().getCode()) {
      case NOT_FOUND -> NOT_FOUND;
      case INVALID_ARGUMENT, FAILED_PRECONDITION, OUT_OF_RANGE -> INVALID_REF;
      case PERMISSION_DENIED, UNAUTHENTICATED -> ACCESS_DENIED;
      default -> throw storeUnavailable(e);
    };
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
        "GCP Secret Manager is unavailable: " + e.getMessage(), e);
  }
}
