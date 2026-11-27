/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.metadata.store.api.Batch;
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStore;
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException;
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.ConditionFailedException;
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.ConditionFailedException.FailedCondition;
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.InvalidArgumentException;
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.NamespaceAlreadyExistsException;
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.NamespaceNotFoundException;
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.StoreClosedException;
import io.camunda.zeebe.metadata.store.api.Condition;
import io.camunda.zeebe.metadata.store.api.KeyEvent;
import io.camunda.zeebe.metadata.store.api.KeyEvent.ChangeType;
import io.camunda.zeebe.metadata.store.api.KeyValue;
import io.camunda.zeebe.metadata.store.api.Namespace;
import io.camunda.zeebe.metadata.store.api.NamespaceApi;
import io.camunda.zeebe.metadata.store.api.NamespaceChange;
import io.camunda.zeebe.metadata.store.api.NamespaceScope;
import io.camunda.zeebe.metadata.store.api.StoreKey;
import io.camunda.zeebe.metadata.store.api.StoreValue;
import io.camunda.zeebe.metadata.store.api.Subscription;
import io.camunda.zeebe.metadata.store.api.Subscription.CloseReason;
import io.camunda.zeebe.metadata.store.api.WatchListener;
import io.camunda.zeebe.metadata.store.api.Write;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The behaviour every cluster metadata store has to show, written against {@link
 * ClusterMetadataStore} and {@link NamespaceApi} only. An implementation passes it by extending
 * this class and supplying a running store.
 */
public abstract class ClusterMetadataStoreContractTest {

  private static final Executor DIRECT = Runnable::run;
  private static final Namespace CONFIG = Namespace.cluster("config");

  protected ClusterMetadataStore store;
  protected NamespaceApi namespaces;

  /** Starts a new, empty store; the test closes it with {@link #closeStore()}. */
  protected abstract StoreUnderTest startStore();

  /**
   * Closes the store, after which calls fail with {@link StoreClosedException}. Called by a test
   * and again after each test, so it must tolerate a store that is already closed.
   */
  protected abstract void closeStore();

  @BeforeEach
  void setUpStore() {
    final var started = startStore();
    store = started.store();
    namespaces = started.namespaces();
    join(namespaces.createNamespace(CONFIG));
  }

  @AfterEach
  void tearDownStore() {
    closeStore();
  }

  @Test
  void shouldReadWhatWasWritten() {
    // when
    join(store.put(key("a.b"), bytes("1")));
    join(store.put(key("a.b.c"), bytes("2")));
    join(store.put(key("a.bc"), bytes("3")));

    // then
    assertThat(join(store.get(key("a.b")))).map(KeyValue::current).contains(bytes("1"));
    assertThat(join(store.get(key("a")))).isEmpty();
    assertThat(join(store.list(key("a.b"))))
        .extracting(pair -> pair.key().key())
        .containsExactly("a.b", "a.b.c");
    assertThat(join(store.list(key(""))))
        .extracting(pair -> pair.key().key())
        .containsExactly("a.b", "a.b.c", "a.bc");
  }

  @Test
  void shouldKeepReplacedValueAsPrevious() {
    // given
    join(store.put(key("a"), bytes("1")));

    // when
    join(store.put(key("a"), bytes("2")));

    // then
    assertThat(join(store.get(key("a")))).contains(new KeyValue(key("a"), bytes("2"), bytes("1")));
  }

  @Test
  void shouldDeleteKeyButNotKeysUnderIt() {
    // given
    join(store.put(key("a"), bytes("1")));
    join(store.put(key("a.b"), bytes("2")));

    // when
    join(store.delete(key("a")));

    // then
    assertThat(join(store.list(key(""))))
        .extracting(pair -> pair.key().key())
        .containsExactly("a.b");
  }

  @Test
  void shouldDeletePrefixOnWholeSegments() {
    // given
    join(store.put(key("a.b"), bytes("1")));
    join(store.put(key("a.b.c"), bytes("2")));
    join(store.put(key("a.bc"), bytes("3")));

    // when
    join(store.deletePrefix(key("a.b")));

    // then
    assertThat(join(store.list(key(""))))
        .extracting(pair -> pair.key().key())
        .containsExactly("a.bc");
  }

  @Test
  void shouldApplyConditionalWriteOnlyIfConditionHolds() {
    // given
    join(store.put(key("id"), bytes("1"), Condition.absent()));

    // when
    final var failure = failureOf(store.put(key("id"), bytes("2"), Condition.absent()));

    // then
    assertThat(failure)
        .isInstanceOfSatisfying(
            ConditionFailedException.class,
            e ->
                assertThat(e.failed())
                    .containsExactly(
                        new FailedCondition(key("id"), new KeyValue(key("id"), bytes("1"), null))));
    assertThat(join(store.get(key("id")))).map(KeyValue::current).contains(bytes("1"));
  }

