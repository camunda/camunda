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
import io.camunda.zeebe.cluster.gossip.MergeStrategy.LastWriterWins;
import io.camunda.zeebe.cluster.gossip.MergeStrategy.Merging;
import io.camunda.zeebe.cluster.gossip.Subscription;
import java.lang.System.Logger.Level;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
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
 * <p>All methods are thread-safe. Each listener's callbacks run one at a time on the subscriber's
 * executor, in the order the changes were applied, even when the executor is multi-threaded or a
 * listener writes from a direct executor.
 */
@NullMarked
public final class InMemoryGossipCluster {

  private static final System.Logger LOG = System.getLogger(InMemoryGossipCluster.class.getName());

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
    synchronized (lock) {
      requireMember(id).stop();
      members.remove(id);
      members.values().forEach(other -> other.forgetMember(id));
      disconnected.removeIf(link -> link.contains(id));
    }
  }

  /**
   * Restarts a member: removes it as {@link #removeMember(MemberId)} does and adds a new, empty run
   * under the same id. The returned gossiper replaces the previous one, which stops working. Keys
   * and subscriptions of the previous run are gone.
   */
  public ClusterGossiper restart(final MemberId id) {
    synchronized (lock) {
      removeMember(id);
      return addMember(id);
    }
  }

  /** Stops state from moving between two members until {@link #heal()} is called. */
  public void disconnect(final MemberId first, final MemberId second) {
    synchronized (lock) {
      disconnected.add(link(first, second));
    }
  }

  /** Disconnects a member from every other current member. */
  public void isolate(final MemberId id) {
    synchronized (lock) {
      requireMember(id);
      members.keySet().stream()
          .filter(other -> !other.equals(id))
          .forEach(other -> disconnected.add(link(id, other)));
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
    synchronized (lock) {
      if (disconnected.contains(link(first, second))) {
        throw new IllegalStateException(
            "Members " + first + " and " + second + " are disconnected");
      }
      exchange(requireMember(first), requireMember(second));
    }
  }

  /**
   * Exchanges state between all connected pairs of members until nothing changes anymore.
   *
   * @throws IllegalStateException if the state does not stop changing, which means a merge strategy
   *     is not idempotent, commutative and associative
   */
  public void sync() {
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
            if (!disconnected.contains(link(a.id, b.id))) {
              changed |= exchange(a, b);
            }
          }
        }
      }
    }
  }

  private boolean exchange(final Member a, final Member b) {
    final boolean changedB = b.receive(a);
    final boolean changedA = a.receive(b);
    return changedA || changedB;
  }

  private Member requireMember(final MemberId id) {
    final var member = members.get(id);
    if (member == null) {
      throw new IllegalStateException("Member " + id + " is not part of the cluster");
    }
    return member;
  }

  private Set<MemberId> link(final MemberId first, final MemberId second) {
    requireMember(first);
    requireMember(second);
    if (first.equals(second)) {
      throw new IllegalArgumentException("Expected two different members, but got " + first);
    }
    return Set.of(first, second);
  }

  private static <T> Optional<T> tryDecode(final GossipKey<T> key, final byte[] bytes) {
    try {
      return Optional.of(key.codec().decode(bytes));
    } catch (final RuntimeException e) {
      LOG.log(Level.WARNING, "Failed to decode the value of gossip key '" + key.name() + "'", e);
      return Optional.empty();
    }
  }

  private static String describe(final GossipKey<?> key) {
    return switch (key) {
      case final MemberKey<?> ignored -> "MEMBER";
      case final GlobalKey<?> global ->
          global.mergeStrategy() instanceof LastWriterWins<?>
              ? "GLOBAL, last writer wins"
              : "GLOBAL, custom merge";
    };
  }

  /** A GLOBAL value as held by one member, with the metadata its merge strategy needs. */
  private sealed interface GlobalEntry {
    byte[] bytes();
  }

  /**
   * A last-writer-wins write: the higher timestamp wins, ties go to the higher writer id and then
   * to the greater encoded value. The last tie-break makes the order total, so members converge
   * even when a restarted writer reuses a timestamp of its previous run for a different value.
   */
  private record LastWrite(byte[] bytes, long timestamp, MemberId writer) implements GlobalEntry {

    boolean isNewerThan(final LastWrite other) {
      if (timestamp != other.timestamp) {
        return timestamp > other.timestamp;
      }
      final int byWriter = MemberId.ID_COMPARATOR.compare(writer, other.writer);
      return byWriter != 0 ? byWriter > 0 : Arrays.compareUnsigned(bytes, other.bytes) > 0;
    }
  }

  /** The result of a custom merge, which carries all its ordering inside the value. */
  private record Merged(byte[] bytes) implements GlobalEntry {}

  /** One member's copy of a MEMBER key, tagged with the run and version it was written in. */
  private record MemberEntry(int run, long version, byte[] bytes) {}

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
      synchronized (lock) {
        register(key);
        putGlobal(key, value);
      }
    }

    @Override
    public <T> void update(final GlobalKey<T> key, final UnaryOperator<T> updater) {
      synchronized (lock) {
        register(key);
        putGlobal(key, updater.apply(currentGlobal(key)));
      }
    }

    @Override
    public <T> T get(final GlobalKey<T> key) {
      synchronized (lock) {
        register(key);
        return currentGlobal(key);
      }
    }

    @Override
    public <T> void put(final MemberKey<T> key, final T value) {
      synchronized (lock) {
        register(key);
        final var bytes = key.codec().encode(value);
        final var own = memberValues.computeIfAbsent(id, ignored -> new HashMap<>());
        final var previous = own.put(key.name(), new MemberEntry(run, ++nextVersion, bytes));
        notifyMember(key.name(), id, previous == null ? null : previous.bytes(), bytes);
      }
    }

    @Override
    public <T> Optional<T> get(final MemberKey<T> key, final MemberId member) {
      synchronized (lock) {
        register(key);
        final var entry = memberValues.getOrDefault(member, Map.of()).get(key.name());
        return entry == null ? Optional.empty() : tryDecode(key, entry.bytes());
      }
    }

    @Override
    public <T> Map<MemberId, T> getAll(final MemberKey<T> key) {
      synchronized (lock) {
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
      synchronized (lock) {
        register(key);
        final var subscription = new GlobalSubscription<>(key, executor, listener);
        globalSubscriptions.add(subscription);
        subscription.notifyChanged();
        subscription.deliver();
        return subscription;
      }
    }

    @Override
    public <T> Subscription subscribe(
        final MemberKey<T> key, final Executor executor, final MemberListener<T> listener) {
      synchronized (lock) {
        register(key);
        final var subscription = new MemberSubscription<>(key, executor, listener);
        memberSubscriptions.add(subscription);
        // a listener on a direct executor may write while being replayed to, so iterate a copy
        List.copyOf(memberValues.entrySet())
            .forEach(
                values -> {
                  final var entry = values.getValue().get(key.name());
                  if (entry != null) {
                    subscription.notifyChanged(values.getKey(), null, entry.bytes());
                  }
                });
        subscription.deliver();
        return subscription;
      }
    }

    /** Ends this run: the gossiper stops working and no further callbacks reach its listeners. */
    private void stop() {
      alive = false;
      globalSubscriptions.forEach(GlobalSubscription::close);
      memberSubscriptions.forEach(MemberSubscription::close);
    }

    /** Registers the key on first use; every public operation starts here. */
    private void register(final GossipKey<?> key) {
      if (!alive) {
        throw new IllegalStateException(
            "This gossiper belongs to a run of member " + id + " that is no longer in the cluster");
      }
      final var existing = registry.putIfAbsent(key.name(), key);
      if (existing != null && !describe(existing).equals(describe(key))) {
        throw new IllegalStateException(
            "Expected key '"
                + key.name()
                + "' to be used as registered ("
                + describe(existing)
                + "), but it was used as "
                + describe(key));
      }
    }

    private <T> T currentGlobal(final GlobalKey<T> key) {
      final var entry = globals.get(key.name());
      return entry == null
          ? key.defaultValue()
          : tryDecode(key, entry.bytes()).orElse(key.defaultValue());
    }

    private <T> void putGlobal(final GlobalKey<T> key, final T value) {
      final var current = globals.get(key.name());
      final GlobalEntry next =
          switch (key.mergeStrategy()) {
            case final LastWriterWins<T> ignored -> {
              final long now = clock.getAsLong();
              final long timestamp =
                  current instanceof final LastWrite last
                      ? Math.max(now, last.timestamp() + 1)
                      : now;
              yield new LastWrite(key.codec().encode(value), timestamp, id);
            }
            case final Merging<T> merging -> {
              final var merged =
                  current == null
                      ? value
                      : tryDecode(key, current.bytes())
                          .map(c -> merging.merge().apply(c, value))
                          .orElse(value);
              yield new Merged(key.codec().encode(merged));
            }
          };
      storeGlobal(key.name(), current, next);
    }

    /** Merges the state of {@code other} into this member. Returns whether anything changed. */
    private boolean receive(final Member other) {
      boolean changed = false;
      for (final var remote : other.globals.entrySet()) {
        changed |= receiveGlobal(remote.getKey(), remote.getValue());
      }
      for (final var remote : other.memberValues.entrySet()) {
        final var owner = members.get(remote.getKey());
        if (owner == null || owner == this) {
          continue;
        }
        for (final var entry : remote.getValue().entrySet()) {
          changed |= receiveMember(owner, entry.getKey(), entry.getValue());
        }
      }
      return changed;
    }

    private boolean receiveGlobal(final String name, final GlobalEntry remote) {
      final var current = globals.get(name);
      final @Nullable GlobalEntry next =
          switch (remote) {
            case final LastWrite write ->
                current instanceof final LastWrite last && !write.isNewerThan(last) ? null : write;
            // a custom merge cannot run without the key's merge function, so values for keys not
            // registered here yet are dropped; the next exchange delivers them again
            case final Merged merged ->
                registry.get(name) instanceof final GlobalKey<?> key
                    ? mergeCustom(key, current, merged)
                    : null;
          };
      return next != null && storeGlobal(name, current, next);
    }

    private <T> @Nullable GlobalEntry mergeCustom(
        final GlobalKey<T> key, final @Nullable GlobalEntry current, final Merged remote) {
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
      return new Merged(key.codec().encode(merged));
    }

    /**
     * Stores a GLOBAL entry and notifies if its value changed. Returns whether the entry changed,
     * which includes a newer last-writer-wins timestamp for the same value: {@link #sync()} must
     * spread that too, or a later write on a member still holding the older timestamp could lose.
     */
    private boolean storeGlobal(
        final String name, final @Nullable GlobalEntry current, final GlobalEntry next) {
      globals.put(name, next);
      if (current != null && Arrays.equals(current.bytes(), next.bytes())) {
        return next instanceof final LastWrite write
            && current instanceof final LastWrite last
            && write.isNewerThan(last);
      }
      final var affected =
          globalSubscriptions.stream()
              .filter(subscription -> subscription.key.name().equals(name))
              .toList();
      affected.forEach(GlobalSubscription::notifyChanged);
      affected.forEach(GlobalSubscription::deliver);
      return true;
    }

    private boolean receiveMember(final Member owner, final String name, final MemberEntry remote) {
      if (remote.run() != owner.run) {
        return false;
      }
      final var values = memberValues.computeIfAbsent(owner.id, ignored -> new HashMap<>());
      final var current = values.get(name);
      if (current != null && current.version() >= remote.version()) {
        return false;
      }
      values.put(name, remote);
      notifyMember(name, owner.id, current == null ? null : current.bytes(), remote.bytes());
      return true;
    }

    private void forgetMember(final MemberId member) {
      final var values = memberValues.remove(member);
      if (values != null) {
        values.forEach((name, entry) -> notifyMember(name, member, entry.bytes(), null));
      }
    }

    private void notifyMember(
        final String name,
        final MemberId member,
        final byte @Nullable [] before,
        final byte @Nullable [] after) {
      if (Arrays.equals(before, after)) {
        return;
      }
      final var affected =
          memberSubscriptions.stream()
              .filter(subscription -> subscription.key.name().equals(name))
              .toList();
      affected.forEach(subscription -> subscription.notifyChanged(member, before, after));
      affected.forEach(MemberSubscription::deliver);
    }

    private final class GlobalSubscription<T> implements Subscription {

      private final GlobalKey<T> key;
      private final SerialCallbacks callbacks;
      private final GlobalListener<T> listener;

      private GlobalSubscription(
          final GlobalKey<T> key, final Executor executor, final GlobalListener<T> listener) {
        this.key = key;
        callbacks = new SerialCallbacks(executor);
        this.listener = listener;
      }

      void notifyChanged() {
        final T value = currentGlobal(key);
        callbacks.enqueue(
            () -> {
              if (globalSubscriptions.contains(this)) {
                listener.onChange(value);
              }
            });
      }

      void deliver() {
        callbacks.deliver();
      }

      @Override
      public void close() {
        globalSubscriptions.remove(this);
      }
    }

    private final class MemberSubscription<T> implements Subscription {

      private final MemberKey<T> key;
      private final SerialCallbacks callbacks;
      private final MemberListener<T> listener;

      private MemberSubscription(
          final MemberKey<T> key, final Executor executor, final MemberListener<T> listener) {
        this.key = key;
        callbacks = new SerialCallbacks(executor);
        this.listener = listener;
      }

      /** Notifies the listener, unless the value neither was nor is readable. */
      void notifyChanged(
          final MemberId member, final byte @Nullable [] before, final byte @Nullable [] after) {
        final Optional<T> value = after == null ? Optional.empty() : tryDecode(key, after);
        if (value.isEmpty() && (before == null || tryDecode(key, before).isEmpty())) {
          return;
        }
        callbacks.enqueue(
            () -> {
              if (memberSubscriptions.contains(this)) {
                listener.onChange(member, value);
              }
            });
      }

      void deliver() {
        callbacks.deliver();
      }

      @Override
      public void close() {
        memberSubscriptions.remove(this);
      }
    }
  }

  /**
   * Runs one subscription's callbacks one at a time, in the order they were added, on the
   * subscriber's executor. Without this, a multi-threaded executor could run an older callback
   * last, and a listener that writes from a direct executor would deliver the resulting change to
   * other listeners before the change that triggered it.
   */
  private final class SerialCallbacks {

    private final Executor executor;
    // guarded by the cluster's lock
    private final Queue<Runnable> pending = new ArrayDeque<>();
    private boolean draining;

    private SerialCallbacks(final Executor executor) {
      this.executor = executor;
    }

    /** Queues a callback without running it. Must be called while holding the cluster's lock. */
    void enqueue(final Runnable callback) {
      pending.add(callback);
    }

    /**
     * Starts running queued callbacks unless that is already under way. Must be called while
     * holding the cluster's lock, after a change was queued for every listener it concerns, so a
     * listener that writes in response cannot overtake the change for the other listeners.
     */
    void deliver() {
      if (!draining && !pending.isEmpty()) {
        draining = true;
        try {
          executor.execute(this::drain);
        } catch (final RuntimeException e) {
          // the queued callbacks stay queued and are handed over again with the next change
          draining = false;
          LOG.log(Level.WARNING, "Failed to hand gossip notifications to an executor", e);
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
          LOG.log(Level.WARNING, "A gossip listener failed to handle a notification", e);
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
