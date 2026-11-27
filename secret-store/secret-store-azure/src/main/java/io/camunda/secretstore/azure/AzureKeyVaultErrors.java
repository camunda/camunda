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
import com.azure.identity.CredentialUnavailableException;
import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretStoreUnavailableException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.nio.channels.ClosedByInterruptException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.Nullable;

/**
 * Azure Key Vault error classification and messaging shared by every {@link AzureSecretResolver}.
 *
 * <p>The mapping is tabled in {@link AzureKeyVaultSecretStore}.
 *
 * <p>A message carries Key Vault's own status, error code and message when the vault answered, so a
 * deleted, expired and missing secret can be told apart, but never a value: Key Vault's error
 * bodies hold none. Without an answer, it carries the message of a network or sign-in error only;
 * of any other exception, such as a response that could not be parsed, only its type, as its
 * message may quote what it failed to parse.
 */
final class AzureKeyVaultErrors {

  /**
   * The inner error code Key Vault answers a read of a disabled secret with, alongside HTTP 403.
   * Not in Microsoft's reference docs; observed on real Azure Key Vault.
   */
  private static final String SECRET_DISABLED = "SecretDisabled";

  /**
   * The inner error codes of a 403 refusing the caller's network rather than its identity: a
   * firewall rule, or a private endpoint connection. Every secret of the store fails alike.
   */
  private static final Set<String> NETWORK_REFUSALS =
      Set.of("ForbiddenByFirewall", "ForbiddenByConnection");

  private static final Set<String> NETWORK_PACKAGES =
      Set.of("java.net.", "java.nio.channels.", "javax.net.ssl.", "io.netty.", "reactor.netty.");

  private AzureKeyVaultErrors() {}

  /**
   * Maps a failed read of {@code secretName} to a {@link SecretErrorCode}.
   *
   * @throws SecretStoreUnavailableException if {@code e} is a store-wide or transient failure
   */
  static SecretErrorCode classify(final String secretName, final RuntimeException e) {
    // a failed sign-in may carry Entra ID's 400 or no response at all: it is never per secret
    if (e instanceof final HttpResponseException http
        && !(e instanceof ClientAuthenticationException)
        && http.getResponse() != null) {
      return switch (http.getResponse().getStatusCode()) {
        case 404 -> NOT_FOUND;
        case 403 -> {
          if (isRefusedByNetworkRules(http)) {
            throw storeUnavailable(secretName, e);
          }
          yield ACCESS_DENIED;
        }
        case 400 -> INVALID_REF;
        default -> throw storeUnavailable(secretName, e);
      };
    }
    throw storeUnavailable(secretName, e);
  }

  static boolean isSecretDisabled(final RuntimeException e) {
    return e instanceof final HttpResponseException http
        && http.getResponse() != null
        && http.getResponse().getStatusCode() == 403
        && hasInnerErrorCode(http, Set.of(SECRET_DISABLED));
  }

  /** Whether {@code http} is a 403 refusing the caller's network, see {@link #NETWORK_REFUSALS}. */
  static boolean isRefusedByNetworkRules(final HttpResponseException http) {
    return http.getResponse() != null
        && http.getResponse().getStatusCode() == 403
        && hasInnerErrorCode(http, NETWORK_REFUSALS);
  }

  /** A per-secret failure message for {@code secretName}, ending with Key Vault's own answer. */
  static String messageFor(final SecretErrorCode code, final String secretName, final Exception e) {
    final var message =
        switch (code) {
          case NOT_FOUND -> "Secret not found: " + secretName;
          case INVALID_REF -> "Invalid secret reference: " + secretName;
          case ACCESS_DENIED -> "Access denied for secret: " + secretName;
          // never returned by classify; kept so the switch covers every code
          case UNREADABLE -> "Secret is unreadable: " + secretName;
        };
    return message + " (" + describe(e) + ")";
  }

  static SecretStoreUnavailableException storeUnavailable(
      final String secretName, final Exception e) {
    return new SecretStoreUnavailableException(
        "Azure Key Vault is unavailable to read secret " + secretName + ": " + describe(e),
        causeToKeep(e));
  }

  /**
   * A failed listing of the store's secrets, naming the store and Key Vault's answer.
   *
   * @param hint appended to the message, e.g. the permission to grant, or empty
   */
  static SecretStoreUnavailableException listingUnavailable(
      final String storeDescription, final Exception e, final String hint) {
    return new SecretStoreUnavailableException(
        storeDescription + " is unavailable to list secrets: " + describe(e) + hint,
        causeToKeep(e));
  }

  /**
   * What a failure may keep as its cause: only a network or sign-in error without an answer from
   * Azure, and only when nothing it holds, as a cause or a suppressed exception, has one. The SDK's
   * exception for an answer holds the request and the answer, which can carry the bearer token, the
   * client secret of a token request, or a secret value; any other exception, such as one for a
   * response that could not be parsed, may quote it in its message. {@link #describe} already puts
   * what the operator needs in the failure's message.
   */
  static @Nullable Exception causeToKeep(final Exception e) {
    final Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    return isSafeToKeep(e, seen) ? e : null;
  }

