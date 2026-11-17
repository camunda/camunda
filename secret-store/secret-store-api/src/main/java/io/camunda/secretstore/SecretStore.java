/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementations must be thread-safe: {@link #resolve} and {@link #list} may be called
 * concurrently from multiple threads.
 */
public interface SecretStore extends AutoCloseable {

  /**
   * Resolves a set of secret names in a single call.
   *
   * <p>Returns a result for <em>every</em> name in the input set. Per-secret failures (e.g. secret
   * not found, access denied) are reported as {@link SecretResolutionResult.Failed}.
   *
   * <p>Implementations must be thread-safe.
   *
   * @throws SecretStoreUnavailableException if the backing store cannot be accessed or its content
   *     is malformed
   */
  Map<String, SecretResolutionResult> resolve(Set<String> names);

  /**
   * Lists all secret names known to this store.
   *
   * <p>Implementations must be thread-safe.
   *
   * @throws SecretStoreUnavailableException if the backing store cannot be accessed or its content
   *     is malformed
   */
  List<String> list();

  /**
   * Whether this is a store of the given type, seeing through any wrapper: a store wrapped for
   * caching answers for the store it wraps rather than for the wrapper. That lets a caller tell
   * which store was configured without reading it, which for a cloud store would mean a call to the
   * provider.
   *
   * <p>Visible for testing: only a configuration test needs to tell a store apart by type, since
   * every other caller resolves through {@link #resolve} regardless of which store answers.
   */
  default boolean is(final Class<? extends SecretStore> storeType) {
    return storeType.isInstance(this);
  }

  /**
   * How many names one {@link #resolve} call covers, for a store whose cost scales with the number
   * of sequential backend calls it issues (a one-by-one cloud store issuing one call per name, or a
   * batched one issuing several sequential calls each covering {@code batchSize} names). {@link
   * ConcurrentSecretStore} reads this to split a request into chunks of this size and resolve them
   * concurrently instead of one after another.
   *
   * <p>Returns {@link Integer#MAX_VALUE} for a store whose single {@link #resolve} call already
   * covers the whole request (a container-style store, or one backed by local disk that pays no
   * round trip at all): fanning such a store out would only add backend calls or a thread hop, not
   * remove round trips.
   */
  default int namesPerCall() {
    return Integer.MAX_VALUE;
  }

  @Override
  default void close() {}
}
