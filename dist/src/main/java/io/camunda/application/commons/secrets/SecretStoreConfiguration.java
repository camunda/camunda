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
import io.camunda.configuration.Secrets;
import io.camunda.configuration.Secrets.AwsSecretsManagerStore;
import io.camunda.configuration.Secrets.FileStore;
import io.camunda.configuration.Secrets.GcpSecretManagerStore;
import io.camunda.configuration.Secrets.Stores;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.secretstore.ConcurrentSecretStore;
import io.camunda.secretstore.NoopSecretStore;
import io.camunda.secretstore.SecretCacheFactory;
import io.camunda.secretstore.SecretStore;
import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.secretstore.aws.AwsSecretsManagerSecretStore;
import io.camunda.secretstore.aws.AwsSecretsManagerStoreConfig;
import io.camunda.secretstore.file.FileBasedSecretStore;
import io.camunda.secretstore.gcp.GcpSecretManagerSecretStore;
import io.camunda.secretstore.gcp.GcpSecretManagerStoreConfig;
import io.camunda.zeebe.shared.management.ActorClockService;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.nio.file.Path;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
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

  /**
   * @param meterRegistry the cluster-wide registry each physical tenant's secret cache meters are
   *     forwarded to. Each tenant gets its own {@link MicrometerUtil#wrap wrapped registry} tagging
   *     them with {@link PartitionKeyNames#PHYSICAL_TENANT}, as {@code RdbmsDataSources} does for
   *     its pools — a store ID is unique only within a tenant, so without that tag two tenants
   *     using the same store ID would register the same series twice: Micrometer hands back the
   *     meter that already exists, and the two caches silently share it.
   */
  @Bean
  public SecretStoreRegistries secretStoreRegistries(
      final PhysicalTenantResolver resolver,
      final ActorClockService clockService,
      final MeterRegistry meterRegistry) {
    final Map<String, SecretStoreRegistry> registries = new LinkedHashMap<>();
    // tracks every store successfully constructed across all tenants processed so far, so a
    // failure partway through (e.g. a later tenant's AWS store failing to build) can close
    // them instead of leaking their underlying clients/connections
    final List<SecretStore> created = new ArrayList<>();
    // tracked for the same reason: a wrapped registry left behind by a failed startup would keep
    // publishing the meters of a cache nothing resolves through
    final List<MeterRegistry> tenantMeterRegistries = new ArrayList<>();
    // tracked for the same reason: a pool backing a store that gets rolled back must not outlive
    // it, since nothing else references the pool to shut it down later
    final List<ExecutorService> concurrencyPools = new ArrayList<>();
    final var timeSource = new ActorClockInstantSource(clockService);
    try {
      resolver
          .mapValues(Camunda::getSecrets)
          .forEach(
              (tenantId, secrets) ->
                  registries.put(
                      tenantId,
                      buildRegistry(
                          tenantId,
                          secrets,
                          created,
                          timeSource,
                          meterRegistry,
                          tenantMeterRegistries,
                          concurrencyPools)));
    } catch (final RuntimeException e) {
      closeAll(created);
      tenantMeterRegistries.forEach(MicrometerUtil::close);
      concurrencyPools.forEach(ExecutorService::shutdownNow);
      throw e;
    }
    return new SecretStoreRegistries(Map.copyOf(registries));
  }

  private static SecretStoreRegistry buildRegistry(
      final String tenantId,
      final Secrets secrets,
      final List<SecretStore> created,
      final InstantSource timeSource,
      final MeterRegistry meterRegistry,
      final List<MeterRegistry> tenantMeterRegistries,
      final List<ExecutorService> concurrencyPools) {
    final Stores config = secrets.getStores();
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
    // read before any store is built, for the same reason: an out-of-bounds ttl/max-size fails
    // startup without first constructing an AWS/GCP client only to roll it back again
    final Secrets.Cache cacheConfig;
    try {
      // validates as a side effect, see Secrets#getCache
      cacheConfig = secrets.getCache();
    } catch (final IllegalArgumentException e) {
      // the config object carries no tenant identity, so the property path it reports is the
      // canonical one; name the tenant here, where it is known, or an operator running several
      // cannot tell whose override is at fault
      throw new IllegalArgumentException(
          "Physical tenant '"
              + tenantId
              + "' has an invalid secret cache configuration: "
              + e.getMessage(),
          e);
    }
    final int maxConcurrency;
    try {
      maxConcurrency = secrets.getMaxConcurrency();
    } catch (final IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Physical tenant '"
              + tenantId
              + "' has an invalid secret max-concurrency configuration: "
              + e.getMessage(),
          e);
    }
    final Map<String, SecretStore> stores = new LinkedHashMap<>();
    STORE_BINDINGS.forEach(binding -> binding.registerAll(config, stores, created, tenantId));
    if (stores.isEmpty()) {
      stores.put(SecretStoreRegistry.DEFAULT_STORE_ID, NOOP_STORE);
      LOG.info("No secret stores configured for physical tenant '{}', using noop store", tenantId);
      // the three-argument constructor leaves the default cache publishing nothing: the noop store
      // caches nothing, so its hit rate would read 0% forever against no TTL or size to tune
      return new SecretStoreRegistry(Map.copyOf(stores), Map.of(), timeSource);
    }
    // one pool shared by every store this tenant configures (today always at most one store, see
    // the totalStores check above), rather than one per store, since nothing here needs them kept
    // apart. Built only for a store that actually pays a round trip per name: wrapping a noop,
    // container, or batched store would only add a thread hop with nothing to overlap.
    if (maxConcurrency > 1 && stores.values().stream().anyMatch(SecretStore::resolvesOneByOne)) {
      final var pool =
          Executors.newFixedThreadPool(maxConcurrency, concurrencyThreadFactory(tenantId));
      concurrencyPools.add(pool);
      stores.replaceAll(
          (id, store) ->
              store.resolvesOneByOne()
                  ? new ConcurrentSecretStore(store, pool, maxConcurrency)
                  : store);
    }
    // wrapped only now that the tenant is known to have a store worth measuring, so a tenant that
    // fails the rules above never leaves a registry behind either
    final var tenantMeterRegistry =
        MicrometerUtil.wrap(
            meterRegistry, Tags.of(PartitionKeyNames.PHYSICAL_TENANT.asString(), tenantId));
    tenantMeterRegistries.add(tenantMeterRegistry);
    return new SecretStoreRegistry(
        Map.copyOf(stores), cacheFactory(cacheConfig, timeSource, tenantMeterRegistry));
  }

  /**
   * Builds one cache per store the registry wraps, at the configured ttl and max-size, publishing
   * what it does under the store ID it was built for. A fresh instance per call is what the factory
   * contract requires: a cache is keyed by the bare secret name, so an instance shared by two
   * stores would let one store's value answer for another store's secret of the same name.
   *
   * <p>Handed to the registry rather than applied to a map of prebuilt caches so that a store that
   * caches natively never reaches this at all. Only the registry knows it leaves such a store
   * unwrapped, so building a cache for every configured store ID here would register the meters of
   * a cache nothing ever resolves through — seven series stuck at zero, for a store {@link
   * io.camunda.secretstore.SecretCacheMetricsDoc} promises emits none.
   *
   * <p>The registry is already wrapped per tenant, so the meters carry both the tenant and the
   * store ID they belong to.
   */
  private static SecretCacheFactory cacheFactory(
      final Secrets.Cache config,
      final InstantSource timeSource,
      final MeterRegistry meterRegistry) {
    return SecretCacheFactory.metered(
        config.getMaxSize(), config.getTtl(), timeSource, meterRegistry);
  }

  /**
   * Daemon threads, so an un-shut-down pool never blocks JVM exit on graceful shutdown. That is the
   * same guarantee the AWS/GCP SDK clients rely on today, since nothing calls {@code close()} on
   * this bean outside the startup-rollback path above.
   */
  private static ThreadFactory concurrencyThreadFactory(final String tenantId) {
    final var counter = new AtomicInteger();
    return runnable -> {
      final var thread =
          new Thread(runnable, "secret-resolution-" + tenantId + "-" + counter.incrementAndGet());
      thread.setDaemon(true);
      return thread;
    };
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
