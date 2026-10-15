/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.hashicorpvault;

import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretStoreUnavailableException;
import io.github.jopenlibs.vault.VaultException;
import java.util.regex.Pattern;

/**
 * Maps HashiCorp Vault HTTP statuses to {@link SecretErrorCode}s, or to a store-wide {@link
 * SecretStoreUnavailableException}.
 *
 * <p>The driver hands back a response for a 4xx and throws a {@link VaultException} for anything
 * else. An exception without a status ({@code 0}) is an I/O failure — connection refused, timeout,
 * TLS — so it has no response to classify and is treated as the store being unavailable.
 */
final class HashiCorpVaultErrors {

  /**
   * Names are path segments, and the driver splits paths on {@code /} and does not URL-encode them.
   * Restricting to this set refuses traversal ({@code /}, {@code ..}) as well as anything that
   * would change the request URL ({@code ?}, {@code #}, {@code %}, spaces).
   */
  private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_.-]+");

  private HashiCorpVaultErrors() {}

  static boolean isValidName(final String name) {
    return VALID_NAME.matcher(name).matches() && !name.equals(".") && !name.equals("..");
  }

  /**
   * @return the per-secret error code for a 4xx status
   * @throws SecretStoreUnavailableException for any status that is not a per-secret failure: sealed
   *     or standby (503), rate limited (429), server errors, or an unexpected status
   */
  static SecretErrorCode classify(final int status, final String what) {
    return switch (status) {
      case 404 -> SecretErrorCode.NOT_FOUND;
      case 403 -> SecretErrorCode.ACCESS_DENIED;
      case 400 -> SecretErrorCode.INVALID_REF;
      default ->
          throw new SecretStoreUnavailableException(
              "HashiCorp Vault is unavailable: HTTP " + status + " reading " + what);
    };
  }

  static SecretStoreUnavailableException storeUnavailable(final VaultException e) {
    final var status = e.getHttpStatusCode();
    // the driver's message for an HTTP error embeds the response body; Vault error bodies carry no
    // secret values, but the status is all a caller needs, so keep logs short
    final var detail = status == 0 ? e.getMessage() : "HTTP " + status;
    return new SecretStoreUnavailableException("HashiCorp Vault is unavailable: " + detail, e);
  }
}
