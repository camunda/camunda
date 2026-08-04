/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.Secrets.AwsSecretsManagerStore;
import io.camunda.configuration.Secrets.FileStore;
import io.camunda.configuration.Secrets.GcpSecretManagerStore;
import io.camunda.configuration.Secrets.Stores;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.secretstore.NoopSecretStore;
import io.camunda.secretstore.SecretStore;
import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.secretstore.aws.AwsSecretsManagerSecretStore;
import io.camunda.secretstore.aws.AwsSecretsManagerStoreConfig;
import io.camunda.secretstore.file.FileBasedSecretStore;
import io.camunda.secretstore.gcp.GcpSecretManagerSecretStore;
import io.camunda.secretstore.gcp.GcpSecretManagerStoreConfig;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers one {@link SecretStoreRegistry} per physical tenant, keyed by physical tenant ID. */
@Configuration(proxyBeanMethods = false)
@NullMarked
public class SecretStoreConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(SecretStoreConfiguration.class);
  private static final NoopSecretStore NOOP_STORE = new NoopSecretStore();

  /**
   * One entry per supported secret store type. Adding a new backend means adding a single binding
   * here (plus its factory method) — the per-tenant count check and registration loop below are
   * type-agnostic and never need to change.
   */
  private static final List<StoreBinding<?>> STORE_BINDINGS =
      List.of(
          new StoreBinding<>("file", Stores::getFile, SecretStoreConfiguration::fileStore),
          new StoreBinding<>(
              "AWS Secrets Manager", Stores::getAws, SecretStoreConfiguration::awsStore),
          new StoreBinding<>(
              "GCP Secret Manager", Stores::getGcp, SecretStoreConfiguration::gcpStore));

  @Bean
  public SecretStoreRegistries secretStoreRegistries(final PhysicalTenantResolver resolver) {
    final Map<String, SecretStoreRegistry> registries = new LinkedHashMap<>();
    // tracks every store successfully constructed across all tenants processed so far, so a
    // failure partway through (e.g. a later tenant's AWS store failing to build) can close
    // them instead of leaking their underlying clients/connections
    final List<SecretStore> created = new ArrayList<>();
    try {
      resolver
          .mapValues(Camunda::getSecrets)
          .forEach(
              (tenantId, secrets) ->
                  registries.put(tenantId, buildRegistry(tenantId, secrets.getStores(), created)));
    } catch (final RuntimeException e) {
      closeAll(created);
      throw e;
    }
    return new SecretStoreRegistries(Map.copyOf(registries));
  }

  private static SecretStoreRegistry buildRegistry(
      final String tenantId, final Stores config, final List<SecretStore> created) {
    // cap is one store total per tenant, counted across all store types combined
    final long totalStores =
        STORE_BINDINGS.stream().mapToLong(binding -> binding.count(config)).sum();
    if (totalStores > 1) {
      throw new IllegalStateException(
          "Physical tenant '"
              + tenantId
              + "' has "
              + totalStores
              + " secret stores configured, but only one is supported at this time");
    }
    final Map<String, SecretStore> stores = new LinkedHashMap<>();
    STORE_BINDINGS.forEach(binding -> binding.registerAll(config, stores, created, tenantId));
    if (stores.isEmpty()) {
      stores.put("default", NOOP_STORE);
      LOG.info("No secret stores configured for physical tenant '{}', using noop store", tenantId);
    }
    return new SecretStoreRegistry(Map.copyOf(stores));
  }

  private static void closeAll(final List<SecretStore> stores) {
    for (final var store : stores) {
      try {
        store.close();
      } catch (final RuntimeException closeFailure) {
        LOG.warn("Failed to close secret store while rolling back a failed startup", closeFailure);
      }
    }
  }

  private static void registerStore(
      final Map<String, SecretStore> stores,
      final List<SecretStore> created,
      final String storeId,
      final String tenantId,
      final String storeType,
      final SecretStore store) {
    final var normalizedId = storeId.trim().toLowerCase();
    stores.put(normalizedId, store);
    created.add(store);
    LOG.info(
        "Registered {} secret store '{}' for physical tenant '{}'",
        storeType,
        normalizedId,
        tenantId);
  }

  private static SecretStore fileStore(
      final String storeId, final String tenantId, final FileStore config) {
    final var path = config.getPath();
    if (path.isBlank()) {
      throw new IllegalStateException(
          "File store '"
              + storeId
              + "' for physical tenant '"
              + tenantId
              + "' has no path configured");
    }
    return new FileBasedSecretStore(Path.of(path));
  }

  private static SecretStore awsStore(
      final String storeId, final String tenantId, final AwsSecretsManagerStore config) {
    return AwsSecretsManagerSecretStore.fromConfig(
        new AwsSecretsManagerStoreConfig(
            config.getRegion(),
            config.getPathPrefix(),
            config.getContainerSecretId(),
            null,
            AwsSecretsManagerStoreConfig.DEFAULT_MAX_RETRIES,
            config.isBatchEnabled(),
            config.getBatchSize()));
  }

  private static SecretStore gcpStore(
      final String storeId, final String tenantId, final GcpSecretManagerStore config) {
    return GcpSecretManagerSecretStore.fromConfig(
        new GcpSecretManagerStoreConfig(
            config.getProjectId(),
            config.getPathPrefix(),
            config.getEndpoint(),
            config.getContainerSecretId()));
  }

  /** Builds one {@link SecretStore} from a single configured entry of a given store type. */
  @FunctionalInterface
  private interface StoreFactory<C> {
    SecretStore create(String storeId, String tenantId, C config);
  }

  /**
   * Binds a secret store type to the config entries it reads and the factory that turns one such
   * entry into a {@link SecretStore}. The type parameter {@code C} keeps the selected config type
   * and the factory's input type in lock-step, so the registration loop can iterate a heterogeneous
   * {@code List<StoreBinding<?>>} without any casts.
   *
   * @param displayName human-readable store type name used in log messages
   * @param selector reads this type's {@code storeId -> config} entries from the tenant's stores
   * @param factory builds a store from one entry, applying any per-type validation
   */
  private record StoreBinding<C>(
      String displayName, Function<Stores, Map<String, C>> selector, StoreFactory<C> factory) {

    private int count(final Stores config) {
      return selector.apply(config).size();
    }

    private void registerAll(
        final Stores config,
        final Map<String, SecretStore> stores,
        final List<SecretStore> created,
        final String tenantId) {
      selector
          .apply(config)
          .forEach(
              (storeId, entry) ->
                  registerStore(
                      stores,
                      created,
                      storeId,
                      tenantId,
                      displayName,
                      factory.create(storeId, tenantId, entry)));
    }
  }
}
