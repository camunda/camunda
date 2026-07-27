/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util;

import io.camunda.secretstore.NoopSecretStore;
import io.camunda.secretstore.SecretCache;
import io.camunda.secretstore.SecretStoreRegistry;
import java.util.Map;
import java.util.Optional;

/** Factories for {@link SecretStoreRegistry} instances used in engine tests. */
public final class SecretStoreRegistries {

  private SecretStoreRegistries() {}

  /**
   * Returns a single-store registry whose cache resolves every secret name to the given value, so
   * jobs with secret references always stay activatable.
   */
  public static SecretStoreRegistry resolveAll(final String value) {
    final SecretCache resolveAllCache =
        new SecretCache() {
          @Override
          public Optional<String> get(final String name) {
            return Optional.of(value);
          }

          @Override
          public void put(final String name, final String ignored) {}
        };
    return new SecretStoreRegistry(
        Map.of("default", new NoopSecretStore()), Map.of("default", resolveAllCache));
  }
}
