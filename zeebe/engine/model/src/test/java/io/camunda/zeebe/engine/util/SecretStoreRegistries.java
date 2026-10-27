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
import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretStore;
import io.camunda.secretstore.SecretStoreRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

          @Override
          public void remove(final String name) {}
        };
    return new SecretStoreRegistry(
        Map.of(SecretStoreRegistry.DEFAULT_STORE_ID, new NoopSecretStore()),
        Map.of(SecretStoreRegistry.DEFAULT_STORE_ID, resolveAllCache));
  }

  /**
   * Returns a single-store registry whose default store holds the given secrets but starts with a
   * cold cache, so a job referencing one of them is parked on its first activation and only becomes
   * activatable once the background resolution has read the store. This is what {@link
   * #resolveAll(String)} cannot exercise: its cache answers every name, so nothing ever parks.
   */
  public static SecretStoreRegistry resolvingFromStore(final Map<String, String> secrets) {
    // one-argument constructor: the registry puts a fresh in-memory cache in front of the store
    return new SecretStoreRegistry(
        Map.of(SecretStoreRegistry.DEFAULT_STORE_ID, new MapSecretStore(Map.copyOf(secrets))));
  }

  /** A store answering from a fixed map, so a read of it can be told apart from a cache hit. */
  private record MapSecretStore(Map<String, String> secrets) implements SecretStore {

    @Override
    public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
      return names.stream()
          .collect(
              Collectors.toMap(
                  name -> name,
                  name ->
                      Optional.ofNullable(secrets.get(name))
                          .<SecretResolutionResult>map(SecretResolutionResult.Resolved::new)
                          .orElseGet(
                              () ->
                                  new SecretResolutionResult.Failed(
                                      SecretErrorCode.NOT_FOUND, "no such secret", null))));
    }

    @Override
    public List<String> list() {
      return List.copyOf(secrets.keySet());
    }
  }
}
