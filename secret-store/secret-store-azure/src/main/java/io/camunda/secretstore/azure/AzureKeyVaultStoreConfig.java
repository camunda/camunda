/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * Configuration for an {@link AzureKeyVaultSecretStore}.
 *
 * <p>Authentication is always identity-based: the Azure {@code DefaultAzureCredential} chain is
 * used (workload identity, managed identity, environment service principal, Azure CLI). No static
 * client secret is accepted here by design — a service principal is supplied only through the
 * standard {@code AZURE_CLIENT_ID}/{@code AZURE_TENANT_ID}/{@code AZURE_CLIENT_SECRET} environment
 * the credential chain reads.
 *
 * @param vaultUrl the Key Vault URL owning the secrets (e.g. {@code
 *     https://my-vault.vault.azure.net}). Required and non-blank.
 * @param pathPrefix optional prefix prepended to every reference name to form the Key Vault secret
 *     name (e.g. {@code camunda-}); {@code null} or blank means references map to bare secret names
 * @param containerSecretName opt-in: when set, every reference is treated as a JSON key inside this
 *     one named secret instead of its own Key Vault secret; {@code null} keeps the default
 *     one-secret per-reference behavior
 * @param managedIdentityClientId optional client id of a user-assigned managed identity to select
 *     within {@code DefaultAzureCredential}; {@code null} uses the system-assigned identity (or
 *     whichever credential the chain resolves). This is how a per-tenant identity is pinned without
 *     any static secret.
 * @param authorityHost optional Azure AD authority host override for sovereign/government clouds
 *     (e.g. {@code https://login.microsoftonline.us/}); {@code null} uses the public cloud default
 * @param maxRetries how many times the Key Vault client retries a throttled/transient request
 *     before giving up. Must be {@code >= 0}
 * @param callTimeout upper bound on one request to Key Vault. Must be positive. The Azure SDK's
 *     default response timeout lets an unresponsive endpoint hold the caller far longer than that;
 *     the background resolution calls this store from an IO-bound actor thread shared with every
 *     partition's exporter, so an unbounded call there stalls exporting broker-wide
 *     (camunda/camunda#62869)
 */
public record AzureKeyVaultStoreConfig(
    String vaultUrl,
    @Nullable String pathPrefix,
    @Nullable String containerSecretName,
    @Nullable String managedIdentityClientId,
    @Nullable String authorityHost,
    int maxRetries,
    Duration callTimeout) {

  /**
   * Default upper bound on one call to Key Vault, retries included. Matches the AWS and GCP stores'
   * default, and is chosen so that a store that has stopped answering is reported unavailable
   * within a few resolution cycles rather than after minutes: that is what lets the scheduler's
   * retry ladder and its cooldown skip engage.
   */
  public static final Duration DEFAULT_CALL_TIMEOUT = Duration.ofSeconds(5);

  /** Default retry count, mirroring the Azure SDK's own default of three retries. */
  public static final int DEFAULT_MAX_RETRIES = 3;

  public AzureKeyVaultStoreConfig {
    if (vaultUrl == null || vaultUrl.isBlank()) {
      throw new IllegalArgumentException("vaultUrl must not be blank");
    }
    if (pathPrefix != null && pathPrefix.isBlank()) {
      throw new IllegalArgumentException("pathPrefix must not be blank when set");
    }
    if (containerSecretName != null && containerSecretName.isBlank()) {
      throw new IllegalArgumentException("containerSecretName must not be blank, but was empty");
    }
    if (managedIdentityClientId != null && managedIdentityClientId.isBlank()) {
      throw new IllegalArgumentException("managedIdentityClientId must not be blank when set");
    }
    if (authorityHost != null && authorityHost.isBlank()) {
      throw new IllegalArgumentException("authorityHost must not be blank when set");
    }
    if (maxRetries < 0) {
      throw new IllegalArgumentException("maxRetries must not be negative, but was " + maxRetries);
    }
    if (callTimeout == null || !callTimeout.isPositive()) {
      throw new IllegalArgumentException("callTimeout must be positive, but was " + callTimeout);
    }
  }

  /**
   * Creates a config with the default retry count and call timeout. Every caller that does not tune
   * them goes through here, so a store is bounded whether or not an operator configured it.
   */
  public AzureKeyVaultStoreConfig(
      final String vaultUrl,
      final @Nullable String pathPrefix,
      final @Nullable String containerSecretName,
      final @Nullable String managedIdentityClientId,
      final @Nullable String authorityHost) {
    this(
        vaultUrl,
        pathPrefix,
        containerSecretName,
        managedIdentityClientId,
        authorityHost,
        DEFAULT_MAX_RETRIES,
        DEFAULT_CALL_TIMEOUT);
  }

  /**
   * Creates a config with only a vault URL and path prefix; container, identity and authority
   * default to {@code null} (one Key Vault secret per reference, system-assigned identity, public
   * cloud).
   */
  public static AzureKeyVaultStoreConfig of(
      final String vaultUrl, final @Nullable String pathPrefix) {
    return new AzureKeyVaultStoreConfig(vaultUrl, pathPrefix, null, null, null);
  }
}
