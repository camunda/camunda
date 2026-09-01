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
   * Whether {@link #resolve} costs one backend call per name rather than covering several names
   * with a single call (a batched or container-style store already does the latter, as does a store
   * backed by local disk that pays no round trip at all). {@link ConcurrentSecretStore} reads this
   * to decide whether resolving through a bounded thread pool has anything to gain: fanning out a
   * store that already covers many names per call would only add backend calls, not remove round
   * trips.
   */
  default boolean resolvesOneByOne() {
    return false;
  }

  @Override
  default void close() {}
}
