/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip.testkit;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.cluster.gossip.ClusterGossiper;
import io.camunda.zeebe.cluster.gossip.GlobalKey;
import io.camunda.zeebe.cluster.gossip.GlobalListener;
import io.camunda.zeebe.cluster.gossip.GossipKey;
import io.camunda.zeebe.cluster.gossip.MemberKey;
import io.camunda.zeebe.cluster.gossip.MemberListener;
import io.camunda.zeebe.cluster.gossip.MergeStrategy;
import io.camunda.zeebe.cluster.gossip.MergeStrategy.LastWriterWins;
import io.camunda.zeebe.cluster.gossip.MergeStrategy.Merging;
import io.camunda.zeebe.cluster.gossip.Subscription;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Several in-memory {@link ClusterGossiper} members in one JVM, for testing consumers without a
 * real transport.
 *
 * <p>State moves between members only when a test calls {@link #exchange(MemberId, MemberId)} or
 * {@link #sync()}, so tests control the order in which updates arrive. Network partitions are
 * simulated with {@link #disconnect(MemberId, MemberId)}, {@link #isolate(MemberId)} and {@link
 * #heal()}.
 *
 * <p>Membership changes ({@link #addMember(MemberId)}, {@link #removeMember(MemberId)}, {@link
 * #restart(MemberId)}) are seen by all members at once, as if failure detection converged
 * instantly; MEMBER values of a removed or restarted member disappear everywhere immediately.
 *
 * <p>Last-writer-wins timestamps come from the clock given to the constructor. A member never
 * writes with a timestamp lower than or equal to the one it already holds for that key, so its own
 * write always wins locally even when the clock stands still.
 *
 * <p>All methods are thread-safe. Listener callbacks are handed to the subscriber's executor after
 * the cluster's internal lock is released.
 */
@NullMarked
public final class InMemoryGossipCluster {

  private final Object lock = new Object();
  private final LongSupplier clock;
  private final Map<MemberId, Member> members = new LinkedHashMap<>();
  private final Set<Set<MemberId>> disconnected = new HashSet<>();
  private int nextRun;

  /** Creates a cluster whose last-writer-wins timestamps come from the system clock. */
  public InMemoryGossipCluster() {
    this(System::currentTimeMillis);
  }

  /** Creates a cluster whose last-writer-wins timestamps come from the given clock. */
  public InMemoryGossipCluster(final LongSupplier clock) {
    this.clock = clock;
  }

  /**
   * Adds a member and returns its gossiper.
   *
   * @throws IllegalStateException if the member is already part of the cluster
   */
  public ClusterGossiper addMember(final MemberId id) {
    synchronized (lock) {
      if (members.containsKey(id)) {
        throw new IllegalStateException("Member " + id + " is already part of the cluster");
      }
      final var member = new Member(id, nextRun++);
      members.put(id, member);
      return member;
    }
  }

  /**
   * Returns the gossiper of the current run of a member.
   *
   * @throws IllegalStateException if the member is not part of the cluster
   */
  public ClusterGossiper member(final MemberId id) {
    synchronized (lock) {
      return requireMember(id);
    }
  }

  /**
   * Removes a member. Its gossiper stops working, and its MEMBER values disappear from every other
   * member, whose listeners are notified with an empty value. GLOBAL values it wrote survive on the
   * members that already received them.
   */
  public void removeMember(final MemberId id) {
    final var notifications = new ArrayList<Runnable>();
    synchronized (lock) {
      final var member = requireMember(id);
      member.alive = false;
      members.remove(id);
      members.values().forEach(other -> other.forgetMember(id, notifications));
      disconnected.removeIf(link -> link.contains(id));
    }
    notifications.forEach(Runnable::run);
  }

  /**
   * Restarts a member: removes it as {@link #removeMember(MemberId)} does and adds a new, empty run
   * under the same id. The returned gossiper replaces the previous one, which stops working. Keys
   * and subscriptions of the previous run are gone.
   */
  public ClusterGossiper restart(final MemberId id) {
    removeMember(id);
    return addMember(id);
  }

  /** Stops state from moving between two members until {@link #heal()} is called. */
  public void disconnect(final MemberId first, final MemberId second) {
    synchronized (lock) {
      requireMember(first);
      requireMember(second);
      disconnected.add(Set.of(first, second));
    }
  }

  /** Disconnects a member from every other current member. */
  public void isolate(final MemberId id) {
    synchronized (lock) {
      requireMember(id);
      members.keySet().stream()
          .filter(other -> !other.equals(id))
          .forEach(other -> disconnected.add(Set.of(id, other)));
    }
  }

  /** Reconnects all members. */
  public void heal() {
    synchronized (lock) {
      disconnected.clear();
    }
  }

  /**
   * Exchanges the full state of two connected members in both directions.
   *
   * @throws IllegalStateException if the members are disconnected
   */
  public void exchange(final MemberId first, final MemberId second) {
    final var notifications = new ArrayList<Runnable>();
    synchronized (lock) {
      final var a = requireMember(first);
      final var b = requireMember(second);
      if (disconnected.contains(Set.of(first, second))) {
        throw new IllegalStateException(
            "Members " + first + " and " + second + " are disconnected");
      }
      exchange(a, b, notifications);
    }
    notifications.forEach(Runnable::run);
  }

  /**
   * Exchanges state between all connected pairs of members until nothing changes anymore.
   *
   * @throws IllegalStateException if the state does not stop changing, which means a merge strategy
   *     is not idempotent, commutative and associative
   */
  public void sync() {
    final var notifications = new ArrayList<Runnable>();
    synchronized (lock) {
      final var current = List.copyOf(members.values());
      final int maxRounds = current.size() * current.size() + 1;
      boolean changed = true;
      for (int round = 0; changed; round++) {
        if (round > maxRounds) {
          throw new IllegalStateException(
              "Expected gossip state to converge within "
                  + maxRounds
                  + " rounds, but it kept changing; check that every merge strategy is"
                  + " commutative, associative and idempotent");
        }
        changed = false;
        for (int i = 0; i < current.size(); i++) {
          for (int j = i + 1; j < current.size(); j++) {
            final var a = current.get(i);
            final var b = current.get(j);
            if (!disconnected.contains(Set.of(a.id, b.id))) {
              changed |= exchange(a, b, notifications);
            }
          }
        }
      }
    }
    notifications.forEach(Runnable::run);
  }

  private boolean exchange(final Member a, final Member b, final List<Runnable> notifications) {
    final boolean changedB = b.receive(a, notifications);
    final boolean changedA = a.receive(b, notifications);
    return changedA || changedB;
  }

  private Member requireMember(final MemberId id) {
    final var member = members.get(id);
    if (member == null) {
      throw new IllegalStateException("Member " + id + " is not part of the cluster");
    }
    return member;
  }

  private static boolean isLastWriterWins(final MergeStrategy<?> strategy) {
    return strategy instanceof LastWriterWins<?>;
  }

  private static <T> Optional<T> tryDecode(final GossipKey<T> key, final byte[] bytes) {
    try {
      return Optional.of(key.codec().decode(bytes));
    } catch (final RuntimeException e) {
      return Optional.empty();
    }
  }

  /** A GLOBAL value as held by one member, with the metadata needed to merge it. */
  private record GlobalEntry(
      byte[] bytes, boolean lastWriterWins, long timestamp, @Nullable MemberId writer) {

    boolean isNewerThan(final GlobalEntry other) {
      if (timestamp != other.timestamp) {
        return timestamp > other.timestamp;
      }
      if (writer == null || other.writer == null) {
        return false;
      }
      return MemberId.ID_COMPARATOR.compare(writer, other.writer) > 0;
    }
  }

  /** One member's copy of a MEMBER key, tagged with the run and version it was written in. */
  private record MemberEntry(int run, long version, byte[] bytes) {}

  private record GlobalSubscription<T>(
      GlobalKey<T> key, Executor executor, GlobalListener<T> listener, Member owner)
      implements Subscription {

    @Override
    public void close() {
      owner.globalSubscriptions.remove(this);
    }

    void notify(final T value, final List<Runnable> notifications) {
      notifications.add(
          () ->
              executor.execute(
                  () -> {
                    if (owner.globalSubscriptions.contains(this)) {
                      listener.onChange(value);
                    }
                  }));
    }
  }

  private record MemberSubscription<T>(
      MemberKey<T> key, Executor executor, MemberListener<T> listener, Member owner)
      implements Subscription {

    @Override
    public void close() {
      owner.memberSubscriptions.remove(this);
    }

    void notify(
        final MemberId member, final Optional<T> value, final List<Runnable> notifications) {
      notifications.add(
          () ->
              executor.execute(
                  () -> {
                    if (owner.memberSubscriptions.contains(this)) {
                      listener.onChange(member, value);
                    }
                  }));
    }
  }

  private final class Member implements ClusterGossiper {

    private final MemberId id;
    private final Map<String, GossipKey<?>> registry = new HashMap<>();
    private final Map<String, GlobalEntry> globals = new HashMap<>();
    private final Map<MemberId, Map<String, MemberEntry>> memberValues = new HashMap<>();
    private final List<GlobalSubscription<?>> globalSubscriptions = new CopyOnWriteArrayList<>();
    private final List<MemberSubscription<?>> memberSubscriptions = new CopyOnWriteArrayList<>();
    private final int run;
    private long nextVersion;
    private boolean alive = true;

    private Member(final MemberId id, final int run) {
      this.id = id;
      this.run = run;
    }

    @Override
    public <T> void put(final GlobalKey<T> key, final T value) {
      final var notifications = new ArrayList<Runnable>();
      synchronized (lock) {
        requireAlive();
        register(key);
        putGlobal(key, value, notifications);
      }
      notifications.forEach(Runnable::run);
    }

    @Override
    public <T> void update(final GlobalKey<T> key, final UnaryOperator<T> updater) {
      final var notifications = new ArrayList<Runnable>();
      synchronized (lock) {
        requireAlive();
        register(key);
        putGlobal(key, updater.apply(currentGlobal(key)), notifications);
      }
      notifications.forEach(Runnable::run);
    }

    @Override
    public <T> T get(final GlobalKey<T> key) {
      synchronized (lock) {
        requireAlive();
        register(key);
        return currentGlobal(key);
      }
    }

    @Override
    public <T> void put(final MemberKey<T> key, final T value) {
      final var notifications = new ArrayList<Runnable>();
      synchronized (lock) {
        requireAlive();
        register(key);
        final var bytes = key.codec().encode(value);
        final var own = memberValues.computeIfAbsent(id, ignored -> new HashMap<>());
        final var previous = own.get(key.name());
        own.put(key.name(), new MemberEntry(run, ++nextVersion, bytes));
        if (previous == null || !Arrays.equals(previous.bytes(), bytes)) {
          notifyMember(key.name(), id, bytes, notifications);
        }
      }
      notifications.forEach(Runnable::run);
    }

    @Override
    public <T> Optional<T> get(final MemberKey<T> key, final MemberId member) {
      synchronized (lock) {
        requireAlive();
        register(key);
        final var entry = memberValues.getOrDefault(member, Map.of()).get(key.name());
        return entry == null ? Optional.empty() : tryDecode(key, entry.bytes());
      }
    }

    @Override
    public <T> Map<MemberId, T> getAll(final MemberKey<T> key) {
      synchronized (lock) {
        requireAlive();
        register(key);
        final var result = new HashMap<MemberId, T>();
        memberValues.forEach(
            (member, values) -> {
              final var entry = values.get(key.name());
              if (entry != null) {
                tryDecode(key, entry.bytes()).ifPresent(value -> result.put(member, value));
              }
            });
        return Map.copyOf(result);
      }
    }

    @Override
    public <T> Subscription subscribe(
        final GlobalKey<T> key, final Executor executor, final GlobalListener<T> listener) {
      final var notifications = new ArrayList<Runnable>();
      final GlobalSubscription<T> subscription;
      synchronized (lock) {
        requireAlive();
        register(key);
        subscription = new GlobalSubscription<>(key, executor, listener, this);
        globalSubscriptions.add(subscription);
        subscription.notify(currentGlobal(key), notifications);
      }
      notifications.forEach(Runnable::run);
      return subscription;
    }

    @Override
    public <T> Subscription subscribe(
        final MemberKey<T> key, final Executor executor, final MemberListener<T> listener) {
      final var notifications = new ArrayList<Runnable>();
      final MemberSubscription<T> subscription;
      synchronized (lock) {
        requireAlive();
        register(key);
        subscription = new MemberSubscription<>(key, executor, listener, this);
        memberSubscriptions.add(subscription);
        memberValues.forEach(
            (member, values) -> {
              final var entry = values.get(key.name());
              if (entry != null) {
                tryDecode(key, entry.bytes())
                    .ifPresent(
                        value -> subscription.notify(member, Optional.of(value), notifications));
              }
            });
      }
      notifications.forEach(Runnable::run);
      return subscription;
    }

    private void requireAlive() {
      if (!alive) {
        throw new IllegalStateException(
            "This gossiper belongs to a run of member " + id + " that is no longer in the cluster");
      }
    }

    private void register(final GossipKey<?> key) {
      final var existing = registry.putIfAbsent(key.name(), key);
      if (existing != null && !isCompatible(existing, key)) {
        throw new IllegalStateException(
            "Expected key '"
                + key.name()
                + "' to be used as registered ("
                + describe(existing)
                + "), but it was used as "
                + describe(key));
      }
    }

    private boolean isCompatible(final GossipKey<?> existing, final GossipKey<?> key) {
      return switch (existing) {
        case final MemberKey<?> ignored -> key instanceof MemberKey<?>;
        case final GlobalKey<?> global ->
            key instanceof final GlobalKey<?> other
                && isLastWriterWins(global.mergeStrategy())
                    == isLastWriterWins(other.mergeStrategy());
      };
    }

    private String describe(final GossipKey<?> key) {
      return switch (key) {
        case final MemberKey<?> ignored -> "MEMBER";
        case final GlobalKey<?> global ->
            isLastWriterWins(global.mergeStrategy())
                ? "GLOBAL, last writer wins"
                : "GLOBAL, custom merge";
      };
    }

    private <T> T currentGlobal(final GlobalKey<T> key) {
      final var entry = globals.get(key.name());
      if (entry == null) {
        return key.defaultValue();
      }
      return tryDecode(key, entry.bytes()).orElse(key.defaultValue());
    }

    private <T> void putGlobal(
        final GlobalKey<T> key, final T value, final List<Runnable> notifications) {
      final var current = globals.get(key.name());
      final GlobalEntry next;
      switch (key.mergeStrategy()) {
        case final LastWriterWins<T> ignored -> {
          final long timestamp =
              current == null
                  ? clock.getAsLong()
                  : Math.max(clock.getAsLong(), current.timestamp() + 1);
          next = new GlobalEntry(key.codec().encode(value), true, timestamp, id);
        }
        case final Merging<T> merging -> {
          final var currentValue =
              current == null ? Optional.<T>empty() : tryDecode(key, current.bytes());
          final T merged = currentValue.map(c -> merging.merge().apply(c, value)).orElse(value);
          next = new GlobalEntry(key.codec().encode(merged), false, 0, null);
        }
      }
      globals.put(key.name(), next);
      if (current == null || !Arrays.equals(current.bytes(), next.bytes())) {
        notifyGlobal(key.name(), notifications);
      }
    }

    /** Merges the state of {@code other} into this member. Returns whether anything changed. */
    private boolean receive(final Member other, final List<Runnable> notifications) {
      boolean changed = false;
      for (final var remote : other.globals.entrySet()) {
        changed |= receiveGlobal(remote.getKey(), remote.getValue(), notifications);
      }
      for (final var remote : other.memberValues.entrySet()) {
        final var owner = members.get(remote.getKey());
        if (owner == null || owner == this) {
          continue;
        }
        for (final var entry : remote.getValue().entrySet()) {
          changed |= receiveMember(owner, entry.getKey(), entry.getValue(), notifications);
        }
      }
      return changed;
    }

    private boolean receiveGlobal(
        final String name, final GlobalEntry remote, final List<Runnable> notifications) {
      final var current = globals.get(name);
      final GlobalEntry next;
      if (remote.lastWriterWins()) {
        if (current != null && !remote.isNewerThan(current)) {
          return false;
        }
        next = remote;
      } else {
        // a custom merge cannot run without the key's merge function, so values for keys not
        // registered here yet are dropped; the next exchange delivers them again
        if (!(registry.get(name) instanceof final GlobalKey<?> key)) {
          return false;
        }
        final var merged = mergeCustom(key, current, remote);
        if (merged == null) {
          return false;
        }
        next = merged;
      }
      if (current != null && Arrays.equals(current.bytes(), next.bytes())) {
        return false;
      }
      globals.put(name, next);
      notifyGlobal(name, notifications);
      return true;
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
      return new GlobalEntry(key.codec().encode(merged), false, 0, null);
    }

    private boolean receiveMember(
        final Member owner,
        final String name,
        final MemberEntry remote,
        final List<Runnable> notifications) {
      if (remote.run() != owner.run) {
        return false;
      }
      final var values = memberValues.computeIfAbsent(owner.id, ignored -> new HashMap<>());
      final var current = values.get(name);
      if (current != null && current.version() >= remote.version()) {
        return false;
      }
      values.put(name, remote);
      if (current == null || !Arrays.equals(current.bytes(), remote.bytes())) {
        notifyMember(name, owner.id, remote.bytes(), notifications);
      }
      return true;
    }

    private void forgetMember(final MemberId member, final List<Runnable> notifications) {
      final var values = memberValues.remove(member);
      if (values == null) {
        return;
      }
      for (final var name : values.keySet()) {
        for (final var subscription : memberSubscriptions) {
          if (subscription.key().name().equals(name)) {
            subscription.notify(member, Optional.empty(), notifications);
          }
        }
      }
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
        final byte[] bytes,
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
        final byte[] bytes,
        final List<Runnable> notifications) {
      subscription.notify(member, tryDecode(subscription.key(), bytes), notifications);
    }
  }
}
