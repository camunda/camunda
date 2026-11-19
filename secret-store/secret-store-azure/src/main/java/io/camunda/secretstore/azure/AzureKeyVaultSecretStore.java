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
 * <p>Reading the vault is not implemented yet (camunda/camunda#67226): until then {@link #resolve}
 * reports every name as not found and {@link #list} returns no names.
 *
 * <p>This class is thread-safe: it and its resolvers keep no mutable state.
 */
public final class AzureKeyVaultSecretStore implements SecretStore {

  private final AzureSecretResolver resolver;
  private final SecretClient client;
  private final ExecutorService credentialExecutor;

  AzureKeyVaultSecretStore(
      final AzureSecretResolver resolver,
      final SecretClient client,
      final ExecutorService credentialExecutor) {
    this.resolver = resolver;
    this.client = client;
    this.credentialExecutor = credentialExecutor;
  }

  /**
   * Builds a store from configuration, with the resolver its container secret setting selects. It
   * signs in with the {@code AZURE_*} variables of this process's environment.
   */
  public static AzureKeyVaultSecretStore fromConfig(final AzureKeyVaultStoreConfig config) {
    return fromConfig(config, Configuration.getGlobalConfiguration(), null);
  }

  /**
   * @param environment where sign-in reads the {@code AZURE_*} variables from
   * @param httpClient the client every call to Entra ID, IMDS and the vault goes through; {@code
   *     null} for the SDK default
   */
  static AzureKeyVaultSecretStore fromConfig(
      final AzureKeyVaultStoreConfig config,
      final Configuration environment,
      final @Nullable HttpClient httpClient) {
    final AzureSecretResolver resolver =
        config.containerSecretId() != null
            ? new ContainerSecretResolver()
            : new OneByOneSecretResolver();
    final var credentialExecutor = newCredentialExecutor();
    try {
      final var credential =
          AzureCredentialChain.build(
              config,
              environment,
              httpClient,
              credentialExecutor,
              new SignInMonitor(config.vaultUrl()));
      return new AzureKeyVaultSecretStore(
          resolver, buildClient(config, credential, httpClient), credentialExecutor);
    } catch (final RuntimeException e) {
      credentialExecutor.shutdownNow();
      throw e;
    }
  }

  /**
   * Token acquisition runs on platform threads the store owns. The resolution pool calls the store
   * on virtual threads ({@link io.camunda.secretstore.ConcurrentSecretStore}), and the SDK's shared
   * pool can deadlock there when every carrier is pinned (Azure/azure-sdk-for-java#46231), as the
   * Azure backup store also works around. The pool creates threads on demand, reaps them after 60s
   * idle, and is shut down in {@link #close()}.
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

  /**
   * The client {@link #resolve} and {@link #list} will read the vault with (camunda/camunda#67226).
   */
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
    credentialExecutor.shutdownNow();
  }
}
