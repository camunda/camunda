/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Configuration for an {@link AzureKeyVaultSecretStore}.
 *
 * <p>No credential is accepted here by design: the store signs in with managed or workload
 * identity, or with a service principal supplied through the standard {@code AZURE_*} environment
 * variables.
 *
 * @param vaultUrl the URL of the vault holding the secrets, for example {@code
 *     https://my-vault.vault.azure.net}
 * @param pathPrefix prepended to every reference name to form the Key Vault secret name, for
 *     example {@code camunda-}; {@code null} or empty means references map to bare secret names
 * @param containerSecretId opt-in: when set, every reference is a key of this one JSON Key Vault
 *     secret, named {@code pathPrefix + containerSecretId}, instead of its own secret; {@code null}
 *     keeps the default one-secret-per-reference behavior
 * @param managedIdentityClientId the client id of the user-assigned managed identity to sign in
 *     with; {@code null} uses the system-assigned identity or whichever identity the environment
 *     provides
 * @param authorityHost the Microsoft Entra authority host, for sovereign clouds (for example {@code
 *     https://login.microsoftonline.us}); {@code null} uses the public cloud
 * @param maxRetries how many times a throttled or transient request to the vault is retried
 * @param callTimeout upper bound on one {@code resolve} or {@code list} call to the vault, retries
 *     included. The background resolution calls this store from an IO-bound actor thread shared
 *     with every partition's exporter, so an unbounded call there stalls exporting broker-wide
 *     (camunda/camunda#62869)
 * @param attemptTimeout upper bound on a single HTTP attempt within a call
 * @throws IllegalArgumentException if a setting can never work, naming the setting and the rule; no
 *     call is made to Azure
 */
public record AzureKeyVaultStoreConfig(
    String vaultUrl,
    @Nullable String pathPrefix,
    @Nullable String containerSecretId,
    @Nullable String managedIdentityClientId,
    @Nullable String authorityHost,
    int maxRetries,
    Duration callTimeout,
    Duration attemptTimeout) {

  /** Default number of retries, matching the AWS store's default. */
  public static final int DEFAULT_MAX_RETRIES = 3;

  /**
   * Default upper bound on one call to the vault, retries included. Matches the AWS and GCP stores'
   * default, so that a store that has stopped answering is reported unavailable within a few
   * resolution cycles rather than after minutes: that is what lets the scheduler's retry ladder and
   * its cooldown skip engage.
   */
  public static final Duration DEFAULT_CALL_TIMEOUT = Duration.ofSeconds(5);

  /** Default upper bound on a single HTTP attempt within a call, matching the AWS store's. */
  public static final Duration DEFAULT_ATTEMPT_TIMEOUT = Duration.ofSeconds(2);

  /** Longest secret name Key Vault accepts. */
  private static final int MAX_SECRET_NAME_LENGTH = 127;

  private static final Pattern SECRET_NAME_CHARS = Pattern.compile("[0-9A-Za-z-]*");

  /**
   * Validates every setting. Mirrored, with full property names in its messages, by {@code
   * io.camunda.configuration.Secrets.AzureKeyVaultStore#validate} in the {@code configuration}
   * module, which cannot depend on this one. {@code AzureStoreValidationParityTest} in {@code dist}
   * feeds both the same settings and fails when one refuses what the other accepts. Two differences
   * are deliberate: here {@code vaultUrl} is required (the configuration layer leaves that to its
   * caller, which names the property), and values are not trimmed (the configuration layer trims
   * before building this record). Messages never repeat a value, since a value pasted into the
   * wrong setting may be a credential.
   */
  public AzureKeyVaultStoreConfig {
    if (vaultUrl == null || vaultUrl.isBlank()) {
      throw new IllegalArgumentException("vaultUrl must be set");
    }
    requireHttpsUrl("vaultUrl", vaultUrl);
    requireNotBlank("managedIdentityClientId", managedIdentityClientId);
    requireNotBlank("authorityHost", authorityHost);
    if (authorityHost != null) {
      requireHttpsUrl("authorityHost", authorityHost);
    }
    requireSecretNameChars("pathPrefix", pathPrefix);
    if (pathPrefix != null && pathPrefix.length() >= MAX_SECRET_NAME_LENGTH) {
      throw new IllegalArgumentException(
          "pathPrefix must be at most "
              + (MAX_SECRET_NAME_LENGTH - 1)
              + " characters so a secret name fits after it, but was "
              + pathPrefix.length());
    }
    if (containerSecretId != null) {
      if (containerSecretId.isEmpty()) {
        throw new IllegalArgumentException("containerSecretId must not be empty");
      }
      requireSecretNameChars("containerSecretId", containerSecretId);
      final int length =
          (pathPrefix == null ? 0 : pathPrefix.length()) + containerSecretId.length();
      if (length > MAX_SECRET_NAME_LENGTH) {
        throw new IllegalArgumentException(
            "pathPrefix + containerSecretId must be at most "
                + MAX_SECRET_NAME_LENGTH
                + " characters to form a Key Vault secret name, but was "
                + length);
      }
    }
    if (maxRetries < 0) {
      throw new IllegalArgumentException("maxRetries must not be negative, but was " + maxRetries);
    }
    if (callTimeout == null || !callTimeout.isPositive()) {
      throw new IllegalArgumentException("callTimeout must be positive, but was " + callTimeout);
    }
    if (attemptTimeout == null || !attemptTimeout.isPositive()) {
      throw new IllegalArgumentException(
          "attemptTimeout must be positive, but was " + attemptTimeout);
    }
    if (attemptTimeout.compareTo(callTimeout) > 0) {
      throw new IllegalArgumentException(
          "attemptTimeout must not be longer than callTimeout ("
              + callTimeout
              + "), but was "
              + attemptTimeout);
    }
  }

  /**
   * Creates a config for the given vault with every other setting at its default: one secret per
   * reference, no prefix, the identity the environment provides, the public cloud, and the default
   * retries and timeouts.
   */
  public static AzureKeyVaultStoreConfig of(final String vaultUrl) {
    return new AzureKeyVaultStoreConfig(
        vaultUrl,
        null,
        null,
        null,
        null,
        DEFAULT_MAX_RETRIES,
        DEFAULT_CALL_TIMEOUT,
        DEFAULT_ATTEMPT_TIMEOUT);
  }

  /**
   * Requires {@code https://host[:port][/]}. The host itself is not checked: private endpoints and
   * sovereign clouds use hosts other than {@code *.vault.azure.net}. Credentials, a path, a query
   * or a fragment never form a Key Vault or Entra ID endpoint.
   */
  private static void requireHttpsUrl(final String setting, final String url) {
    final var message =
        setting
            + " must be an absolute https URL with only a host and optional port, as Azure Key"
            + " Vault requires HTTPS";
    final URI uri;
    try {
      uri = new URI(url);
    } catch (final URISyntaxException e) {
      // the cause is dropped: its message repeats the value, which may hold credentials
      throw new IllegalArgumentException(message);
    }
    if (uri.getHost() == null && uri.getRawAuthority() != null) {
      // java.net.URI leaves the host null for names it cannot parse, e.g. with an underscore
      throw new IllegalArgumentException(
          setting + " must have a host name of only letters, digits, hyphens and dots");
    }
    final var path = uri.getRawPath();
    if (!"https".equalsIgnoreCase(uri.getScheme())
        || uri.getHost() == null
        || uri.getRawUserInfo() != null
        || !(path == null || path.isEmpty() || "/".equals(path))
        || uri.getRawQuery() != null
        || uri.getRawFragment() != null) {
      throw new IllegalArgumentException(message);
    }
  }

  private static void requireNotBlank(final String setting, final @Nullable String value) {
    if (value != null && value.isBlank()) {
      throw new IllegalArgumentException(setting + " must not be blank");
    }
  }

  private static void requireSecretNameChars(final String setting, final @Nullable String value) {
    if (value != null && !SECRET_NAME_CHARS.matcher(value).matches()) {
      throw new IllegalArgumentException(
          setting
              + " must contain only [0-9A-Za-z-] to form valid Key Vault secret names, but has"
              + " an invalid character at index "
              + firstInvalidCharIndex(value));
    }
  }

  private static int firstInvalidCharIndex(final String value) {
    for (int i = 0; i < value.length(); i++) {
      final char c = value.charAt(i);
      if (!(c == '-' || c >= '0' && c <= '9' || c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z')) {
        return i;
      }
    }
    return -1;
  }
}
