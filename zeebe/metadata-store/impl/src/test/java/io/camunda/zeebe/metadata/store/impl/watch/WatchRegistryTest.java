/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.impl.watch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.InvalidArgumentException;
import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.NamespaceNotFoundException;
import io.camunda.zeebe.metadata.store.api.KeyEvent;
import io.camunda.zeebe.metadata.store.api.Namespace;
import io.camunda.zeebe.metadata.store.api.NamespaceChange;
import io.camunda.zeebe.metadata.store.api.NamespaceScope;
import io.camunda.zeebe.metadata.store.api.StoreKey;
import io.camunda.zeebe.metadata.store.api.StoreValue;
import io.camunda.zeebe.metadata.store.api.Subscription;
import io.camunda.zeebe.metadata.store.api.Subscription.CloseReason;
import io.camunda.zeebe.metadata.store.api.WatchListener;
import io.camunda.zeebe.metadata.store.impl.state.Entry;
import io.camunda.zeebe.metadata.store.impl.state.Entry.NamespaceEntry;
import io.camunda.zeebe.metadata.store.impl.state.Entry.Operation;
import io.camunda.zeebe.metadata.store.impl.state.EntryApplier;
import io.camunda.zeebe.metadata.store.impl.state.StoreState;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Dispatch of applied changes: events bubble up on whole segments, each subscription gets one event
 * per changed key in commit order, and the store ends subscriptions with a reason after their last
 * event.
 */
final class WatchRegistryTest {

  private static final Namespace CONFIG = Namespace.cluster("config");
  private static final Executor DIRECT = Runnable::run;
  private static final StoreValue V1 = StoreValue.of(new byte[] {1});

  private StoreState state =
      EntryApplier.apply(
              StoreState.empty(),
              1,
              new Entry("8.11.0", 0, List.of(new NamespaceEntry.Created(CONFIG, List.of()))))
          .state();
  private final TestActor actor = new TestActor();
  private final WatchRegistry registry = new WatchRegistry(actor::run, () -> state);

  @Test
  void shouldNotifyWatchesOnKeyAndAncestorsOnWholeSegments() {
    // given
    final var onKey = watch("a.b.c");
    final var onParent = watch("a.b");
    final var onRoot = watch("a");
    final var onSibling = watch("a.bc");
    final var onChild = watch("a.b.c.d");

    // when
    apply(put("a.b.c"));

    // then
    assertThat(onKey.events).hasSize(1);
    assertThat(onParent.events).hasSize(1);
    assertThat(onRoot.events).hasSize(1);
    assertThat(onSibling.events).isEmpty();
    assertThat(onChild.events).isEmpty();
  }

  @Test
  void shouldDeliverOneEventPerChangedKeyInOrder() {
    // given
    final var watch = watch("a");

    // when
    apply(put("a.x"), put("a.y"));
    apply(new Operation.Delete("a.x"));

    // then
    assertThat(watch.events)
        .extracting(event -> event.type() + " " + event.key().key())
        .containsExactly("ADD a.x", "ADD a.y", "REMOVE a.x");
  }

  @Test
  void shouldRemoveKeysThenCloseWatchesOfDeletedNamespace() {
    // given
    apply(put("a.x"), put("a.y"), put("b"));
    final var watch = watch("a");
    final var namespaces = new CopyOnWriteArrayList<String>();
    registry.watchNamespace(
        NamespaceScope.CLUSTER,
        change -> namespaces.add(change.type() + " " + change.namespace()),
        DIRECT);

    // when
    applyNamespace(new NamespaceEntry.Deleted(CONFIG));

    // then
    assertThat(watch.log)
        .containsExactly("REMOVE a.x", "REMOVE a.y", "closed " + CloseReason.NAMESPACE_DELETED);
    assertThat(namespaces).containsExactly(NamespaceChange.ChangeType.DELETED + " " + CONFIG);
  }

  @Test
  void shouldRejectWatchOfEmptyKeyOrUnknownNamespace() {
    // when - then
    assertThatThrownBy(() -> registry.watch(StoreKey.of(CONFIG, ""), event -> {}, DIRECT))
        .isInstanceOf(InvalidArgumentException.class);
    assertThatThrownBy(
            () ->
                registry.watch(StoreKey.of(Namespace.cluster("unknown"), "a"), event -> {}, DIRECT))
        .isInstanceOf(NamespaceNotFoundException.class);
  }

  @Test
  void shouldWatchKeyThatDoesNotExistYet() {
    // given
    final var watch = watch("a.b");

    // when
    apply(put("a.b.c"));

    // then
    assertThat(watch.log).containsExactly("ADD a.b.c");
  }

