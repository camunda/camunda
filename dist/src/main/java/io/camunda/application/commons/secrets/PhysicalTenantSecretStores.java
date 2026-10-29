/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import io.camunda.configuration.Secrets;
import io.camunda.secretstore.SecretStoreRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.InstantSource;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the secret stores of one physical tenant that is added or replaced while the node runs.
 * Unlike the stores of the boot tenants, which the application context owns, each result is owned
 * by its caller, which closes it when the tenant's module stops.
 */
@NullMarked
public final class PhysicalTenantSecretStores {

  private final InstantSource timeSource;
  private final MeterRegistry meterRegistry;

  PhysicalTenantSecretStores(final InstantSource timeSource, final MeterRegistry meterRegistry) {
    this.timeSource = timeSource;
    this.meterRegistry = meterRegistry;
  }

  /** The stores that jobs resolve secrets from, with their caches and meters. */
  public Owned<SecretStoreRegistry> jobStores(
      final String physicalTenantId, final Secrets secrets) {
    final var resources = new SecretStoreResources();
    try {
      return new Owned<>(
          SecretStoreConfiguration.buildRegistry(
              physicalTenantId, secrets, resources, timeSource, meterRegistry),
          resources);
    } catch (final RuntimeException | Error e) {
      resources.close();
      throw e;
    }
  }

  /** The store that configuration references of the tenant resolve from. */
  public Owned<InternalConfigSecretResolver> internalConfig(
      final String physicalTenantId, final Secrets secrets) {
    final var resources = new SecretStoreResources();
    try {
      final var registry =
          SecretStoreConfiguration.buildInternalConfigRegistry(
              physicalTenantId, secrets, resources);
      return new Owned<>(
          new InternalConfigSecretResolver(
              physicalTenantId,
              registry == null
                  ? null
                  : registry.getStores().get(SecretStoreRegistry.DEFAULT_STORE_ID)),
          resources);
    } catch (final RuntimeException | Error e) {
      resources.close();
      throw e;
    }
  }

  /** A value together with the resources behind it, which {@link #close()} releases. */
  public record Owned<T>(T value, AutoCloseable resources) implements AutoCloseable {

    /** A value whose resources someone else owns. */
    public static <T> Owned<T> borrowed(final T value) {
      return new Owned<>(value, () -> {});
    }

    @Override
    public void close() {
      try {
        resources.close();
      } catch (final Exception e) {
        throw new IllegalStateException("Failed to release secret store resources", e);
      }
    }
  }
}
