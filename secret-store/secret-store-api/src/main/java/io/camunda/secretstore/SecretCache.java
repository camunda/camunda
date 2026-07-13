/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import java.util.Optional;

/**
 * Caches resolved secret values in front of a {@link SecretStore}, keyed by that store's own {@link
 * SecretReference} type. One cache is created per configured store, so entries from different
 * stores never collide and each cache speaks the same reference type its store resolves.
 *
 * <p>Implementations must be thread-safe: a {@link SecretStore} may be resolved concurrently, so
 * the cache in front of it is accessed concurrently too.
 *
 * <p>This is the stable contract callers resolve secrets through; the minimal {@link
 * InMemorySecretCache} has no eviction or expiry, and a TTL/eviction variant replaces it later
 * behind this same interface.
 */
public interface SecretCache<T extends SecretReference> {

  /**
   * Returns the cached resolved value for the given reference.
   *
   * @return the cached value, or empty if the reference is not cached
   */
  Optional<String> get(T reference);

  /** Stores the resolved value for the reference, overwriting any previously cached value. */
  void put(T reference, String value);
}