  @Test
  void shouldEndWatchRegisteredAfterItsNamespaceWasDeleted() {
    // given - a registry whose actor runs the registration only later
    final var actorTasks = new ArrayList<Runnable>();
    final var deferred = new WatchRegistry(actorTasks::add, () -> state);
    final var reasons = new CopyOnWriteArrayList<CloseReason>();
    deferred.watch(StoreKey.of(CONFIG, "a"), listener(event -> {}, reasons::add), DIRECT);
    applyNamespace(new NamespaceEntry.Deleted(CONFIG));

    // when
    actorTasks.forEach(Runnable::run);

    // then
    assertThat(reasons).containsExactly(CloseReason.NAMESPACE_DELETED);
  }

  @Test
  void shouldStopDeliveringAfterConsumerCloses() {
    // given
    final var watch = watch("a");

    // when
    watch.subscription.close();
    apply(put("a"));

    // then
    assertThat(watch.log).isEmpty();
  }

  @Test
  void shouldCloseEverySubscriptionWhenStoreCloses() {
    // given
    final var first = watch("a");
    final var second = watch("b");

    // when
    registry.closeAll(CloseReason.STORE_CLOSED);
    apply(put("a"));

    // then
    assertThat(first.log).containsExactly("closed " + CloseReason.STORE_CLOSED);
    assertThat(second.log).containsExactly("closed " + CloseReason.STORE_CLOSED);
  }

  @Test
  void shouldEndWatchWhoseRegistrationActorDropsOnClose() {
    // given - the actor closes before it runs the registration, and so drops it
    final var actorTasks = new ArrayList<Runnable>();
    final var deferred = new WatchRegistry(actorTasks::add, () -> state);
    final var keyReasons = new CopyOnWriteArrayList<CloseReason>();
    final var namespaceReasons = new CopyOnWriteArrayList<CloseReason>();
    deferred.watch(StoreKey.of(CONFIG, "a"), listener(event -> {}, keyReasons::add), DIRECT);
    deferred.watchNamespace(
        NamespaceScope.CLUSTER, namespaceListener(namespaceReasons::add), DIRECT);

    // when
    deferred.closeAll(CloseReason.STORE_CLOSED);

    // then
    assertThat(keyReasons).containsExactly(CloseReason.STORE_CLOSED);
    assertThat(namespaceReasons).containsExactly(CloseReason.STORE_CLOSED);
  }

  @Test
  void shouldNotRegisterWatchEndedBeforeItsRegistrationRan() {
    // given
    final var actorTasks = new ArrayList<Runnable>();
    final var deferred = new WatchRegistry(actorTasks::add, () -> state);
    final var log = new CopyOnWriteArrayList<String>();
    deferred.watch(
        StoreKey.of(CONFIG, "a"),
        listener(event -> log.add(event.key().key()), reason -> log.add("closed " + reason)),
        DIRECT);
    deferred.closeAll(CloseReason.STORE_CLOSED);

    // when - the actor runs the registration after all
    actorTasks.forEach(Runnable::run);
    final var applied =
        EntryApplier.apply(
            state,
            state.appliedPosition() + 1,
            new Entry(
                "8.11.0", 0, List.of(new NamespaceEntry.KeysChanged(CONFIG, List.of(put("a"))))));
    deferred.dispatch(applied.changes());

    // then
    assertThat(log).containsExactly("closed " + CloseReason.STORE_CLOSED);
  }

  @Test
  void shouldEndWatchRegisteredAfterClose() {
    // given
    registry.closeAll(CloseReason.STORE_CLOSED);

    // when
    final var watch = watch("a");
    final var namespaceReasons = new CopyOnWriteArrayList<CloseReason>();
    registry.watchNamespace(
        NamespaceScope.CLUSTER, namespaceListener(namespaceReasons::add), DIRECT);
    apply(put("a"));

    // then
    assertThat(watch.log).containsExactly("closed " + CloseReason.STORE_CLOSED);
    assertThat(namespaceReasons).containsExactly(CloseReason.STORE_CLOSED);
  }

  @Test
  void shouldStopUsingExecutorOnceItRejects() {
    // given
    final var attempts = new AtomicInteger();
    registry.watch(
        StoreKey.of(CONFIG, "a"),
        event -> {},
        task -> {
          attempts.incrementAndGet();
          throw new RejectedExecutionException("shut down");
        });

    // when
    apply(put("a.x"));
    apply(put("a.y"));
    registry.closeAll(CloseReason.STORE_CLOSED);

    // then
    assertThat(attempts).hasValue(1);
  }

