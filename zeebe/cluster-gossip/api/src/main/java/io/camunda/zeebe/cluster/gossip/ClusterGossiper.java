/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip;

import io.atomix.cluster.MemberId;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;

/**
 * Shares small, eventually consistent, in-memory state across all cluster members, brokers and
 * gateways alike. Nothing is persisted.
 *
 * <p>Keys come in two scopes:
 *
 * <ul>
 *   <li>{@link GlobalKey GLOBAL}: one logical value cluster-wide. Any member may write it, and
 *       concurrent writes are resolved by the key's {@link MergeStrategy}. A value lives as long as
 *       at least one live member still holds it.
 *   <li>{@link MemberKey MEMBER}: one value per member. Only the member itself writes its copy;
 *       readers see every member's copy. A value lives for one run of its member: it disappears
 *       when the member leaves the cluster or restarts.
 * </ul>
 *
 * <p>Contract, independent of how state is transported:
 *
 * <ul>
 *   <li>Writes are non-blocking and thread-safe, visible to local readers right away, and
 *       propagated asynchronously. No acknowledgement is offered.
 *   <li>The implementation eventually re-delivers the current state of every key to every live
 *       member. Values received for a key not yet registered locally may be dropped, and are
 *       received again later.
 *   <li>A key is registered the first time it is used. Using a name already registered with an
 *       incompatible descriptor (different scope or merge strategy kind) throws {@link
 *       IllegalStateException}.
 *   <li>A received value that cannot be decoded looks absent to local readers; for a GLOBAL key
 *       that means the key's default value.
 *   <li>Implementations may limit the size of encoded values, so values should be kept small.
 * </ul>
 */
public interface ClusterGossiper {

  /**
   * Writes a GLOBAL value. The stored value is {@code merge(stored, value)}, or {@code value}
   * itself if nothing is stored yet; the key's default value is never merged into a write. Under
   * {@link MergeStrategy#lastWriterWins()} the value replaces the local one, though it can still
   * lose to a concurrent write on another member.
   */
  <T> void put(GlobalKey<T> key, T value);

  /**
   * Applies {@code updater} to the current local value (or the key's default) and writes the result
   * as {@link #put(GlobalKey, Object)} does. The read and the write are atomic with respect to
   * other local writes, but not to writes on other members: under {@link
   * MergeStrategy#lastWriterWins()} a concurrent write elsewhere can still win.
   */
  <T> void update(GlobalKey<T> key, UnaryOperator<T> updater);

  /**
   * Returns the merged value known locally, or the key's default value if none was received yet.
   */
  <T> T get(GlobalKey<T> key);

  /**
   * Writes the local member's copy of a MEMBER key. There is no way to write another member's copy.
   */
  <T> void put(MemberKey<T> key, T value);

  /**
   * Returns the given member's copy, or empty if that member has not written the key in its current
   * run, is not a member, or its value could not be decoded.
   */
  <T> Optional<T> get(MemberKey<T> key, MemberId member);

  /** Returns a snapshot of every member's current copy, including the local member's own. */
  <T> Map<MemberId, T> getAll(MemberKey<T> key);

  /**
   * Subscribes to changes of a GLOBAL key. The listener is called with the current value right
   * away, then on every change. Changes may be coalesced: the listener is guaranteed to eventually
   * see the latest value, not every intermediate value. Local writes notify too.
   *
   * @param executor runs every callback, and must not block; a direct executor runs callbacks on
   *     the gossiper's own thread
   */
  <T> Subscription subscribe(GlobalKey<T> key, Executor executor, GlobalListener<T> listener);

  /**
   * Subscribes to changes of a MEMBER key. The listener is called right away once for every member
   * holding a value, then on every change. A member leaving or restarting is reported with an empty
   * value. Changes may be coalesced as for {@link #subscribe(GlobalKey, Executor, GlobalListener)}.
   *
   * @param executor runs every callback, and must not block; a direct executor runs callbacks on
   *     the gossiper's own thread
   */
  <T> Subscription subscribe(MemberKey<T> key, Executor executor, MemberListener<T> listener);
}