  private static boolean isSafeToKeep(final Throwable t, final Set<Throwable> seen) {
    if (!seen.add(t)) {
      return true;
    }
    if (hasAnswer(t) || !(hasSafeMessage(t) || isPlainWrapper(t))) {
      return false;
    }
    if (t.getCause() != null && !isSafeToKeep(t.getCause(), seen)) {
      return false;
    }
    for (final var suppressed : t.getSuppressed()) {
      if (!isSafeToKeep(suppressed, seen)) {
        return false;
      }
    }
    return true;
  }

  /**
   * What went wrong, as Key Vault said it: {@code Key Vault answered HTTP 403 Forbidden: <its
   * message>} for an answer, on one line, or the network or sign-in error for a failure without
   * one. A sign-in error is described by its {@link SignInMonitor#reason(Throwable) reason}, as the
   * SDK may copy a parse error quoting the sign-in response, and its token, into its message.
   */
  static String describe(final Exception e) {
    // the vault's own 401 reaches here as ClientAuthenticationException, with Key Vault's body
    if (e instanceof final HttpResponseException http
        && http.getResponse() != null
        && (!(e instanceof ClientAuthenticationException) || error(http) != null)) {
      final var error = error(http);
      final var code = error == null ? null : error.get("code");
      final var message = error == null ? null : error.get("message");
      return "Key Vault answered HTTP "
          + http.getResponse().getStatusCode()
          + (code instanceof final String c ? " " + c : "")
          + ": "
          + (message instanceof final String m
              ? SignInMonitor.oneLine(m)
              : firstLine(http.getMessage()));
    }
    final Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Throwable t = e; t != null && seen.add(t); t = t.getCause()) {
      if (hasSafeMessage(t)) {
        final var described =
            t.getClass().getSimpleName()
                + ": "
                + (isSignInFailure(t) ? SignInMonitor.reason(t) : firstLine(t.getMessage()));
        return t == e ? described : e.getClass().getSimpleName() + " caused by " + described;
      }
    }
    return e.getClass().getSimpleName();
  }

  private static boolean isSignInFailure(final Throwable t) {
    return t instanceof ClientAuthenticationException
        || t instanceof CredentialUnavailableException;
  }

  private static boolean hasAnswer(final Throwable t) {
    return t instanceof final HttpResponseException http && http.getResponse() != null;
  }

  /**
   * Whether the message of {@code t} is written by the JDK's networking, the HTTP client or Azure
   * about a network or sign-in error, and so cannot quote a response body. A plain {@code
   * IOException} is not: a JSON parser may throw one, quoting what it failed to parse.
   */
  private static boolean hasSafeMessage(final Throwable t) {
    if (repeatsItsCause(t)) {
      return false;
    }
    if (t instanceof TimeoutException) {
      return true;
    }
    if (isTransportFailure(t)) {
      final var type = t.getClass().getName();
      return NETWORK_PACKAGES.stream().anyMatch(type::startsWith);
    }
    return t instanceof ClientAuthenticationException
        || t instanceof CredentialUnavailableException
        || (t instanceof final HttpResponseException http && http.getResponse() == null);
  }

  // like new RuntimeException(cause): its message adds nothing to its cause's
  private static boolean isPlainWrapper(final Throwable t) {
    return t.getMessage() == null || repeatsItsCause(t);
  }

  private static boolean repeatsItsCause(final Throwable t) {
    return t.getCause() != null && t.getCause().toString().equals(t.getMessage());
  }

  /**
   * Checks whether any {@code innererror} object nested under {@code error} in Key Vault's OData
   * error body carries one of {@code expectedCodes}. Key Vault's {@code Error} model nests {@code
   * innererror} recursively from less specific to more specific codes.
   */
  private static boolean hasInnerErrorCode(
      final HttpResponseException e, final Set<String> expectedCodes) {
    var current = error(e);
    if (current == null) {
      return false;
    }
    final Set<Map<?, ?>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    while (seen.add(current) && current.get("innererror") instanceof final Map<?, ?> inner) {
      if (inner.get("code") instanceof final String code
          && expectedCodes.stream().anyMatch(code.strip()::equalsIgnoreCase)) {
        return true;
      }
      current = inner;
    }
    return false;
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
  static String firstLine(final @Nullable String message) {
    if (message == null) {
      return "";
    }
    final int end = message.indexOf('\n');
    return end < 0 ? message : message.substring(0, end);
  }

  /**
   * Whether {@code failure} is a network error or timeout. The SDK asks the retry strategy for
   * every cause of a failed request, so a cause decides on its own: a rejected sign-in is not
   * retried, but a sign-in that could not reach Entra ID is.
   */
  static boolean isTransportFailure(final @Nullable Throwable failure) {
    if ((failure instanceof InterruptedIOException && !(failure instanceof SocketTimeoutException))
        || failure instanceof ClosedByInterruptException) {
      return false;
    }
    return failure instanceof IOException || failure instanceof TimeoutException;
  }
}