  @Test
  void shouldKeepDeliveringAfterListenerThrowsError() {
    // given - an executor whose thread survives the error, as a pool's would
    final Executor survivesErrors =
        task -> {
          try {
            task.run();
          } catch (final AssertionError ignored) {
            // the next task runs as if on a fresh thread
          }
        };
    final var received = new CopyOnWriteArrayList<String>();
    registry.watch(
        StoreKey.of(CONFIG, "a"),
        event -> {
          received.add(event.key().key());
          throw new AssertionError("listener error");
        },
        survivesErrors);

    // when
    apply(put("a.x"));
    apply(put("a.y"));

    // then
    assertThat(received).containsExactly("a.x", "a.y");
  }

  @Test
  void shouldDeliverCallsQueuedBehindListenerError() {
    // given - an executor that runs its tasks only later, so events and the end queue up
    final var tasks = new ArrayDeque<Runnable>();
    final var log = new CopyOnWriteArrayList<String>();
    registry.watch(
        StoreKey.of(CONFIG, "a"),
        listener(
            event -> {
              log.add(event.key().key());
              if (event.key().key().equals("a.x")) {
                throw new AssertionError("listener error");
              }
            },
            reason -> log.add("closed " + reason)),
        tasks::add);
    apply(put("a.x"));
    apply(put("a.y"));
    registry.closeAll(CloseReason.STORE_CLOSED);

    // when - each task runs as on a pool thread that survives the error
    Runnable task;
    while ((task = tasks.poll()) != null) {
      try {
        task.run();
      } catch (final AssertionError ignored) {
        // the executor carries on with its next task
      }
    }

    // then
    assertThat(log).containsExactly("a.x", "a.y", "closed " + CloseReason.STORE_CLOSED);
  }

  @Test
  void shouldEndOtherSubscriptionsWhenListenerExecutorRejects() {
    // given - a consumer that shut its executor down before the store closed
    final var shutDown = Executors.newSingleThreadExecutor();
    shutDown.shutdownNow();
    registry.watch(StoreKey.of(CONFIG, "a"), event -> {}, shutDown);
    final var other = watch("a");

    // when
    apply(put("a.x"));
    registry.closeAll(CloseReason.STORE_CLOSED);

    // then
    assertThat(other.log).containsExactly("ADD a.x", "closed " + CloseReason.STORE_CLOSED);
  }

  @Test
  void shouldKeepDeliveringAfterListenerThrows() {
    // given
    final var received = new CopyOnWriteArrayList<String>();
    registry.watch(
        StoreKey.of(CONFIG, "a"),
        event -> {
          received.add(event.key().key());
          throw new IllegalStateException("listener failure");
        },
        DIRECT);

    // when
    apply(put("a.x"));
    apply(put("a.y"));

    // then
    assertThat(received).containsExactly("a.x", "a.y");
  }

