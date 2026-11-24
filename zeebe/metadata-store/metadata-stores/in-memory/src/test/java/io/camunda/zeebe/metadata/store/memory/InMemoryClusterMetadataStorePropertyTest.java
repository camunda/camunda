/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.metadata.store.api.Batch;
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException;
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.ConditionFailedException;
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.InvalidArgumentException;
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.NamespaceAlreadyExistsException;
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.NamespaceNotFoundException;
import io.camunda.zeebe.metadata.store.api.Condition;
import io.camunda.zeebe.metadata.store.api.KeyEvent;
import io.camunda.zeebe.metadata.store.api.KeyEvent.ChangeType;
import io.camunda.zeebe.metadata.store.api.KeyValue;
import io.camunda.zeebe.metadata.store.api.Namespace;
import io.camunda.zeebe.metadata.store.api.NamespaceChange;
import io.camunda.zeebe.metadata.store.api.NamespaceScope;
import io.camunda.zeebe.metadata.store.api.StoreKey;
import io.camunda.zeebe.metadata.store.api.StoreValue;
import io.camunda.zeebe.metadata.store.api.Subscription;
import io.camunda.zeebe.metadata.store.api.Subscription.CloseReason;
import io.camunda.zeebe.metadata.store.api.WatchListener;
import io.camunda.zeebe.metadata.store.api.Write;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.time.Duration;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import net.jqwik.api.statistics.Statistics;
import org.jspecify.annotations.Nullable;

/**
 * Runs random sequences of writes, namespace changes and subscriptions against the in-memory store
 * and against a plain model of the store, and requires the same outcome of every call, the same
 * state after every call, and the same events, in the same order, on every subscription.
 *
 * <p>The keys come from a small pool, with segments such as {@code a} and {@code ab}, so that
 * writes collide, conditions both hold and fail, and prefixes that share characters but not
 * segments meet. Listeners run on a shared thread pool, so the order of events depends on the
 * store's own serialization, not on the executor.
 */
final class InMemoryClusterMetadataStorePropertyTest {

  private static final List<Namespace> NAMESPACES =
      List.of(Namespace.cluster("config"), Namespace.cluster("failover"), Namespace.tenant("t1"));
  private static final List<String> SEGMENTS = List.of("a", "ab");
  private static final List<String> KEYS =
      Stream.concat(
              SEGMENTS.stream(),
              SEGMENTS.stream().flatMap(first -> SEGMENTS.stream().map(s -> first + "." + s)))
          .toList();
  private static final List<Namespace> INITIAL_NAMESPACES =
      List.of(NAMESPACES.get(0), NAMESPACES.get(2));
  private static final Comparator<Namespace> NAMESPACE_ORDER =
      Comparator.comparing(Namespace::scope).thenComparing(Namespace::name);

  private static final List<String> COVERED_KINDS =
      List.of(
          "ADD",
          "REPLACE",
          "REMOVE",
          "namespace CREATED",
          "namespace DELETED",
          "closed NAMESPACE_DELETED",
          "closed STORE_CLOSED");

  private static ActorScheduler scheduler;
  private static ExecutorService listenerExecutor;

  @BeforeContainer
  static void startScheduler() {
    scheduler =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(1)
            .setIoBoundActorThreadCount(1)
            .build();
    scheduler.start();
    listenerExecutor = Executors.newFixedThreadPool(4);
  }

  @AfterContainer
  static void stopScheduler() throws Exception {
    listenerExecutor.shutdownNow();
    scheduler.stop().get(10, TimeUnit.SECONDS);
  }

