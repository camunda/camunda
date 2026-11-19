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

import com.azure.core.exception.ClientAuthenticationException;
import com.azure.core.exception.HttpResponseException;
import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretStoreUnavailableException;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Azure Key Vault error classification and messaging shared by every {@link AzureSecretResolver}.
 *
 * <p>The mapping is tabled in {@link AzureKeyVaultSecretStore}.
 *
 * <p>Every message carries Key Vault's own status, error code and message, so a deleted, expired
 * and missing secret can be told apart, but never a value: Key Vault's error bodies hold none.
 */
final class AzureKeyVaultErrors {

  /**
   * The inner error code Key Vault answers a read of a disabled secret with, alongside HTTP 403.
   * Not in Microsoft's reference docs; observed on real Azure Key Vault.
   */
  static final String SECRET_DISABLED = "SecretDisabled";

  private AzureKeyVaultErrors() {}

  /**
   * Maps a failed per-secret call to a {@link SecretErrorCode}.
   *
   * @throws SecretStoreUnavailableException if {@code e} is a store-wide or transient failure
   */
  static SecretErrorCode classify(final RuntimeException e) {
    // a failed sign-in may carry Entra ID's 400 or no response at all: it is never per secret
    if (e instanceof final HttpResponseException http
        && !(e instanceof ClientAuthenticationException)
        && http.getResponse() != null) {
      return switch (http.getResponse().getStatusCode()) {
        case 404 -> NOT_FOUND;
        case 403 -> ACCESS_DENIED;
        case 400 -> INVALID_REF;
        default -> throw storeUnavailable(e);
      };
    }
    throw storeUnavailable(e);
  }

  static boolean isSecretDisabled(final RuntimeException e) {
    return e instanceof final HttpResponseException http
        && http.getResponse() != null
        && http.getResponse().getStatusCode() == 403
        && SECRET_DISABLED.equals(innerErrorCode(http));
  }

  /**
   * A per-secret failure message, shared by every resolver so the wording is consistent whatever
   * the mode, ending with Key Vault's own answer.
   *
   * @param what what {@code id} identifies, e.g. {@code "secret"} or {@code "container secret"}
   */
  static String messageFor(
      final SecretErrorCode code, final String what, final String id, final Exception e) {
    final var message =
        switch (code) {
          case NOT_FOUND -> capitalize(what) + " not found: " + id;
          case INVALID_REF -> "Invalid " + what + " reference: " + id;
          case ACCESS_DENIED -> "Access denied for " + what + ": " + id;
          case UNREADABLE -> capitalize(what) + " is unreadable: " + id;
        };
    return message + " (" + describe(e) + ")";
  }

  static SecretStoreUnavailableException storeUnavailable(final Exception e) {
    return new SecretStoreUnavailableException(
        "Azure Key Vault is unavailable: " + describe(e), causeToKeep(e));
  }

  /**
   * What a failure may keep as its cause: {@code null} for an answer from Azure, as the SDK's
   * exception holds the request and the answer, which can carry the bearer token, the client secret
   * of a token request, or a secret value. {@link #describe} already puts its status and message in
   * the failure's message. A failure without an answer, such as a network error, is kept.
   */
  static @Nullable Exception causeToKeep(final Exception e) {
    return e instanceof final HttpResponseException http && http.getResponse() != null ? null : e;
  }

  /**
   * What went wrong, as Key Vault said it: {@code Key Vault answered HTTP 403 Forbidden: <its
   * message>} for an answer, or the exception for a failure without one.
   */
  static String describe(final Exception e) {
    if (e instanceof final HttpResponseException http
        && !(e instanceof ClientAuthenticationException)
        && http.getResponse() != null) {
      final var error = error(http);
      final var code = error == null ? null : error.get("code");
      final var message = error == null ? null : error.get("message");
      return "Key Vault answered HTTP "
          + http.getResponse().getStatusCode()
          + (code instanceof final String c ? " " + c : "")
          + ": "
          + (message instanceof final String m ? m : firstLine(http.getMessage()));
    }
    return e.getClass().getSimpleName() + ": " + firstLine(e.getMessage());
  }

  /**
   * The {@code error.innererror.code} of Key Vault's error body, or {@code null} if it has none.
   */
  static @Nullable String innerErrorCode(final HttpResponseException e) {
    final var error = error(e);
    if (error != null
        && error.get("innererror") instanceof final Map<?, ?> innerError
        && innerError.get("code") instanceof final String code) {
      return code;
    }
    return null;
  }

  /** The {@code error} object of Key Vault's error body, which the SDK hands back parsed. */
  private static @Nullable Map<?, ?> error(final HttpResponseException e) {
    if (e.getValue() instanceof final Map<?, ?> body
        && body.get("error") instanceof final Map<?, ?> error) {
      return error;
    }
    return null;
  }

  // the SDK appends the whole JSON error body on further lines
  private static String firstLine(final @Nullable String message) {
    if (message == null) {
      return "";
    }
    final int end = message.indexOf('\n');
    return end < 0 ? message : message.substring(0, end);
  }

  private static String capitalize(final String s) {
    return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }
}
