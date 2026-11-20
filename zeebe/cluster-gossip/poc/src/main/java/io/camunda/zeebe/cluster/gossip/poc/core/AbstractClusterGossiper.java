/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip.poc.core;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.cluster.gossip.ClusterGossiper;
import io.camunda.zeebe.cluster.gossip.GlobalKey;
import io.camunda.zeebe.cluster.gossip.GlobalListener;
import io.camunda.zeebe.cluster.gossip.GossipKey;
import io.camunda.zeebe.cluster.gossip.MemberKey;
import io.camunda.zeebe.cluster.gossip.MemberListener;
import io.camunda.zeebe.cluster.gossip.MergeStrategy.LastWriterWins;
import io.camunda.zeebe.cluster.gossip.MergeStrategy.Merging;
import io.camunda.zeebe.cluster.gossip.Subscription;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Everything a {@link ClusterGossiper} does except moving state between members: key registration,
 * the local view of GLOBAL and MEMBER values, merging, and listener dispatch. Subclasses only
 * implement the transport, which lets both proofs of concept share the exact same semantics and
 * differ only in how state travels.
 *
 * <p>All state is guarded by {@link #lock}. Listener callbacks are handed to the subscriber's
 * executor after the lock is released.
 */
public abstract class AbstractClusterGossiper implements ClusterGossiper, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractClusterGossiper.class);

  protected final Object lock = new Object();
  protected final MemberId localMemberId;
  private final LongSupplier clock;
  private final KeyRegistry registry = new KeyRegistry();
  private final Map<String, GlobalEntry> globals = new HashMap<>();
  private final Map<MemberId, Map<String, byte[]>> memberValues = new HashMap<>();
  private final List<GlobalSubscription<?>> globalSubscriptions = new CopyOnWriteArrayList<>();
  private final List<MemberSubscription<?>> memberSubscriptions = new CopyOnWriteArrayList<>();
  private final GossipStats stats = new GossipStats();

  protected AbstractClusterGossiper(final MemberId localMemberId, final LongSupplier clock) {
    this.localMemberId = localMemberId;
    this.clock = clock;
  }

  /** Starts the transport. */
  public abstract void start();

  /** Stops the transport. */
  @Override
  public abstract void close();

  // ---------------------------------------------------------------------------------------------
  // Transport hooks, called while holding the lock. Implementations must not block.
  // ---------------------------------------------------------------------------------------------

  /** The local GLOBAL entry changed, because of a local write or a merged remote value. */
  protected abstract void onGlobalChanged(String name, GlobalEntry entry);

  /** The local member's own copy of a MEMBER key changed. */
  protected abstract void onLocalMemberValueChanged(String name, byte[] bytes);

  /** A key was used for the first time on this member. */
  protected void onKeyRegistered(final GossipKey<?> key, final List<Runnable> notifications) {}

  // ---------------------------------------------------------------------------------------------
  // For transports: apply received state. Call while holding the lock, e.g. via withLock.
  // ---------------------------------------------------------------------------------------------

  /** Runs the action under the lock, then dispatches the notifications it collected. */
  protected final void withLock(final Consumer<List<Runnable>> action) {
    final var notifications = new ArrayList<Runnable>();
    synchronized (lock) {
      action.accept(notifications);
    }
    notifications.forEach(Runnable::run);
  }

  /**
   * Merges a GLOBAL entry received from another member.
   *
   * @return whether the local value changed
   */
  protected final boolean mergeRemoteGlobal(
      final String name, final GlobalEntry remote, final List<Runnable> notifications) {
    final var current = globals.get(name);
    final GlobalEntry next;
    if (remote.lastWriterWins()) {
      if (current != null && !remote.isNewerThan(current)) {
        return false;
      }
      next = remote;
    } else {
      // a custom merge needs the key's merge function; values of keys not registered here yet are
      // dropped and arrive again with a later sync
      if (!(registry.get(name) instanceof final GlobalKey<?> key)) {
        return false;
      }
      final var merged = mergeCustom(key, current, remote);
      if (merged == null) {
        return false;
      }
      next = merged;
    }
    if (next.hasSameBytes(current)) {
      return false;
    }
    globals.put(name, next);
    onGlobalChanged(name, next);
    notifyGlobal(name, notifications);
    return true;
  }

  /** Sets another member's copy of a MEMBER key. */
  protected final void setMemberValue(
      final MemberId owner,
      final String name,
      final byte[] bytes,
      final List<Runnable> notifications) {
    final var values = memberValues.computeIfAbsent(owner, ignored -> new HashMap<>());
    final var previous = values.put(name, bytes);
    if (previous == null || !Arrays.equals(previous, bytes)) {
      notifyMember(name, owner, Optional.of(bytes), notifications);
    }
  }

  /** Drops every MEMBER value of a member that left or restarted. */
  protected final void removeMemberValues(
      final MemberId owner, final List<Runnable> notifications) {
    final var values = memberValues.remove(owner);
    if (values != null) {
      values.keySet().forEach(name -> notifyMember(name, owner, Optional.empty(), notifications));
    }
  }

  /** Counters for comparing transports. */
  public final GossipStats stats() {
    return stats;
  }

  protected final Map<String, GlobalEntry> globals() {
    return globals;
  }

  /** The MEMBER values known locally, by owner, including the local member's own. */
  protected final Map<MemberId, Map<String, byte[]>> memberValues() {
    return memberValues;
  }

  // ---------------------------------------------------------------------------------------------
  // ClusterGossiper
  // ---------------------------------------------------------------------------------------------

  @Override
  public <T> void put(final GlobalKey<T> key, final T value) {
    withLock(
        notifications -> {
          register(key, notifications);
          putGlobal(key, value, notifications);
        });
  }

  @Override
  public <T> void update(final GlobalKey<T> key, final UnaryOperator<T> updater) {
    withLock(
        notifications -> {
          register(key, notifications);
          putGlobal(key, updater.apply(currentGlobal(key)), notifications);
        });
  }

  @Override
  public <T> T get(final GlobalKey<T> key) {
    final var result = new ArrayList<T>(1);
    withLock(
        notifications -> {
          register(key, notifications);
          result.add(currentGlobal(key));
        });
    return result.getFirst();
  }

  @Override
  public <T> void put(final MemberKey<T> key, final T value) {
    withLock(
        notifications -> {
          register(key, notifications);
          final var bytes = key.codec().encode(value);
          final var own = memberValues.computeIfAbsent(localMemberId, ignored -> new HashMap<>());
          final var previous = own.put(key.name(), bytes);
          if (previous == null || !Arrays.equals(previous, bytes)) {
            onLocalMemberValueChanged(key.name(), bytes);
            notifyMember(key.name(), localMemberId, Optional.of(bytes), notifications);
          }
        });
  }

  @Override
  public <T> Optional<T> get(final MemberKey<T> key, final MemberId member) {
    final var result = new ArrayList<Optional<T>>(1);
    withLock(
        notifications -> {
          register(key, notifications);
          final var bytes = memberValues.getOrDefault(member, Map.of()).get(key.name());
          result.add(bytes == null ? Optional.empty() : tryDecode(key, bytes));
        });
    return result.getFirst();
  }

  @Override
  public <T> Map<MemberId, T> getAll(final MemberKey<T> key) {
    final var result = new HashMap<MemberId, T>();
    withLock(
        notifications -> {
          register(key, notifications);
          memberValues.forEach(
              (member, values) -> {
                final var bytes = values.get(key.name());
                if (bytes != null) {
                  tryDecode(key, bytes).ifPresent(value -> result.put(member, value));
                }
              });
        });
    return Map.copyOf(result);
  }

  @Override
  public <T> Subscription subscribe(
      final GlobalKey<T> key, final Executor executor, final GlobalListener<T> listener) {
    final var subscription = new GlobalSubscription<>(key, executor, listener);
    withLock(
        notifications -> {
          register(key, notifications);
          globalSubscriptions.add(subscription);
          subscription.notify(currentGlobal(key), notifications);
        });
    return subscription;
  }

  @Override
  public <T> Subscription subscribe(
      final MemberKey<T> key, final Executor executor, final MemberListener<T> listener) {
    final var subscription = new MemberSubscription<>(key, executor, listener);
    withLock(
        notifications -> {
          register(key, notifications);
          memberSubscriptions.add(subscription);
          memberValues.forEach(
              (member, values) -> {
                final var bytes = values.get(key.name());
                if (bytes != null) {
                  tryDecode(key, bytes)
                      .ifPresent(
                          value -> subscription.notify(member, Optional.of(value), notifications));
                }
              });
        });
    return subscription;
  }

  // ---------------------------------------------------------------------------------------------

  private void register(final GossipKey<?> key, final List<Runnable> notifications) {
    if (registry.register(key)) {
      onKeyRegistered(key, notifications);
    }
  }

  private <T> T currentGlobal(final GlobalKey<T> key) {
    final var entry = globals.get(key.name());
    return entry == null
        ? key.defaultValue()
        : tryDecode(key, entry.bytes()).orElse(key.defaultValue());
  }

  private <T> void putGlobal(
      final GlobalKey<T> key, final T value, final List<Runnable> notifications) {
    final var current = globals.get(key.name());
    final GlobalEntry next =
        switch (key.mergeStrategy()) {
          case final LastWriterWins<T> ignored -> {
            // never write with a timestamp at or below the one held, so the own write wins locally
            final long now = clock.getAsLong();
            final long timestamp = current == null ? now : Math.max(now, current.timestamp() + 1);
            yield GlobalEntry.lastWriterWins(key.codec().encode(value), timestamp, localMemberId);
          }
          case final Merging<T> merging -> {
            final var currentValue =
                current == null ? Optional.<T>empty() : tryDecode(key, current.bytes());
            final T merged = currentValue.map(c -> merging.merge().apply(c, value)).orElse(value);
            yield GlobalEntry.merged(key.codec().encode(merged));
          }
        };
    globals.put(key.name(), next);
    if (!next.hasSameBytes(current)) {
      onGlobalChanged(key.name(), next);
      notifyGlobal(key.name(), notifications);
    }
  }

  private <T> @Nullable GlobalEntry mergeCustom(
      final GlobalKey<T> key, final @Nullable GlobalEntry current, final GlobalEntry remote) {
    if (!(key.mergeStrategy() instanceof final Merging<T> merging)) {
      return null;
    }
    final var remoteValue = tryDecode(key, remote.bytes());
    if (remoteValue.isEmpty()) {
      return null;
    }
    final var currentValue =
        current == null ? Optional.<T>empty() : tryDecode(key, current.bytes());
    if (currentValue.isEmpty()) {
      return remote;
    }
    final T merged = merging.merge().apply(currentValue.get(), remoteValue.get());
    return GlobalEntry.merged(key.codec().encode(merged));
  }

  private void notifyGlobal(final String name, final List<Runnable> notifications) {
    for (final var subscription : globalSubscriptions) {
      if (subscription.key().name().equals(name)) {
        notifyGlobal(subscription, notifications);
      }
    }
  }

  private <T> void notifyGlobal(
      final GlobalSubscription<T> subscription, final List<Runnable> notifications) {
    subscription.notify(currentGlobal(subscription.key()), notifications);
  }

  private void notifyMember(
      final String name,
      final MemberId member,
      final Optional<byte[]> bytes,
      final List<Runnable> notifications) {
    for (final var subscription : memberSubscriptions) {
      if (subscription.key().name().equals(name)) {
        notifyMember(subscription, member, bytes, notifications);
      }
    }
  }

  private <T> void notifyMember(
      final MemberSubscription<T> subscription,
      final MemberId member,
      final Optional<byte[]> bytes,
      final List<Runnable> notifications) {
    final Optional<T> value = bytes.flatMap(present -> tryDecode(subscription.key(), present));
    if (bytes.isPresent() && value.isEmpty()) {
      return;
    }
    subscription.notify(member, value, notifications);
  }

  private static <T> Optional<T> tryDecode(final GossipKey<T> key, final byte[] bytes) {
    try {
      return Optional.of(key.codec().decode(bytes));
    } catch (final RuntimeException e) {
      LOG.warn("Failed to decode value of gossip key '{}'; treating it as absent", key.name(), e);
      return Optional.empty();
    }
  }

  private final class GlobalSubscription<T> implements Subscription {
    private final GlobalKey<T> key;
    private final Executor executor;
    private final GlobalListener<T> listener;

    private GlobalSubscription(
        final GlobalKey<T> key, final Executor executor, final GlobalListener<T> listener) {
      this.key = key;
      this.executor = executor;
      this.listener = listener;
    }

    GlobalKey<T> key() {
      return key;
    }

    @Override
    public void close() {
      globalSubscriptions.remove(this);
    }

    void notify(final T value, final List<Runnable> notifications) {
      notifications.add(
          () ->
              executor.execute(
                  () -> {
                    if (globalSubscriptions.contains(this)) {
                      listener.onChange(value);
                    }
                  }));
    }
  }

  private final class MemberSubscription<T> implements Subscription {
    private final MemberKey<T> key;
    private final Executor executor;
    private final MemberListener<T> listener;

    private MemberSubscription(
        final MemberKey<T> key, final Executor executor, final MemberListener<T> listener) {
      this.key = key;
      this.executor = executor;
      this.listener = listener;
    }

    MemberKey<T> key() {
      return key;
    }

    @Override
    public void close() {
      memberSubscriptions.remove(this);
    }

    void notify(
        final MemberId member, final Optional<T> value, final List<Runnable> notifications) {
      notifications.add(
          () ->
              executor.execute(
                  () -> {
                    if (memberSubscriptions.contains(this)) {
                      listener.onChange(member, value);
                    }
                  }));
    }
  }
}
