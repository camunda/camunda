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
 * <p>No credential is accepted here by design: the store signs in with managed or workload
 * identity, or with a service principal supplied through the standard {@code AZURE_*} environment
 * variables.
 *
 * @param vaultUrl the URL of the vault holding the secrets, for example {@code
 *     https://my-vault.vault.azure.net}
 * @param pathPrefix prepended to every reference name to form the Key Vault secret name, for
 *     example {@code camunda-}; {@code null} or blank means references map to bare secret names
 * @param mode how reference names map to Key Vault secrets; {@link Mode#ONE_BY_ONE} by default
 * @param containerSecret the name of the JSON secret holding every reference as a key, read in
 *     {@link Mode#CONTAINER} mode only; the path prefix is prepended to it
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
 */
public record AzureKeyVaultStoreConfig(
    String vaultUrl,
    @Nullable String pathPrefix,
    Mode mode,
    @Nullable String containerSecret,
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

  /**
   * Creates a config for the given vault with every other setting at its default: one secret per
   * reference, no prefix, the identity the environment provides, the public cloud, and the default
   * retries and timeouts.
   */
  public static AzureKeyVaultStoreConfig of(final String vaultUrl) {
    return new AzureKeyVaultStoreConfig(
        vaultUrl,
        null,
        Mode.ONE_BY_ONE,
        null,
        null,
        null,
        DEFAULT_MAX_RETRIES,
        DEFAULT_CALL_TIMEOUT,
        DEFAULT_ATTEMPT_TIMEOUT);
  }

  /** How reference names map to Key Vault secrets. */
  public enum Mode {
    /** Every reference is its own Key Vault secret, named {@code pathPrefix + name}. */
    ONE_BY_ONE,
    /**
     * Every reference is a key of the JSON Key Vault secret {@code pathPrefix + containerSecret}.
     */
    CONTAINER
  }
}
