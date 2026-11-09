/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretStore;
import io.camunda.secretstore.azure.AzureKeyVaultStoreConfig.Mode;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A {@link SecretStore} backed by Azure Key Vault.
 *
 * <p>How a reference is looked up is delegated to one {@link AzureSecretResolver}, chosen once at
 * construction from {@link AzureKeyVaultStoreConfig#mode()}: {@link OneByOneSecretResolver} (one
 * Key Vault secret per reference) or {@link ContainerSecretResolver} (every reference is a key of
 * one JSON secret). Adding a resolution mode means adding a resolver, not touching this class.
 *
 * <p>Signing in and reading the vault are not implemented yet (camunda/camunda#67225,
 * camunda/camunda#67226): until then {@link #resolve} reports every name as not found and {@link
 * #list} returns no names.
 *
 * <p>This class is thread-safe: it and its resolvers keep no mutable state.
 */
public final class AzureKeyVaultSecretStore implements SecretStore {

  private final AzureSecretResolver resolver;

  AzureKeyVaultSecretStore(final AzureSecretResolver resolver) {
    this.resolver = resolver;
  }

  /** Builds a store from configuration, with the resolver for the configured mode. */
  public static AzureKeyVaultSecretStore fromConfig(final AzureKeyVaultStoreConfig config) {
    final AzureSecretResolver resolver =
        config.mode() == Mode.CONTAINER
            ? new ContainerSecretResolver()
            : new OneByOneSecretResolver();
    return new AzureKeyVaultSecretStore(resolver);
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
}