  @Property(tries = 500)
  void shouldBehaveLikeTheModel(
      @ForAll("initialWatches") final List<StoreKey> initialWatches,
      @ForAll("operations") final List<Op> operations) {
    // given
    final var store = new InMemoryClusterMetadataStore("8.11.0", InstantSource.system());
    scheduler.submitActor(store).join(10, TimeUnit.SECONDS);
    final var model = new Model();
    var closed = false;

    try {
      // most writes and watches need a namespace, and most events a watch registered before
      // them, so each try starts with both
      for (final var namespace : INITIAL_NAMESPACES) {
        run(store, model, new Single(new CreateNamespaceOp(namespace)));
      }
      for (final var scope : NamespaceScope.values()) {
        run(store, model, new WatchNamespace(scope));
      }
      for (final var path : initialWatches) {
        run(store, model, new Watch(path));
      }

      // when
      for (final var operation : operations) {
        run(store, model, operation);
        // then
        assertSameState(store, model);
      }

      store.closeAsync().join(10, TimeUnit.SECONDS);
      closed = true;
      model.closeStore();

      // then
      for (final var watcher : model.watchers) {
        awaitEvents(watcher);
      }
      checkCoverage(model);
    } finally {
      if (!closed) {
        store.closeAsync().join(10, TimeUnit.SECONDS);
      }
    }
  }

  private static void run(
      final InMemoryClusterMetadataStore store, final Model model, final Op operation) {
    switch (operation) {
      case final Single single -> {
        final var write = single.write().toWrite();
        final var expected = model.batch(List.of(write));
        final var future =
            switch (write) {
              case final Write.Put put ->
                  put.condition() instanceof Condition.None
                      ? store.put(put.key(), put.value())
                      : store.put(put.key(), put.value(), put.condition());
              case final Write.Delete delete ->
                  delete.condition() instanceof Condition.None
                      ? store.delete(delete.key())
                      : store.delete(delete.key(), delete.condition());
              case final Write.DeletePrefix deletePrefix ->
                  store.deletePrefix(deletePrefix.prefix());
              case final Write.CreateNamespace create -> store.createNamespace(create.namespace());
              case final Write.Check check ->
                  throw new IllegalArgumentException("A check is only part of a batch: " + check);
            };
        assertOutcome(future, expected, operation);
      }
      case final BatchOp batchOp -> {
        final var writes = batchOp.writes().stream().map(WriteOp::toWrite).toList();
        final var expected = model.batch(writes);
        final Batch batch;
        try {
          batch = new Batch(writes);
        } catch (final InvalidArgumentException e) {
          // a batch that breaks a rule needing no state cannot be built, so it never reaches the
          // store
          assertThat(expected)
              .as("outcome of %s", operation)
              .isEqualTo(InvalidArgumentException.class);
          return;
        }
        assertOutcome(store.batch(batch), expected, operation);
      }
      case final DeleteNamespace delete -> {
        final var expected = model.deleteNamespace(delete.namespace());
        assertOutcome(store.deleteNamespace(delete.namespace()), expected, operation);
      }
      case final Watch watch -> {
        final var watcher = new Watcher(watch.path(), null);
        try {
          watcher.subscription = store.watch(watch.path(), watcher.listener(), listenerExecutor);
        } catch (final NamespaceNotFoundException e) {
          assertThat(model.exists(watch.path().namespace()))
              .as("watch failed although the namespace exists: %s", operation)
              .isFalse();
          return;
        }
        assertThat(model.exists(watch.path().namespace()))
            .as("watch succeeded although the namespace does not exist: %s", operation)
            .isTrue();
        model.watchers.add(watcher);
      }
      case final WatchNamespace watch -> {
        final var watcher = new Watcher(null, watch.scope());
        watcher.subscription =
            store.watchNamespace(watch.scope(), watcher.listener(), listenerExecutor);
        model.watchers.add(watcher);
      }
      case final CloseSubscription close -> {
        final var active = model.watchers.stream().filter(w -> w.active).toList();
        if (active.isEmpty()) {
          return;
        }
        final var watcher = active.get(close.index() % active.size());
        // events still queued for a subscription that its consumer closes may be dropped
        awaitEvents(watcher);
        watcher.subscription.close();
        watcher.active = false;
      }
    }
  }

  private static void assertOutcome(
      final ActorFuture<?> future,
      final @Nullable Class<? extends ClusterMetadataStoreException> expected,
      final Op operation) {
    final var failure = failureOf(future);
    if (expected == null) {
      assertThat(failure).as("outcome of %s", operation).isNull();
    } else {
      assertThat(failure).as("outcome of %s", operation).isInstanceOf(expected);
    }
  }

