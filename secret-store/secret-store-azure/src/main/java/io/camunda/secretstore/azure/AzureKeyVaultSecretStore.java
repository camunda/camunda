/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import com.azure.core.http.HttpClient;
import com.azure.core.http.policy.ExponentialBackoffOptions;
import com.azure.core.http.policy.RetryOptions;
import com.azure.core.util.HttpClientOptions;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretStore;
import io.camunda.secretstore.SecretStoreUnavailableException;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link SecretStore} backed by Azure Key Vault.
 *
 * <p>References are mapped to Key Vault secret names by prepending the configured {@code
 * pathPrefix} and read from the latest enabled version within the configured vault. Authentication
 * uses the Azure {@code DefaultAzureCredential} chain (identity-based, no static credentials passed
 * through configuration).
 *
 * <p>How a reference is actually looked up is delegated to one {@link AzureSecretResolver}, chosen
 * once at construction time: {@link OneByOneSecretResolver} by default (one Key Vault secret per
 * reference), or {@link ContainerSecretResolver} when a container secret name is set (every
 * reference is a JSON key inside one shared secret). This class itself only owns the Key Vault
 * client and picks the resolver; adding a new resolution mode means adding a new {@link
 * AzureSecretResolver} implementation, not touching this class or the existing ones.
 *
 * <p>Per-secret failures (missing secret, access denied, invalid reference) are returned as {@link
 * Failed} results. Store-wide failures (connectivity, throttling after retries, service errors) are
 * surfaced as {@link SecretStoreUnavailableException} so callers can retry or back off.
 *
 * <p>This class is thread-safe: {@link SecretClient} is thread-safe, {@link AzureSecretResolver}
 * implementations keep no mutable state between calls, and neither does this class.
 */
public final class AzureKeyVaultSecretStore implements SecretStore {

  private static final Logger LOG = LoggerFactory.getLogger(AzureKeyVaultSecretStore.class);

  private final AzureSecretResolver resolver;

  /**
   * Creates a store using an already-built client, with no JSON container and the system clock.
   * Package-private and test-only: it skips the credential/client building and startup probe done
   * by {@link #fromConfig(AzureKeyVaultStoreConfig)}, so production code must go through {@code
   * fromConfig} and only same-package tests can reach this constructor.
   */
  AzureKeyVaultSecretStore(final SecretClient client, final @Nullable String pathPrefix) {
    this(client, pathPrefix, null, InstantSource.system());
  }

  /**
   * Creates a store using an already-built client, optionally treating every reference as a JSON
   * key inside the given container secret, with an injectable clock for deterministic
   * validity-window tests. Package-private and test-only for the same reason as the other
   * constructor.
   */
  AzureKeyVaultSecretStore(
      final SecretClient client,
      final @Nullable String pathPrefix,
      final @Nullable String containerSecretName,
      final InstantSource clock) {
    this(buildResolver(client, normalize(pathPrefix), containerSecretName, clock));
  }

  private AzureKeyVaultSecretStore(final AzureSecretResolver resolver) {
    this.resolver = resolver;
  }

  /**
   * Builds a store from configuration, eagerly constructing the underlying client and probing
   * connectivity with a minimal Key Vault call. Two failure modes are distinguished:
   *
   * <ul>
   *   <li>Building the client fails (e.g. the credential chain cannot be initialized): this fails
   *       fast with a {@link SecretStoreUnavailableException}, since a client that cannot even be
   *       constructed can never resolve a secret.
   *   <li>The client builds but the startup probe fails (e.g. an unreachable vault or a missing
   *       access policy): this is best-effort — it is logged as a warning at startup but does not
   *       prevent the store from being built, so a transient Azure problem during boot cannot stop
   *       the whole application from starting. Such a misconfiguration then surfaces on the first
   *       real {@link #resolve} or {@link #list} call.
   * </ul>
   */
  public static AzureKeyVaultSecretStore fromConfig(final AzureKeyVaultStoreConfig config) {
    final SecretClient client;
    try {
      client = buildClient(config);
    } catch (final RuntimeException e) {
      throw new SecretStoreUnavailableException(
          "Failed to initialize Azure Key Vault client: " + e.getMessage(), e);
    }
    final var resolver =
        buildResolver(
            client,
            normalize(config.pathPrefix()),
            config.containerSecretName(),
            InstantSource.system());
    validateConnectivity(resolver);
    return new AzureKeyVaultSecretStore(resolver);
  }

  private static SecretClient buildClient(final AzureKeyVaultStoreConfig config) {
    final var credentialBuilder = new DefaultAzureCredentialBuilder();
    if (config.managedIdentityClientId() != null) {
      // Pins a specific user-assigned managed identity so a per-tenant store authenticates as its
      // own identity rather than whichever one the chain would otherwise resolve.
      credentialBuilder.managedIdentityClientId(config.managedIdentityClientId());
    }
    if (config.authorityHost() != null) {
      credentialBuilder.authorityHost(config.authorityHost());
    }
    // Bound the per-request wait: the Azure SDK's default response timeout lets an unresponsive
    // endpoint hold the IO-bound actor thread shared with every partition's exporter far longer
    // than callTimeout, stalling exporting broker-wide (camunda/camunda#62869).
    final var httpClient =
        HttpClient.createDefault(new HttpClientOptions().setResponseTimeout(config.callTimeout()));
    final var retryOptions =
        new RetryOptions(new ExponentialBackoffOptions().setMaxRetries(config.maxRetries()));
    return new SecretClientBuilder()
        .vaultUrl(config.vaultUrl())
        .credential(credentialBuilder.build())
        .httpClient(httpClient)
        .retryOptions(retryOptions)
        .buildClient();
  }

  /**
   * Probes Azure connectivity/credentials at startup, delegating to the resolver so it uses the
   * exact API — and thus the minimal access policy — that mode resolves with. The probe is
   * best-effort: on failure it logs a warning and lets the store be built anyway, so a transient
   * Azure problem during boot cannot stop the application from starting. The misconfiguration then
   * surfaces on the first real {@link #resolve} or {@link #list} call.
   *
   * <p>Package-private so the warn-and-continue behaviour can be unit-tested with a stub resolver;
   * the per-mode probe choice is tested on the resolvers themselves.
   */
  static void validateConnectivity(final AzureSecretResolver resolver) {
    try {
      resolver.validateConnectivity();
    } catch (final RuntimeException e) {
      LOG.warn(
          "Failed to validate Azure Key Vault connectivity/credentials at startup; "
              + "the store will still be created and the error will surface on first use: {}",
          e.getMessage(),
          e);
    }
  }

  @Override
  public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
    return resolver.resolve(names);
  }

  @Override
  public List<String> list() {
    return resolver.list();
  }

  @Override
  public int namesPerCall() {
    return resolver.namesPerCall();
  }

  @Override
  public void close() {
    // Azure's SecretClient and the underlying HttpClient are not Closeable, unlike the GCP and AWS
    // SDK clients: there is no handle to release here. Idle HTTP connections are reclaimed by the
    // shared Netty/JDK connection pool on their own timeout. This no-op is deliberate.
  }

  private static AzureSecretResolver buildResolver(
      final SecretClient client,
      final String pathPrefix,
      final @Nullable String containerSecretName,
      final InstantSource clock) {
    return containerSecretName == null || containerSecretName.isBlank()
        ? new OneByOneSecretResolver(client, pathPrefix, clock)
        : new ContainerSecretResolver(client, pathPrefix, containerSecretName);
  }

  private static String normalize(final @Nullable String pathPrefix) {
    return pathPrefix == null || pathPrefix.isBlank() ? "" : pathPrefix;
  }
}
