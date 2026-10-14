/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import static io.camunda.secretstore.SecretErrorCode.ACCESS_DENIED;
import static io.camunda.secretstore.SecretErrorCode.INVALID_REF;
import static io.camunda.secretstore.SecretErrorCode.NOT_FOUND;

import com.azure.core.exception.HttpResponseException;
import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretStoreUnavailableException;
import java.util.regex.Pattern;

/**
 * Azure Key Vault error classification and messaging shared by every {@link AzureSecretResolver}.
 */
final class AzureKeyVaultErrors {

  /**
   * Azure Key Vault secret names are limited to 1-127 characters of {@code [0-9a-zA-Z-]}. A
   * reference that cannot form a valid name can never exist in the vault, so it is rejected locally
   * as {@link SecretErrorCode#INVALID_REF} rather than issuing a request that would come back as an
   * opaque 400.
   */
  static final Pattern VALID_SECRET_NAME = Pattern.compile("^[0-9A-Za-z-]{1,127}$");

  private AzureKeyVaultErrors() {}

  /**
   * Maps a per-secret Azure Key Vault exception to a {@link SecretErrorCode} based on the HTTP
   * status it carries.
   *
   * @throws SecretStoreUnavailableException if {@code e} indicates a store-wide failure rather than
   *     a per-secret one (e.g. throttling {@code 429}, a {@code 5xx} service error, or a credential
   *     that could not obtain a token and so carries no response at all)
   */
  static SecretErrorCode classify(final HttpResponseException e) {
    if (e.getResponse() == null) {
      throw storeUnavailable(e);
    }
    return switch (e.getResponse().getStatusCode()) {
      case 404 -> NOT_FOUND;
      case 400 -> INVALID_REF;
      case 401, 403 -> ACCESS_DENIED;
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
        "Azure Key Vault is unavailable: " + e.getMessage(), e);
  }
}