  private static @Nullable Throwable failureOf(final ActorFuture<?> future) {
    Throwable failure;
    try {
      future.join(10, TimeUnit.SECONDS);
      return null;
    } catch (final Exception e) {
      // join rethrows the failure as it is, checked or not
      failure = e;
    }
    while (!(failure instanceof ClusterMetadataStoreException) && failure.getCause() != null) {
      failure = failure.getCause();
    }
    return failure;
  }

  private static void assertSameState(final InMemoryClusterMetadataStore store, final Model model) {
    for (final var namespace : NAMESPACES) {
      final var exists = model.exists(namespace);
      assertThat(store.exists(namespace).join(10, TimeUnit.SECONDS))
          .as("namespace %s exists", namespace)
          .isEqualTo(exists);
      if (exists) {
        assertThat(store.list(StoreKey.of(namespace, "")).join(10, TimeUnit.SECONDS))
            .as("keys of %s", namespace)
            .containsExactlyElementsOf(model.namespaces.get(namespace).values());
      }
    }
    for (final var scope : NamespaceScope.values()) {
      assertThat(store.list(scope).join(10, TimeUnit.SECONDS))
          .as("namespaces of scope %s", scope)
          .containsExactlyElementsOf(model.namespaces(scope));
    }
  }

  private static void awaitEvents(final Watcher watcher) {
    await("events of " + watcher)
        .atMost(Duration.ofSeconds(5))
        .pollDelay(Duration.ZERO)
        .pollInterval(Duration.ofMillis(1))
        .untilAsserted(
            () -> assertThat(watcher.received()).containsExactlyElementsOf(watcher.expected));
  }

  /**
   * Fails the property if too few tries reach an event of each kind: without enough of them, the
   * comparison with the model would pass without having checked those events at all.
   */
  private static void checkCoverage(final Model model) {
    final var seen =
        model.watchers.stream()
            .flatMap(watcher -> watcher.expected.stream())
            .map(InMemoryClusterMetadataStorePropertyTest::kindOf)
            .collect(Collectors.toSet());
    for (final var kind : COVERED_KINDS) {
      Statistics.label(kind).collect(seen.contains(kind));
      Statistics.label(kind).coverage(coverage -> coverage.check(true).percentage(p -> p >= 5));
    }
  }

  private static String kindOf(final Object event) {
    return switch (event) {
      case final KeyEvent keyEvent -> keyEvent.type().name();
      case final NamespaceChange namespaceChange -> "namespace " + namespaceChange.type().name();
      case final CloseReason reason -> "closed " + reason.name();
      default -> throw new IllegalArgumentException("Unexpected event " + event);
    };
  }

  private static boolean isAtOrUnder(final String key, final String prefix) {
    return key.equals(prefix) || key.startsWith(prefix + ".");
  }

  // --- generators ---

  @Provide
  Arbitrary<List<Op>> operations() {
    return Arbitraries.<Op>frequencyOf(
            Tuple.of(16, singleWrites().map(Single::new)),
            Tuple.of(4, writeOps().list().ofMinSize(1).ofMaxSize(4).map(BatchOp::new)),
            Tuple.of(4, keys().map(Watch::new)),
            Tuple.of(1, namespaces().map(DeleteNamespace::new)),
            Tuple.of(1, Arbitraries.of(NamespaceScope.class).map(WatchNamespace::new)),
            Tuple.of(1, Arbitraries.integers().between(0, 20).map(CloseSubscription::new)))
        .list()
        .ofMinSize(5)
        .ofMaxSize(50);
  }

  @Provide
  Arbitrary<List<StoreKey>> initialWatches() {
    return Combinators.combine(Arbitraries.of(INITIAL_NAMESPACES), Arbitraries.of(KEYS))
        .as(StoreKey::of)
        .list()
        .ofMinSize(1)
        .ofMaxSize(4);
  }

  private static Arbitrary<WriteOp> singleWrites() {
    return Arbitraries.<WriteOp>frequencyOf(
        Tuple.of(6, puts()),
        Tuple.of(2, deletes()),
        Tuple.of(1, keys().map(DeletePrefixOp::new)),
        Tuple.of(1, namespaces().map(CreateNamespaceOp::new)));
  }

