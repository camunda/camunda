/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.Secrets;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.secretstore.SecretStore;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import org.agrona.CloseHelper;
import org.jspecify.annotations.Nullable;

/**
 * The configuration secrets store of each physical tenant, which configuration values refer to as
 * {@code secret:<name>}. These stores are kept apart from {@link SecretStoreRegistries}, so neither
 * the engine nor the secrets endpoints can read from them.
 */
public final class ConfigurationSecretStores implements AutoCloseable {

  private final Map<String, SecretStore> byPhysicalTenant = new ConcurrentHashMap<>();
  private final BiFunction<String, Secrets, @Nullable SecretStore> factory;

  public ConfigurationSecretStores(
      final BiFunction<String, Secrets, @Nullable SecretStore> factory) {
    this.factory = factory;
  }

  /** Builds the store of every physical tenant that configures one. */
  public static ConfigurationSecretStores of(final PhysicalTenantResolver resolver) {
    final var stores =
        new ConfigurationSecretStores(SecretStoreConfiguration::buildConfigurationStore);
    final var secretsByTenant = resolver.mapValues(Camunda::getSecrets);
    SecretStoreConfiguration.requireConfigurationStoresApart(secretsByTenant);
    try {
      secretsByTenant.forEach(stores::put);
    } catch (final Throwable e) {
      try {
        stores.close();
      } catch (final Throwable closeFailure) {
        e.addSuppressed(closeFailure);
      }
      throw e;
    }
    return stores;
  }

  public Optional<SecretStore> forPhysicalTenant(final String physicalTenantId) {
    return Optional.ofNullable(byPhysicalTenant.get(physicalTenantId));
  }

  /**
   * Builds the store of the given physical tenant and puts it in place of its current store, which
   * is then closed. If the new store fails to build, the current one stays in place.
   */
  public void put(final String physicalTenantId, final Secrets secrets) {
    final var store = factory.apply(physicalTenantId, secrets);
    final var replaced =
        store == null
            ? byPhysicalTenant.remove(physicalTenantId)
            : byPhysicalTenant.put(physicalTenantId, store);
    if (replaced != null) {
      replaced.close();
    }
  }

  /** Removes the store of the given physical tenant and closes it. */
  public void remove(final String physicalTenantId) {
    final var removed = byPhysicalTenant.remove(physicalTenantId);
    if (removed != null) {
      removed.close();
    }
  }

  @Override
  public void close() {
    final List<SecretStore> stores = List.copyOf(byPhysicalTenant.values());
    byPhysicalTenant.clear();
    CloseHelper.closeAll(stores);
  }
}