  @Test
  void shouldReplaceWholeRecordOnlyIfItIsStillTheOneRead() {
    // given - a record kept as one value, read before it is changed
    final var record = key("failover");
    join(store.put(record, bytes("v1")));
    final var seen = join(store.get(record)).orElseThrow().current();

    // when - one writer advances it, a second one still bases its change on the old read
    join(store.put(record, bytes("v2"), Condition.valueEquals(seen)));
    final var stale = failureOf(store.put(record, bytes("v3"), Condition.valueEquals(seen)));

    // then
    assertThat(stale)
        .isInstanceOfSatisfying(
            ConditionFailedException.class,
            e ->
                assertThat(e.failed())
                    .containsExactly(
                        new FailedCondition(
                            record, new KeyValue(record, bytes("v2"), bytes("v1")))));
    assertThat(join(store.get(record))).map(KeyValue::current).contains(bytes("v2"));
  }

  @Test
  void shouldCompareAndSetAcrossKeysAndNamespaces() {
    // given - the values a change is based on, read together
    final var tenant = Namespace.tenant("tenanta");
    join(namespaces.createNamespace(tenant));
    final var bucket = key("backup.s3.bucket");
    final var region = key("backup.s3.region");
    final var kind = key("backup.store");
    final var basePath = StoreKey.of(tenant, "backup.s3.base-path");
    join(store.put(bucket, bytes("eu-bucket")));
    join(store.put(region, bytes("eu-west-1")));
    join(store.put(kind, bytes("S3")));
    final var seen = join(store.list(key("backup")));
    final var change =
        Batch.of(
            new Write.Put(bucket, bytes("us-bucket"), Condition.valueEquals(valueOf(seen, bucket))),
            new Write.Put(region, bytes("us-east-1"), Condition.valueEquals(valueOf(seen, region))),
            new Write.Put(basePath, bytes("tenanta-us")),
            new Write.Check(kind, Condition.valueEquals(bytes("S3"))));

    // when - a concurrent writer changes both keys the change is based on
    join(store.put(bucket, bytes("other-bucket")));
    join(store.put(region, bytes("other-region")));
    final var failure = failureOf(store.batch(change));

    // then - every failed key is reported as it is now, and nothing was applied
    assertThat(failure)
        .isInstanceOfSatisfying(
            ConditionFailedException.class,
            e ->
                assertThat(e.failed())
                    .extracting(FailedCondition::key, failed -> failed.current().current())
                    .containsExactly(
                        tuple(bucket, bytes("other-bucket")),
                        tuple(region, bytes("other-region"))));
    assertThat(join(store.get(basePath))).isEmpty();

    // when - the same change based on a fresh read
    final var fresh = join(store.list(key("backup")));
    join(
        store.batch(
            Batch.of(
                new Write.Put(
                    bucket, bytes("us-bucket"), Condition.valueEquals(valueOf(fresh, bucket))),
                new Write.Put(
                    region, bytes("us-east-1"), Condition.valueEquals(valueOf(fresh, region))),
                new Write.Put(basePath, bytes("tenanta-us")),
                new Write.Check(kind, Condition.valueEquals(bytes("S3"))))));

    // then - all of it applies at once
    assertThat(join(store.get(bucket))).map(KeyValue::current).contains(bytes("us-bucket"));
    assertThat(join(store.get(region))).map(KeyValue::current).contains(bytes("us-east-1"));
    assertThat(join(store.get(basePath))).map(KeyValue::current).contains(bytes("tenanta-us"));
  }

  @Test
  void shouldApplyBatchAllOrNothing() {
    // given
    final var tenant = Namespace.tenant("tenant-a");
    final var failing =
        Batch.of(
            new Write.CreateNamespace(tenant),
            new Write.Put(StoreKey.of(tenant, "a"), bytes("1")),
            new Write.Check(key("missing"), Condition.exists()));

    // when
    final var failure = failureOf(store.batch(failing));
    join(store.batch(new Batch(failing.writes().subList(0, 2))));

    // then
    assertThat(failure).isInstanceOf(ConditionFailedException.class);
    assertThat(join(store.get(StoreKey.of(tenant, "a"))))
        .map(KeyValue::current)
        .contains(bytes("1"));
  }

