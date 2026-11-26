/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip.swim;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.cluster.gossip.ClusterGossiper;
import io.camunda.zeebe.cluster.gossip.GlobalKey;
import io.camunda.zeebe.cluster.gossip.GlobalListener;
import io.camunda.zeebe.cluster.gossip.MemberKey;
import io.camunda.zeebe.cluster.gossip.MemberListener;
import io.camunda.zeebe.cluster.gossip.Subscription;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link ClusterGossiper} that carries MEMBER values in SWIM member properties. GLOBAL keys are
 * not supported yet: every GLOBAL operation throws {@link UnsupportedOperationException}.
 *
 * <p>A MEMBER value is the property of its owner named exactly like the key, holding the encoded
 * value in Base64. That is the format components already use when they write member properties
 * directly, such as {@code brokerInfo}, so such a component can move to this gossiper without
 * changing what other members, including older versions, read. SWIM gossips the local member's
 * properties whenever they change and drops a member's properties when the member leaves or
 * restarts, which gives MEMBER values exactly the lifetime the contract requires.
 *
 * <p>Reads go straight to the properties SWIM holds. Each subscription remembers the value it last
 * notified per member, to tell on a membership event whether its key changed; properties no key
 * refers to are never read.
 *
 * <p>Create one instance per membership service and share it between components: key names must be
 * unique across the cluster, and separate instances cannot detect a name used twice. On the local
 * member, write a key's property only through this gossiper, never directly as well: local
 * subscribers are only notified of writes made through {@link #put(MemberKey, Object)}.
 *
 * <p>SWIM gossips a member's whole property set in one UDP datagram, which is dropped silently once
 * it no longer fits; probes and syncs over TCP still deliver it, but more slowly. Keep values
 * small.
 */
public final class SwimClusterGossiper
    implements ClusterGossiper, ClusterMembershipEventListener, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(SwimClusterGossiper.class);
  private static final String GLOBAL_UNSUPPORTED =
      "GLOBAL keys are not supported by the SWIM gossiper yet";

  private final ClusterMembershipService membershipService;
  private final MemberId localMemberId;
  private final Object lock = new Object();
  private final List<MemberSubscription<?>> subscriptions = new CopyOnWriteArrayList<>();
  // the last unreadable value warned about per key, so it is logged once, not on every read
  private final Map<String, Integer> reportedFailures = new ConcurrentHashMap<>();

  public SwimClusterGossiper(final ClusterMembershipService membershipService) {
    this.membershipService = membershipService;
    localMemberId = membershipService.getLocalMember().id();
  }

  /** Starts following membership changes. */
  public void start() {
    membershipService.addListener(this);
  }

  /** Stops following membership changes. The gossiper must not be used afterwards. */
  @Override
  public void close() {
    membershipService.removeListener(this);
  }

  @Override
  public void event(final ClusterMembershipEvent event) {
    // SWIM reports the local member with a snapshot of its properties taken before the event was
    // posted, which can be older than the last put; local changes are notified by put instead.
    if (event.subject().id().equals(localMemberId)) {
      return;
    }
    // the event carries a snapshot taken when it was posted, which can be older than what a
    // subscribe in the meantime already read, so the current state is read instead
    final var member = event.subject().id();
    switch (event.type()) {
      case MEMBER_ADDED, METADATA_CHANGED -> refresh(member, membershipService.getMember(member));
      case MEMBER_REMOVED -> refresh(member, null);
      default -> {}
    }
  }

  @Override
  public <T> void put(final MemberKey<T> key, final T value) {
    final var localMember = membershipService.getLocalMember();
    localMember
        .properties()
        .setProperty(key.name(), Base64.getEncoder().encodeToString(key.codec().encode(value)));
    refresh(localMemberId, localMember);
  }

  @Override
  public <T> Optional<T> get(final MemberKey<T> key, final MemberId member) {
    final var owner =
        member.equals(localMemberId)
            ? membershipService.getLocalMember()
            : membershipService.getMember(member);
    final var bytes = owner == null ? null : read(owner.properties(), key.name());
    return bytes == null ? Optional.empty() : decode(key, bytes);
  }

  @Override
  public <T> Map<MemberId, T> getAll(final MemberKey<T> key) {
    final var result = new HashMap<MemberId, T>();
    members()
        .forEach(
            member -> {
              final var bytes = read(member.properties(), key.name());
              if (bytes != null) {
                decode(key, bytes).ifPresent(value -> result.put(member.id(), value));
              }
            });
    return Map.copyOf(result);
  }

  @Override
  public <T> Subscription subscribe(
      final MemberKey<T> key, final Executor executor, final MemberListener<T> listener) {
    final var subscription = new MemberSubscription<>(key, executor, listener);
    synchronized (lock) {
      subscriptions.add(subscription);
      // the new subscription has been told nothing yet, so this replays every current value to it
      members().forEach(member -> subscription.refresh(member.id(), member.properties()));
      subscription.deliver();
    }
    return subscription;
  }

  @Override
  public <T> void put(final GlobalKey<T> key, final T value) {
    throw new UnsupportedOperationException(GLOBAL_UNSUPPORTED);
  }

  @Override
  public <T> void update(final GlobalKey<T> key, final UnaryOperator<T> updater) {
    throw new UnsupportedOperationException(GLOBAL_UNSUPPORTED);
  }

  @Override
  public <T> T get(final GlobalKey<T> key) {
    throw new UnsupportedOperationException(GLOBAL_UNSUPPORTED);
  }

  @Override
  public <T> Subscription subscribe(
      final GlobalKey<T> key, final Executor executor, final GlobalListener<T> listener) {
    throw new UnsupportedOperationException(GLOBAL_UNSUPPORTED);
  }

  /** All current members, with the local member's live properties rather than a snapshot. */
  private List<Member> members() {
    final var members = new ArrayList<Member>();
    membershipService.getMembers().stream()
        .filter(member -> !member.id().equals(localMemberId))
        .forEach(members::add);
    members.add(membershipService.getLocalMember());
    return members;
  }

  /**
   * Lets every subscription compare a member's current value with the one it last notified. A
   * member that is gone has no values. A change is queued for every subscription before any
   * callback runs, so a listener that writes in response cannot overtake the change for the other
   * listeners.
   */
  private void refresh(final MemberId member, final @Nullable Member current) {
    synchronized (lock) {
      final var properties = current == null ? null : current.properties();
      final var affected = List.copyOf(subscriptions);
      affected.forEach(subscription -> subscription.refresh(member, properties));
      affected.forEach(MemberSubscription::deliver);
    }
  }

  private byte @Nullable [] read(final Properties properties, final String name) {
    final var encoded = properties.getProperty(name);
    if (encoded == null) {
      return null;
    }
    try {
      return Base64.getDecoder().decode(encoded);
    } catch (final IllegalArgumentException e) {
      warnOnce(name, encoded.hashCode(), "Ignoring gossip property '{}', which is not Base64", e);
      return null;
    }
  }

  private <T> Optional<T> decode(final MemberKey<T> key, final byte[] bytes) {
    try {
      return Optional.of(key.codec().decode(bytes));
    } catch (final RuntimeException e) {
      warnOnce(
          key.name(),
          Arrays.hashCode(bytes),
          "Failed to decode the value of gossip key '{}'; treating it as absent",
          e);
      return Optional.empty();
    }
  }

  private void warnOnce(
      final String subject, final int valueHash, final String message, final Exception error) {
    final var previous = reportedFailures.put(subject, valueHash);
    if (previous == null || previous != valueHash) {
      LOG.warn(message, subject, error);
    }
  }

  private final class MemberSubscription<T> implements Subscription {

    private final MemberKey<T> key;
    private final SerialCallbacks callbacks;
    private final MemberListener<T> listener;
    // guarded by the gossiper's lock; the encoded value this listener was last told, per member
    private final Map<MemberId, byte[]> lastNotified = new HashMap<>();

    private MemberSubscription(
        final MemberKey<T> key, final Executor executor, final MemberListener<T> listener) {
      this.key = key;
      callbacks = new SerialCallbacks(executor);
      this.listener = listener;
    }

    /** Queues a notification if the member's value differs from the one the listener was told. */
    void refresh(final MemberId member, final @Nullable Properties properties) {
      final var before = lastNotified.get(member);
      final var after = properties == null ? null : read(properties, key.name());
      if (Arrays.equals(before, after)) {
        return;
      }
      if (after == null) {
        lastNotified.remove(member);
      } else {
        lastNotified.put(member, after);
      }
      final Optional<T> value = after == null ? Optional.empty() : decode(key, after);
      if (value.isEmpty() && (before == null || decode(key, before).isEmpty())) {
        // the value neither was nor is readable; there is nothing to tell
        return;
      }
      callbacks.enqueue(
          () -> {
            if (subscriptions.contains(this)) {
              listener.onChange(member, value);
            }
          });
    }

    void deliver() {
      callbacks.deliver();
    }

    @Override
    public void close() {
      subscriptions.remove(this);
    }
  }

  /**
   * Runs one subscription's callbacks one at a time, in the order they were queued, on the
   * subscriber's executor. Without this, a multi-threaded executor could run an older callback
   * last, and a listener that writes from a direct executor would deliver the resulting change to
   * other listeners before the change that triggered it.
   */
  private final class SerialCallbacks {

    private final Executor executor;
    // guarded by the gossiper's lock
    private final Queue<Runnable> pending = new ArrayDeque<>();
    private boolean draining;

    private SerialCallbacks(final Executor executor) {
      this.executor = executor;
    }

    /** Queues a callback without running it. Must be called while holding the gossiper's lock. */
    void enqueue(final Runnable callback) {
      pending.add(callback);
    }

    /**
     * Starts running queued callbacks unless that is already under way. Must be called while
     * holding the gossiper's lock.
     */
    void deliver() {
      if (!draining && !pending.isEmpty()) {
        draining = true;
        try {
          executor.execute(this::drain);
        } catch (final RuntimeException e) {
          // the queued callbacks stay queued and are handed over again with the next change
          draining = false;
          LOG.warn("Failed to hand gossip notifications to an executor", e);
        }
      }
    }

    private void drain() {
      while (true) {
        final Runnable next;
        synchronized (lock) {
          next = pending.poll();
          if (next == null) {
            draining = false;
            return;
          }
        }
        boolean completed = false;
        try {
          next.run();
          completed = true;
        } catch (final RuntimeException e) {
          // a failing listener must not keep later notifications from this or other listeners
          completed = true;
          LOG.warn("A gossip listener failed to handle a notification", e);
        } finally {
          if (!completed) {
            // an Error ends this drain; the next delivery runs the remaining callbacks
            synchronized (lock) {
              draining = false;
            }
          }
        }
      }
    }
  }
}