  private static Arbitrary<WriteOp> writeOps() {
    return Arbitraries.<WriteOp>frequencyOf(
        Tuple.of(4, puts()),
        Tuple.of(2, deletes()),
        Tuple.of(1, keys().map(DeletePrefixOp::new)),
        Tuple.of(1, Combinators.combine(keys(), conditions(false)).as(CheckOp::new)),
        Tuple.of(1, namespaces().map(CreateNamespaceOp::new)));
  }

  private static Arbitrary<WriteOp> puts() {
    return Combinators.combine(keys(), values(), conditions(true)).as(PutOp::new);
  }

  private static Arbitrary<WriteOp> deletes() {
    return Combinators.combine(keys(), conditions(true)).as(DeleteOp::new);
  }

  private static Arbitrary<Namespace> namespaces() {
    // the namespaces that exist from the start are written more often
    return Arbitraries.frequency(
        Tuple.of(3, NAMESPACES.get(0)),
        Tuple.of(1, NAMESPACES.get(1)),
        Tuple.of(2, NAMESPACES.get(2)));
  }

  private static Arbitrary<StoreKey> keys() {
    return Combinators.combine(namespaces(), Arbitraries.of(KEYS)).as(StoreKey::of);
  }

  private static Arbitrary<Byte> values() {
    return Arbitraries.of((byte) 1, (byte) 2, (byte) 3);
  }

  private static Arbitrary<Cond> conditions(final boolean optional) {
    // most writes are unconditional, so that enough of them apply
    final var kinds =
        optional
            ? Arbitraries.frequency(
                Tuple.of(6, Cond.Kind.NONE),
                Tuple.of(1, Cond.Kind.ABSENT),
                Tuple.of(1, Cond.Kind.EXISTS),
                Tuple.of(1, Cond.Kind.EQUALS))
            : Arbitraries.of(Cond.Kind.ABSENT, Cond.Kind.EXISTS, Cond.Kind.EQUALS);
    return Combinators.combine(kinds, values()).as(Cond::new);
  }

  // --- operations ---

  sealed interface Op {}

  /** A write through the single-operation call of the API, not through {@code batch}. */
  record Single(WriteOp write) implements Op {}

  record BatchOp(List<WriteOp> writes) implements Op {}

  record DeleteNamespace(Namespace namespace) implements Op {}

  record Watch(StoreKey path) implements Op {}

  record WatchNamespace(NamespaceScope scope) implements Op {}

  /** Closes one of the open subscriptions, chosen by the index modulo their number. */
  record CloseSubscription(int index) implements Op {}

  sealed interface WriteOp {
    Write toWrite();
  }

  record PutOp(StoreKey key, byte value, Cond condition) implements WriteOp {
    @Override
    public Write toWrite() {
      return new Write.Put(key, StoreValue.of(new byte[] {value}), condition.toCondition());
    }
  }

  record DeleteOp(StoreKey key, Cond condition) implements WriteOp {
    @Override
    public Write toWrite() {
      return new Write.Delete(key, condition.toCondition());
    }
  }

  record DeletePrefixOp(StoreKey prefix) implements WriteOp {
    @Override
    public Write toWrite() {
      return new Write.DeletePrefix(prefix);
    }
  }

  record CheckOp(StoreKey key, Cond condition) implements WriteOp {
    @Override
    public Write toWrite() {
      return new Write.Check(key, condition.toCondition());
    }
  }

  record CreateNamespaceOp(Namespace namespace) implements WriteOp {
    @Override
    public Write toWrite() {
      return new Write.CreateNamespace(namespace);
    }
  }

  /** A condition that prints its expected bytes, so a shrunk failure is readable. */
  record Cond(Kind kind, byte expected) {

    Condition toCondition() {
      return switch (kind) {
        case NONE -> Condition.none();
        case ABSENT -> Condition.absent();
        case EXISTS -> Condition.exists();
        case EQUALS -> Condition.valueEquals(StoreValue.of(new byte[] {expected}));
      };
    }

    enum Kind {
      NONE,
      ABSENT,
      EXISTS,
      EQUALS
    }
  }