  @Test
  void shouldFailKeyOperationsOnUnknownNamespace() {
    // given
    final var unknown = StoreKey.of(Namespace.cluster("unknown"), "a");

    // when - then
    assertThat(failureOf(store.get(unknown))).isInstanceOf(NamespaceNotFoundException.class);
    assertThat(failureOf(store.list(unknown))).isInstanceOf(NamespaceNotFoundException.class);
    assertThat(failureOf(store.put(unknown, bytes("1"))))
        .isInstanceOf(NamespaceNotFoundException.class);
    assertThatThrownBy(() -> store.watch(unknown, event -> {}, DIRECT))
        .isInstanceOf(NamespaceNotFoundException.class);
  }

  @Test
  void shouldRejectEmptyKeyExceptAsListPrefix() {
    // given
    final var empty = key("");

    // when - then
    assertThat(failureOf(store.get(empty))).isInstanceOf(InvalidArgumentException.class);
    assertThat(failureOf(store.put(empty, bytes("1"))))
        .isInstanceOf(InvalidArgumentException.class);
    assertThat(failureOf(store.delete(empty))).isInstanceOf(InvalidArgumentException.class);
    assertThat(failureOf(store.deletePrefix(empty))).isInstanceOf(InvalidArgumentException.class);
    assertThatThrownBy(() -> store.watch(empty, event -> {}, DIRECT))
        .isInstanceOf(InvalidArgumentException.class);
    assertThat(join(store.list(empty))).isEmpty();
  }

  @Test
  void shouldDeliverChangesAfterSubscribingThenReading() {
    // given
    join(store.put(key("a.x"), bytes("1")));
    final var events = new CopyOnWriteArrayList<KeyEvent>();
    store.watch(key("a"), events::add, DIRECT);
    final var initial = join(store.list(key("a")));

    // when
    join(store.put(key("a.x"), bytes("1")));
    join(store.put(key("a.x"), bytes("2")));
    join(store.put(key("a.y"), bytes("3")));
    join(store.delete(key("a.x")));

    // then
    assertThat(initial).extracting(pair -> pair.key().key()).containsExactly("a.x");
    await()
        .untilAsserted(
            () ->
                assertThat(events)
                    .containsExactly(
                        new KeyEvent(ChangeType.REPLACE, key("a.x"), bytes("2"), bytes("1")),
                        new KeyEvent(ChangeType.ADD, key("a.y"), bytes("3"), null),
                        new KeyEvent(ChangeType.REMOVE, key("a.x"), null, bytes("2"))));
  }