  @Test
  void shouldCallListenerOneAtATimeOnConcurrentExecutor() {
    // given
    final var executor = Executors.newFixedThreadPool(4);
    final var running = new AtomicInteger();
    final var overlaps = new AtomicInteger();
    final var received = new CopyOnWriteArrayList<String>();
    registry.watch(
        StoreKey.of(CONFIG, "a"),
        event -> {
          if (running.incrementAndGet() > 1) {
            overlaps.incrementAndGet();
          }
          received.add(event.key().key());
          running.decrementAndGet();
        },
        executor);

    try {
      // when
      IntStream.range(0, 200).forEach(i -> apply(put("a.k" + i)));

      // then
      await().untilAsserted(() -> assertThat(received).hasSize(200));
      assertThat(overlaps).hasValue(0);
      assertThat(received)
          .containsExactlyElementsOf(IntStream.range(0, 200).mapToObj(i -> "a.k" + i).toList());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void shouldNotHoldBackDispatchOrOtherSubscriptionsWhileListenerIsSlow() {
    // given
    final var slowExecutor = Executors.newSingleThreadExecutor();
    final var fastExecutor = Executors.newSingleThreadExecutor();
    final var release = new CountDownLatch(1);
    final var slow = new CopyOnWriteArrayList<String>();
    final var fast = new CopyOnWriteArrayList<String>();
    registry.watch(
        StoreKey.of(CONFIG, "a"),
        event -> {
          awaitRelease(release);
          slow.add(event.key().key());
        },
        slowExecutor);
    registry.watch(StoreKey.of(CONFIG, "a"), event -> fast.add(event.key().key()), fastExecutor);
    final var expected = IntStream.range(0, 100).mapToObj(i -> "a.k" + i).toList();

    try {
      // when: dispatching never waits for a listener
      assertCompletesWithoutWaiting(() -> expected.forEach(key -> apply(put(key))));

      // then
      await().untilAsserted(() -> assertThat(fast).containsExactlyElementsOf(expected));
      assertThat(slow).isEmpty();

      release.countDown();
      await().untilAsserted(() -> assertThat(slow).containsExactlyElementsOf(expected));
    } finally {
      release.countDown();
      slowExecutor.shutdownNow();
      fastExecutor.shutdownNow();
    }
  }

  @Test
  void shouldDropQueuedEventsWhenConsumerClosesSlowSubscription() {
    // given
    final var executor = Executors.newSingleThreadExecutor();
    final var release = new CountDownLatch(1);
    final var started = new CountDownLatch(1);
    final var received = new CopyOnWriteArrayList<String>();
    final var reasons = new CopyOnWriteArrayList<CloseReason>();
    final var subscription =
        registry.watch(
            StoreKey.of(CONFIG, "a"),
            listener(
                event -> {
                  started.countDown();
                  awaitRelease(release);
                  received.add(event.key().key());
                },
                reasons::add),
            executor);

    try {
      assertCompletesWithoutWaiting(
          () -> {
            apply(put("a.first"));
            awaitRelease(started);
            apply(put("a.queued"));
          });

      // when
      subscription.close();
      release.countDown();

      // then
      await().untilAsserted(() -> assertThat(received).containsExactly("a.first"));
      apply(put("a.after"));
      executor.submit(() -> {}).get();
      assertThat(received).containsExactly("a.first");
      assertThat(reasons).isEmpty();
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  /** Runs the dispatch on another thread, so a dispatch that waits for a listener fails. */
  private static void assertCompletesWithoutWaiting(final Runnable dispatch) {
    assertThat(CompletableFuture.runAsync(dispatch)).succeedsWithin(Duration.ofSeconds(10));
  }

  private static WatchListener<KeyEvent> listener(
      final Consumer<KeyEvent> onEvent, final Consumer<CloseReason> onClosed) {
    return new WatchListener<>() {
      @Override
      public void onEvent(final KeyEvent event) {
        onEvent.accept(event);
      }

      @Override
      public void onClosed(final CloseReason reason) {
        onClosed.accept(reason);
      }
    };
  }

  private static WatchListener<NamespaceChange> namespaceListener(
      final Consumer<CloseReason> onClosed) {
    return new WatchListener<>() {
      @Override
      public void onEvent(final NamespaceChange change) {}

      @Override
      public void onClosed(final CloseReason reason) {
        onClosed.accept(reason);
      }
    };
  }

  private static void awaitRelease(final CountDownLatch latch) {
    try {
      latch.await();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private Watch watch(final String path) {
    final var watch = new Watch();
    watch.subscription =
        registry.watch(
            StoreKey.of(CONFIG, path),
            listener(
                event -> {
                  watch.events.add(event);
                  watch.log.add(event.type() + " " + event.key().key());
                },
                reason -> watch.log.add("closed " + reason)),
            DIRECT);
    return watch;
  }

  private void apply(final Operation... operations) {
    applyNamespace(new NamespaceEntry.KeysChanged(CONFIG, List.of(operations)));
  }

  private void applyNamespace(final NamespaceEntry namespace) {
    final var applied =
        EntryApplier.apply(
            state, state.appliedPosition() + 1, new Entry("8.11.0", 0, List.of(namespace)));
    state = applied.state();
    actor.run(() -> registry.dispatch(applied.changes()));
  }

  private static Operation put(final String key) {
    return new Operation.Put(key, V1);
  }

  /**
   * Runs one task at a time. A task submitted from within a running task runs after it, as on the
   * store's actor, so a removal requested during dispatch does not change the watches dispatch is
   * iterating.
   */
  private static final class TestActor {
    private final Deque<Runnable> queued = new ArrayDeque<>();
    private boolean running;

    synchronized void run(final Runnable task) {
      if (running) {
        queued.add(task);
        return;
      }
      running = true;
      try {
        task.run();
        Runnable next;
        while ((next = queued.poll()) != null) {
          next.run();
        }
      } finally {
        running = false;
      }
    }
  }

  private static final class Watch {
    private final List<KeyEvent> events = new CopyOnWriteArrayList<>();
    private final List<String> log = new CopyOnWriteArrayList<>();
    private Subscription subscription;
  }
}