  // --- model ---

  /** One subscription: what its listener received, and what the model expects it to receive. */
  static final class Watcher {

    final @Nullable StoreKey path;
    final @Nullable NamespaceScope scope;
    final List<Object> received = Collections.synchronizedList(new ArrayList<>());
    final List<Object> expected = new ArrayList<>();
    Subscription subscription;
    boolean active = true;

    Watcher(final @Nullable StoreKey path, final @Nullable NamespaceScope scope) {
      this.path = path;
      this.scope = scope;
    }

    /** Records each event and the end of the watch, in the order the store delivers them. */
    <E> WatchListener<E> listener() {
      return new WatchListener<>() {
        @Override
        public void onEvent(final E event) {
          received.add(event);
        }

        @Override
        public void onClosed(final CloseReason reason) {
          received.add(reason);
        }
      };
    }

    List<Object> received() {
      synchronized (received) {
        return new ArrayList<>(received);
      }
    }

    @Override
    public String toString() {
      return path != null ? "watch(" + path + ")" : "watchNamespace(" + scope + ")";
    }
  }

  /**
   * The store as plain maps, written from the API's rules rather than from the implementation: a
   * batch is checked in order against the state with its earlier writes on top, fails as a whole,
   * and on success changes namespaces by scope and name, and keys by key.
   */
  static final class Model {

    final Map<Namespace, TreeMap<String, KeyValue>> namespaces = new HashMap<>();
    final List<Watcher> watchers = new ArrayList<>();

    boolean exists(final Namespace namespace) {
      return namespaces.containsKey(namespace);
    }

    List<Namespace> namespaces(final NamespaceScope scope) {
      return namespaces.keySet().stream()
          .filter(namespace -> namespace.scope() == scope)
          .sorted(Comparator.comparing(Namespace::name))
          .toList();
    }

    /** Returns the failure the batch is expected to end with, or null if it applies. */
    @Nullable Class<? extends ClusterMetadataStoreException> batch(final List<Write> writes) {
      try {
        new Batch(writes);
      } catch (final InvalidArgumentException e) {
        return InvalidArgumentException.class;
      }

      final var created = new HashSet<Namespace>();
      // the batch's writes by key: a value for a put, empty for a delete
      final var pending = new HashMap<StoreKey, Optional<StoreValue>>();
      var conditionFailed = false;

      for (final var write : writes) {
        switch (write) {
          case final Write.CreateNamespace create -> {
            if (exists(create.namespace()) || !created.add(create.namespace())) {
              return NamespaceAlreadyExistsException.class;
            }
          }
          case final Write.Put put -> {
            if (!visible(put.key().namespace(), created)) {
              return NamespaceNotFoundException.class;
            }
            if (holds(put.condition(), current(put.key(), pending))) {
              pending.put(put.key(), Optional.of(put.value()));
            } else {
              conditionFailed = true;
            }
          }
          case final Write.Delete delete -> {
            if (!visible(delete.key().namespace(), created)) {
              return NamespaceNotFoundException.class;
            }
            if (holds(delete.condition(), current(delete.key(), pending))) {
              pending.put(delete.key(), Optional.empty());
            } else {
              conditionFailed = true;
            }
          }
          case final Write.DeletePrefix deletePrefix -> {
            final var namespace = deletePrefix.prefix().namespace();
            if (!visible(namespace, created)) {
              return NamespaceNotFoundException.class;
            }
            // no other write of the batch is at or under the prefix, so the state's keys are all
            namespaces.getOrDefault(namespace, new TreeMap<>()).keySet().stream()
                .filter(key -> isAtOrUnder(key, deletePrefix.prefix().key()))
                .forEach(key -> pending.put(StoreKey.of(namespace, key), Optional.empty()));
          }
          case final Write.Check check -> {
            if (!visible(check.key().namespace(), created)) {
              return NamespaceNotFoundException.class;
            }
            conditionFailed |= !holds(check.condition(), current(check.key(), pending));
          }
        }
      }

      if (conditionFailed) {
        return ConditionFailedException.class;
      }
      commit(created, pending);
      return null;
    }

