/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/**
 * A {@link SecretStore} that holds the values it resolved, so {@link #resolve} is cache-first and
 * the two reads below are possible at all. This is what {@link SecretStoreRegistry} hands out: a
 * store implementing this natively (an SDK with its own client-side cache) satisfies the contract
 * without sitting behind a second cache, and any other store is wrapped in a {@link
 * CachingSecretStore} to satisfy it.
 *
 * <p>The two reads exist for the two callers a plain {@link #resolve} does not serve: the engine
 * needs a lookup that cannot block, and the background resolution needs a read that does not trust
 * what is held.
 */
@NullMarked
public interface LocallyCachedSecretStore extends SecretStore {

  /**
   * Returns the value this store already holds for the secret name, without reading the backing
   * store.
   *
   * <p>This is what the engine needs on the job activation path: it runs on the stream processor
   * thread, where blocking on a file read or an AWS Secrets Manager call would stall processing. A
   * job whose secret is not held locally is parked and its reference resolved in the background
   * instead, so a lookup that answers "not held locally" costs the job a wait, not a failure.
   *
   * <p>Implementations must neither read the backing store nor block: whatever this answers has to
   * be answerable from memory. An empty result means "not held locally", not "no such secret" — the
   * store may well hold it.
   *
   * <p>Implementations must not fail either, since a failure here fails the whole activation
   * command. A failure is nevertheless propagated rather than swallowed: a lookup that cannot
   * answer is broken, and reporting it as a miss would park every job behind it forever.
   *
   * @return the locally held value, or empty if the secret name is not held locally
   */
  Optional<String> lookupLocal(String name);

  /**
   * Resolves the given names against the backing store even where a value for them is already held,
   * holding on to a resolved value just as {@link #resolve} does.
   *
   * <p>For the caller whose outcome outlives the value: the background resolution writes a durable,
   * exported record stating that a secret resolved, which an exporter and a later activation both
   * act on, so it has to ask the store rather than trust a value held since before the secret may
   * have been deleted. Every other caller wants {@link #resolve}, which is cache-first.
   *
   * <p>This is also the only read whose failure clears an already-held value, since it is the one
   * caller treating the store's answer as authoritative: {@link #resolve} only ever reaches the
   * store for a name it does not hold, so a failure there has nothing of this store's own to
   * invalidate.
   */
  Map<String, SecretResolutionResult> resolveFromStore(Set<String> names);
}
