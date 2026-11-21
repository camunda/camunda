/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import com.azure.core.credential.TokenCredential;
import com.azure.core.http.HttpClient;
import com.azure.core.util.Configuration;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretStore;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jspecify.annotations.Nullable;

/**
 * A {@link SecretStore} backed by Azure Key Vault.
 *
 * <p>How a reference is looked up is delegated to one {@link AzureSecretResolver}, chosen once at
 * construction from {@link AzureKeyVaultStoreConfig#containerSecretId()}: {@link
 * ContainerSecretResolver} when it is set (every reference is a key of one JSON secret), {@link
 * OneByOneSecretResolver} otherwise (one Key Vault secret per reference). Adding a resolution mode
 * means adding a resolver, not touching this class.
 *
 * <p>Each instance signs in with its own {@link AzureCredentialChain credential chain} and talks to
 * the vault through its own {@link SecretClient}, so two physical tenants never share an identity
 * or a cached token. No credential is read from the configuration.
 *
 * <p>In one-by-one mode, {@link #resolve} reads each reference's latest version from the vault;
 * container mode and {@link #list} are not implemented yet (camunda/camunda#67226): they report
 * every name as not found and list no names. The store keeps no cache: the registry wraps it in
 * {@link io.camunda.secretstore.CachingSecretStore} and {@link
 * io.camunda.secretstore.ConcurrentSecretStore}, which fans a request out one name per call.
 *
 * <p>This class is thread-safe: its resolvers keep no mutable state, and its {@link SignInMonitor}
 * is thread-safe.
 */
public final class AzureKeyVaultSecretStore implements SecretStore {

  private final AzureSecretResolver resolver;
  private final SecretClient client;
  private final ExecutorService credentialExecutor;
  private final SignInMonitor signInMonitor;

  AzureKeyVaultSecretStore(
      final AzureSecretResolver resolver,
      final SecretClient client,
      final ExecutorService credentialExecutor,
      final SignInMonitor signInMonitor) {
    this.resolver = resolver;
    this.client = client;
    this.credentialExecutor = credentialExecutor;
    this.signInMonitor = signInMonitor;
  }

  /**
   * Builds a store from configuration, with the resolver its container secret setting selects. It
   * signs in with the {@code AZURE_*} variables of this process's environment.
   *
   * <p>Before returning, checks once that the store can reach its vault and sign in, logging the
   * outcome ({@link StartupCheck}). The store is returned whatever the outcome, within the call
   * timeout at most.
   *
   * @param physicalTenantId the physical tenant the store belongs to, named in its log lines
   * @param storeId the ID the store is configured under, tagging its meters
   * @param meterRegistry where the store publishes {@link
   *     io.camunda.secretstore.SecretStoreMetricsDoc#SIGNIN_FAILURES}; the caller tags it with the
   *     physical tenant. The meter is removed again on {@link #close()}.
   */
  public static AzureKeyVaultSecretStore fromConfig(
      final String physicalTenantId,
      final String storeId,
      final AzureKeyVaultStoreConfig config,
      final MeterRegistry meterRegistry) {
    return fromConfig(
        physicalTenantId,
        storeId,
        config,
        meterRegistry,
        Configuration.getGlobalConfiguration(),
        null);
  }

  /**
   * @param environment where sign-in reads the {@code AZURE_*} variables from
   * @param httpClient the client every call to Entra ID, IMDS and the vault goes through; {@code
   *     null} for the SDK default
   */
  static AzureKeyVaultSecretStore fromConfig(
      final String physicalTenantId,
      final String storeId,
      final AzureKeyVaultStoreConfig config,
      final MeterRegistry meterRegistry,
      final Configuration environment,
      final @Nullable HttpClient httpClient) {
    final var credentialExecutor = newCredentialExecutor();
    final var signInMonitor =
        new SignInMonitor(physicalTenantId, storeId, config.vaultUrl(), meterRegistry);
    final AzureKeyVaultSecretStore store;
    try {
      final var credential =
          AzureCredentialChain.build(
              config, environment, httpClient, credentialExecutor, signInMonitor);
      final var client = buildClient(config, credential, httpClient);
      store =
          new AzureKeyVaultSecretStore(
              resolver(config, client), client, credentialExecutor, signInMonitor);
    } catch (final RuntimeException e) {
      signInMonitor.close();
      credentialExecutor.shutdownNow();
      throw e;
    }
    StartupCheck.run(
        store.resolver,
        credentialExecutor,
        config.callTimeout(),
        signInMonitor,
        physicalTenantId,
        storeId,
        config.vaultUrl());
    return store;
  }

  private static AzureSecretResolver resolver(
      final AzureKeyVaultStoreConfig config, final SecretClient client) {
    final var configuredPrefix = config.pathPrefix();
    final var pathPrefix = configuredPrefix == null ? "" : configuredPrefix;
    final var containerSecretId = config.containerSecretId();
    if (containerSecretId == null) {
      return new OneByOneSecretResolver(client, pathPrefix);
    }
    return new ContainerSecretResolver(client, pathPrefix + containerSecretId);
  }

  /**
   * Token acquisition, and the startup check, run on platform threads the store owns. The
   * resolution pool calls the store on virtual threads ({@link
   * io.camunda.secretstore.ConcurrentSecretStore}), and the SDK's shared pool can deadlock there
   * when every carrier is pinned (Azure/azure-sdk-for-java#46231), as the Azure backup store also
   * works around. The pool creates threads on demand, reaps them after 60s idle, and is shut down
   * in {@link #close()}.
   */
  static ExecutorService newCredentialExecutor() {
    return Executors.newCachedThreadPool(
        Thread.ofPlatform().daemon().name("secret-store-azure-credential-", 0).factory());
  }

  private static SecretClient buildClient(
      final AzureKeyVaultStoreConfig config,
      final TokenCredential credential,
      final @Nullable HttpClient httpClient) {
    final var builder =
        new SecretClientBuilder().vaultUrl(config.vaultUrl()).credential(credential);
    if (httpClient != null) {
      builder.httpClient(httpClient);
    }
    return builder.buildClient();
  }

  /** The client {@link #resolve} and {@link #list} read the vault with. */
  SecretClient client() {
    return client;
  }

  ExecutorService credentialExecutor() {
    return credentialExecutor;
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
    // first, so a token request the shutdown interrupts is not reported as a failed sign-in
    signInMonitor.close();
    credentialExecutor.shutdownNow();
  }
}