    @Nullable Class<? extends ClusterMetadataStoreException> deleteNamespace(
        final Namespace namespace) {
      final var removed = namespaces.remove(namespace);
      if (removed == null) {
        return NamespaceNotFoundException.class;
      }
      for (final var watcher : active()) {
        if (watcher.path != null && watcher.path.namespace().equals(namespace)) {
          removed.values().stream()
              .filter(value -> isAtOrUnder(value.key().key(), watcher.path.key()))
              .forEach(
                  value ->
                      watcher.expected.add(
                          new KeyEvent(ChangeType.REMOVE, value.key(), null, value.current())));
          watcher.expected.add(CloseReason.NAMESPACE_DELETED);
          watcher.active = false;
        } else if (watcher.scope == namespace.scope()) {
          watcher.expected.add(new NamespaceChange(namespace, NamespaceChange.ChangeType.DELETED));
        }
      }
      return null;
    }

    void closeStore() {
      for (final var watcher : active()) {
        watcher.expected.add(CloseReason.STORE_CLOSED);
        watcher.active = false;
      }
    }

    private void commit(
        final HashSet<Namespace> created, final Map<StoreKey, Optional<StoreValue>> pending) {
      final var touched = new TreeSet<>(NAMESPACE_ORDER);
      touched.addAll(created);
      pending.keySet().forEach(key -> touched.add(key.namespace()));

      for (final var namespace : touched) {
        if (created.contains(namespace)) {
          namespaces.put(namespace, new TreeMap<>());
          notifyNamespace(namespace, NamespaceChange.ChangeType.CREATED);
        }
        final var values = namespaces.get(namespace);
        pending.entrySet().stream()
            .filter(write -> write.getKey().namespace().equals(namespace))
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(StoreKey::key)))
            .forEach(write -> apply(values, write.getKey(), write.getValue()));
      }
    }

    private void apply(
        final TreeMap<String, KeyValue> values,
        final StoreKey key,
        final Optional<StoreValue> write) {
      final var existing = values.get(key.key());
      if (write.isPresent()) {
        final var value = write.get();
        if (existing == null) {
          values.put(key.key(), new KeyValue(key, value, null));
          notifyKey(new KeyEvent(ChangeType.ADD, key, value, null));
        } else if (!existing.current().equals(value)) {
          values.put(key.key(), new KeyValue(key, value, existing.current()));
          notifyKey(new KeyEvent(ChangeType.REPLACE, key, value, existing.current()));
        }
      } else if (existing != null) {
        values.remove(key.key());
        notifyKey(new KeyEvent(ChangeType.REMOVE, key, null, existing.current()));
      }
    }

    private void notifyKey(final KeyEvent event) {
      for (final var watcher : active()) {
        if (watcher.path != null
            && watcher.path.namespace().equals(event.key().namespace())
            && isAtOrUnder(event.key().key(), watcher.path.key())) {
          watcher.expected.add(event);
        }
      }
    }

    private void notifyNamespace(final Namespace namespace, final NamespaceChange.ChangeType type) {
      for (final var watcher : active()) {
        if (watcher.scope == namespace.scope()) {
          watcher.expected.add(new NamespaceChange(namespace, type));
        }
      }
    }

    private List<Watcher> active() {
      return watchers.stream().filter(watcher -> watcher.active).toList();
    }

    private boolean visible(final Namespace namespace, final HashSet<Namespace> created) {
      return exists(namespace) || created.contains(namespace);
    }

    private @Nullable StoreValue current(
        final StoreKey key, final Map<StoreKey, Optional<StoreValue>> pending) {
      final var write = pending.get(key);
      if (write != null) {
        return write.orElse(null);
      }
      final var value = namespaces.getOrDefault(key.namespace(), new TreeMap<>()).get(key.key());
      return value == null ? null : value.current();
    }

    private static boolean holds(final Condition condition, final @Nullable StoreValue current) {
      return switch (condition) {
        case final Condition.None ignored -> true;
        case final Condition.Absent ignored -> current == null;
        case final Condition.Exists ignored -> current != null;
        case final Condition.ValueEquals equals -> equals.expected().equals(current);
      };
    }
  }
}
