/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import io.camunda.cluster.PhysicalTenantIds;
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
import io.camunda.zeebe.shared.management.ActorClockService;
import java.nio.file.Path;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
          new StoreBinding<>("file", "file", Stores::getFile, SecretStoreConfiguration::fileStore),
          new StoreBinding<>(
              "aws", "AWS Secrets Manager", Stores::getAws, SecretStoreConfiguration::awsStore),
          new StoreBinding<>(
              "gcp", "GCP Secret Manager", Stores::getGcp, SecretStoreConfiguration::gcpStore));

  @Bean
  public SecretStoreRegistries secretStoreRegistries(
      final PhysicalTenantResolver resolver, final ActorClockService clockService) {
    final Map<String, SecretStoreRegistry> registries = new LinkedHashMap<>();
    // tracks every store successfully constructed across all tenants processed so far, so a
    // failure partway through (e.g. a later tenant's AWS store failing to build) can close
    // them instead of leaking their underlying clients/connections
    final List<SecretStore> created = new ArrayList<>();
    final var timeSource = new ActorClockInstantSource(clockService);
    try {
      resolver
          .mapValues(Camunda::getSecrets)
          .forEach(
              (tenantId, secrets) ->
                  registries.put(
                      tenantId, buildRegistry(tenantId, secrets.getStores(), created, timeSource)));
    } catch (final RuntimeException e) {
      closeAll(created);
      throw e;
    }
    return new SecretStoreRegistries(Map.copyOf(registries));
  }

  private static SecretStoreRegistry buildRegistry(
      final String tenantId,
      final Stores config,
      final List<SecretStore> created,
      final InstantSource timeSource) {
    // cap is one store total per tenant, counted across all store types combined
    final long totalStores =
        STORE_BINDINGS.stream().mapToLong(binding -> binding.ids(config).size()).sum();
    if (totalStores > 1) {
      throw new IllegalStateException(
          "Physical tenant '"
              + tenantId
              + "' has "
              + totalStores
              + " secret stores configured, but only one is supported at this time");
    }
    // both rules are checked before any store is built, so the operator gets the configuration
    // error rather than whatever the store's own construction fails with — an AWS or GCP store
    // eagerly builds a client and probes credentials, which would otherwise surface first for a
    // configuration that is rejected anyway
    STORE_BINDINGS.forEach(binding -> binding.requireSupportedStoreIds(config, tenantId));
    final Map<String, SecretStore> stores = new LinkedHashMap<>();
    STORE_BINDINGS.forEach(binding -> binding.registerAll(config, stores, created, tenantId));
    if (stores.isEmpty()) {
      stores.put(SecretStoreRegistry.DEFAULT_STORE_ID, NOOP_STORE);
      LOG.info("No secret stores configured for physical tenant '{}', using noop store", tenantId);
    }
    return new SecretStoreRegistry(Map.copyOf(stores), Map.of(), timeSource);
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
      final String normalizedId,
      final String tenantId,
      final String storeType,
      final SecretStore store) {
    stores.put(normalizedId, store);
    created.add(store);
    LOG.info(
        "Registered {} secret store '{}' for physical tenant '{}'",
        storeType,
        normalizedId,
        tenantId);
  }

  /** The store ID as it is registered and looked up: trimmed and case-insensitive. */
  private static String normalizeStoreId(final String storeId) {
    return storeId.trim().toLowerCase();
  }

  /**
   * Rejects a store under an ID no secret reference can address. A {@code camunda.secrets.<name>}
   * reference addresses {@link SecretStoreRegistry#DEFAULT_STORE_ID} and the store lookup that
   * resolves it is exact, so a store under any other ID would never be reached.
   *
   * @param storeId the ID as the operator wrote it, so the property this names is the one to find
   *     in their configuration; the rule itself is checked against the normalized form
   */
  private static void requireSupportedStoreId(
      final String storeId, final String tenantId, final String storeTypeProperty) {
    if (SecretStoreRegistry.DEFAULT_STORE_ID.equals(normalizeStoreId(storeId))) {
      return;
    }
    final var storesProperty =
        (PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID.equals(tenantId)
                ? "camunda.secrets.stores."
                : "camunda.physical-tenants." + tenantId + ".secrets.stores.")
            + storeTypeProperty;
    throw new IllegalStateException(
        "Physical tenant '%s' configures secret store '%s', but the only supported store id is '%s'; rename %s.%s to %s.%s"
            .formatted(
                tenantId,
                storeId,
                SecretStoreRegistry.DEFAULT_STORE_ID,
                storesProperty,
                storeId,
                storesProperty,
                SecretStoreRegistry.DEFAULT_STORE_ID));
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

  /**
   * Adapts {@link ActorClockService}, which exposes only epoch millis, into an {@link
   * InstantSource} for the secret cache's expiry — so {@code /actuator/clock} time travel reaches
   * cached secrets, and both broker and gateway share the same time source as the rest of the
   * platform.
   *
   * <p>Overrides {@link #millis()} rather than relying on the default {@code
   * instant().toEpochMilli()}, since the cache reads its ticker on every lookup, including on the
   * job-activation path where an allocation per lookup is exactly what that path's contract rules
   * out. {@link io.camunda.zeebe.scheduler.clock.ActorClock} overrides the same pair for the same
   * reason.
   */
  private record ActorClockInstantSource(ActorClockService clockService) implements InstantSource {

    @Override
    public Instant instant() {
      return Instant.ofEpochMilli(clockService.epochMilli());
    }

    @Override
    public long millis() {
      return clockService.epochMilli();
    }
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
   * @param propertyKey the {@code camunda.secrets.stores.<key>} segment this type binds to, so a
   *     configuration error can name the exact property the operator has to change
   * @param displayName human-readable store type name used in log messages
   * @param selector reads this type's {@code storeId -> config} entries from the tenant's stores
   * @param factory builds a store from one entry, applying any per-type validation
   */
  private record StoreBinding<C>(
      String propertyKey,
      String displayName,
      Function<Stores, Map<String, C>> selector,
      StoreFactory<C> factory) {

    private Set<String> ids(final Stores config) {
      return selector.apply(config).keySet();
    }

    private void requireSupportedStoreIds(final Stores config, final String tenantId) {
      ids(config).forEach(storeId -> requireSupportedStoreId(storeId, tenantId, propertyKey));
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
                      normalizeStoreId(storeId),
                      tenantId,
                      displayName,
                      // the raw id is what the operator wrote, so it is what a factory names in its
                      // own validation errors
                      factory.create(storeId, tenantId, entry)));
    }
  }
}