  @Test
  void shouldDeliverEventsInCommitOrderOnConcurrentExecutor() {
    // given - a pool, so only the store's own ordering keeps the events in order
    final var executor = Executors.newFixedThreadPool(4);
    final var received = new CopyOnWriteArrayList<String>();
    final var running = new AtomicInteger();
    final var overlaps = new AtomicInteger();
    store.watch(
        key("a"),
        listener(
            event -> {
              if (running.incrementAndGet() > 1) {
                overlaps.incrementAndGet();
              }
              received.add(event.type() + " " + event.key().key());
              running.decrementAndGet();
            },
            reason -> received.add("closed " + reason)),
        executor);
    final var expected = new ArrayList<String>();

    try {
      // when
      for (int i = 0; i < 100; i++) {
        join(store.put(key("a.k" + i), bytes(String.valueOf(i))));
        expected.add("ADD a.k" + i);
      }
      join(
          store.batch(
              Batch.of(
                  new Write.Delete(key("a.k5")),
                  new Write.Put(key("a.k1"), bytes("x")),
                  new Write.Put(key("a.b"), bytes("y")))));
      // the events of one batch follow each other, in key order
      expected.addAll(List.of("ADD a.b", "REPLACE a.k1", "REMOVE a.k5"));
      closeStore();
      expected.add("closed " + CloseReason.STORE_CLOSED);

      // then
      await().untilAsserted(() -> assertThat(received).containsExactlyElementsOf(expected));
      assertThat(overlaps).hasValue(0);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void shouldLetListenerReadWholeBatchOnItsFirstEvent() {
    // given - the listener reads on another thread than the store's
    final var executor = Executors.newSingleThreadExecutor();
    final var first = new AtomicBoolean(true);
    final var listed = new CompletableFuture<List<String>>();
    store.watch(
        key("a"),
        event -> {
          if (first.getAndSet(false)) {
            store
                .list(key("a"))
                .onComplete(
                    (pairs, error) -> {
                      if (error != null) {
                        listed.completeExceptionally(error);
                      } else {
                        listed.complete(pairs.stream().map(pair -> pair.key().key()).toList());
                      }
                    },
                    Runnable::run);
          }
        },
        executor);

    try {
      // when
      join(
          store.batch(
              Batch.of(
                  new Write.Put(key("a.x"), bytes("1")), new Write.Put(key("a.y"), bytes("2")))));

      // then - the first event is about a.x, but the read already sees all of the batch
      assertThat(listed).succeedsWithin(Duration.ofSeconds(10)).isEqualTo(List.of("a.x", "a.y"));
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void shouldStopDeliveringAfterSubscriptionIsClosed() {
    // given
    final var events = new CopyOnWriteArrayList<KeyEvent>();
    final var subscription = store.watch(key("a"), events::add, DIRECT);
    join(store.put(key("a"), bytes("1")));
    await().until(() -> events.size() == 1);

    // when
    subscription.close();
    join(store.put(key("a"), bytes("2")));
    join(store.put(key("b"), bytes("2")));

    // then
    assertThat(events).hasSize(1);
  }

  @Test
  void shouldDeliverInCommitOrderWhenListenerWrites() {
    // given - the first listener writes on its first event, before the second one has seen it
    final var received = new CopyOnWriteArrayList<String>();
    final var first = new AtomicBoolean(true);
    store.watch(
        key("a"),
        event -> {
          if (first.getAndSet(false)) {
            // not joined: a store may deliver on a thread that must not block
            store.put(key("a.y"), bytes("2"));
          }
        },
        DIRECT);
    store.watch(key("a"), event -> received.add(event.type() + " " + event.key().key()), DIRECT);

    // when
    join(store.put(key("a.x"), bytes("1")));

    // then
    await().untilAsserted(() -> assertThat(received).containsExactly("ADD a.x", "ADD a.y"));
  }

  @Test
  void shouldKeepDeliveringToOthersWhenListenerChangesSubscriptions() {
    // given - a listener that closes its own watch and registers a new one on its first event
    final var own = new AtomicReference<Subscription>();
    final var registered = new CopyOnWriteArrayList<String>();
    own.set(
        store.watch(
            key("a"),
            event -> {
              own.get().close();
              store.watch(key("a"), next -> registered.add(next.key().key()), DIRECT);
            },
            DIRECT));
    final var other = new CopyOnWriteArrayList<String>();
    store.watch(key("a"), event -> other.add(event.key().key()), DIRECT);

    // when
    join(store.put(key("a.x"), bytes("1")));
    join(store.put(key("a.y"), bytes("2")));

    // then
    await().untilAsserted(() -> assertThat(other).containsExactly("a.x", "a.y"));
    await().untilAsserted(() -> assertThat(registered).containsExactly("a.y"));
  }

  @Test
  void shouldEndEverySubscriptionWhenListenersCloseThemOnClose() {
    // given - listeners that close their own watch when the store ends it
    final var reasons = new CopyOnWriteArrayList<CloseReason>();
    for (int i = 0; i < 2; i++) {
      final var own = new AtomicReference<Subscription>();
      own.set(
          store.watch(
              key("a"),
              listener(
                  event -> {},
                  reason -> {
                    own.get().close();
                    reasons.add(reason);
                  }),
              DIRECT));
    }

    // when
    closeStore();

    // then
    await()
        .untilAsserted(
            () ->
                assertThat(reasons)
                    .containsExactly(CloseReason.STORE_CLOSED, CloseReason.STORE_CLOSED));
  }

  @Test
  void shouldKeepDeliveringAfterListenerThrowsError() {
    // given - a listener that fails an assertion on its first event, on a thread that swallows it
    final var executor =
        Executors.newSingleThreadExecutor(
            runnable -> {
              final var thread = new Thread(runnable);
              thread.setUncaughtExceptionHandler((t, e) -> {});
              return thread;
            });
    final var log = new CopyOnWriteArrayList<String>();
    store.watch(
        key("a"),
        listener(
            event -> {
              log.add(event.key().key());
              if (event.key().key().equals("a.x")) {
                throw new AssertionError("expected by the test");
              }
            },
            reason -> log.add("closed " + reason)),
        executor);

    try {
      // when
      join(store.put(key("a.x"), bytes("1")));
      join(store.put(key("a.y"), bytes("2")));
      closeStore();

      // then
      await()
          .untilAsserted(
              () ->
                  assertThat(log)
                      .containsExactly("a.x", "a.y", "closed " + CloseReason.STORE_CLOSED));
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void shouldManageNamespaces() {
    // given
    final var created = new CopyOnWriteArrayList<String>();
    namespaces.watchNamespace(
        NamespaceScope.PHYSICAL_TENANT,
        change -> created.add(change.type() + " " + change.namespace().name()),
        DIRECT);

    // when
    join(namespaces.createNamespace(Namespace.tenant("b")));
    join(namespaces.createNamespace(Namespace.tenant("a")));
    join(namespaces.deleteNamespace(Namespace.tenant("b")));

    // then
    assertThat(join(namespaces.list(NamespaceScope.PHYSICAL_TENANT)))
        .containsExactly(Namespace.tenant("a"));
    assertThat(join(namespaces.exists(Namespace.tenant("a")))).isTrue();
    assertThat(join(namespaces.exists(Namespace.tenant("b")))).isFalse();
    assertThat(failureOf(namespaces.createNamespace(Namespace.tenant("a"))))
        .isInstanceOf(NamespaceAlreadyExistsException.class);
    assertThat(failureOf(namespaces.deleteNamespace(Namespace.tenant("b"))))
        .isInstanceOf(NamespaceNotFoundException.class);
    await()
        .untilAsserted(
            () ->
                assertThat(created)
                    .containsExactly(
                        NamespaceChange.ChangeType.CREATED + " b",
                        NamespaceChange.ChangeType.CREATED + " a",
                        NamespaceChange.ChangeType.DELETED + " b"));
  }

  @Test
  void shouldRemoveKeysThenCloseWatchWhenNamespaceIsDeleted() {
    // given
    join(store.put(key("a.x"), bytes("1")));
    final var log = new CopyOnWriteArrayList<String>();
    store.watch(
        key("a"),
        listener(
            event -> log.add(event.type() + " " + event.key().key()),
            reason -> log.add("closed " + reason)),
        DIRECT);

    // when
    join(namespaces.deleteNamespace(CONFIG));

    // then
    await()
        .untilAsserted(
            () ->
                assertThat(log)
                    .containsExactly("REMOVE a.x", "closed " + CloseReason.NAMESPACE_DELETED));
    assertThat(failureOf(store.get(key("a.x")))).isInstanceOf(NamespaceNotFoundException.class);
  }

  @Test
  void shouldFailCallsAndEndSubscriptionsWhenClosed() {
    // given
    final var reasons = new CopyOnWriteArrayList<CloseReason>();
    store.watch(key("a"), listener(event -> {}, reasons::add), DIRECT);
    namespaces.watchNamespace(NamespaceScope.CLUSTER, listener(change -> {}, reasons::add), DIRECT);

    // when
    closeStore();

    // then
    await()
        .untilAsserted(
            () ->
                assertThat(reasons)
                    .containsExactly(CloseReason.STORE_CLOSED, CloseReason.STORE_CLOSED));
    assertThat(failureOf(store.get(key("a")))).isInstanceOf(StoreClosedException.class);
    assertThat(failureOf(store.put(key("a"), bytes("1")))).isInstanceOf(StoreClosedException.class);
    assertThat(failureOf(namespaces.exists(CONFIG))).isInstanceOf(StoreClosedException.class);
    assertThatThrownBy(() -> store.watch(key("a"), event -> {}, DIRECT))
        .isInstanceOf(StoreClosedException.class);
  }

  protected static StoreKey key(final String key) {
    return StoreKey.of(CONFIG, key);
  }

  private static StoreValue valueOf(final List<KeyValue> pairs, final StoreKey key) {
    return pairs.stream()
        .filter(pair -> pair.key().equals(key))
        .findFirst()
        .orElseThrow()
        .current();
  }

  protected static StoreValue bytes(final String value) {
    return StoreValue.of(value.getBytes(StandardCharsets.UTF_8));
  }

  /** A listener that also follows the end of its watch. */
  protected static <E> WatchListener<E> listener(
      final Consumer<E> onEvent, final Consumer<CloseReason> onClosed) {
    return new WatchListener<>() {
      @Override
      public void onEvent(final E event) {
        onEvent.accept(event);
      }

      @Override
      public void onClosed(final CloseReason reason) {
        onClosed.accept(reason);
      }
    };
  }

  protected static <T> T join(final ActorFuture<T> future) {
    return future.join(10, TimeUnit.SECONDS);
  }

  /** Returns the store error that the future failed with. */
  protected static Throwable failureOf(final ActorFuture<?> future) {
    Throwable failure = null;
    try {
      join(future);
    } catch (final Exception e) {
      // join rethrows the failure as it is, checked or not
      failure = e;
    }
    assertThat(failure).as("expected the call to fail").isNotNull();
    while (!(failure instanceof ClusterMetadataStoreException) && failure.getCause() != null) {
      failure = failure.getCause();
    }
    return failure;
  }

  /** A running store, with both interfaces it serves. */
  public record StoreUnderTest(ClusterMetadataStore store, NamespaceApi namespaces) {}
}
