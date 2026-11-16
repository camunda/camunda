/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import static io.camunda.zeebe.db.writebehind.TestValues.commit;
import static io.camunda.zeebe.db.writebehind.TestValues.held;
import static io.camunda.zeebe.db.writebehind.TestValues.key;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import io.camunda.zeebe.db.DbValue;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class WriterFailureTest {

  private WriteBehindStore<RawKey, HeldValue, DbValue> store;

  @AfterEach
  void tearDown() {
    if (store != null) {
      store.close();
    }
  }

  @Test
  void shouldFailStoreWhenWriterThreadDiesOfUnexpectedError() {
    // given - the cache weighs a flushed value on the writer thread, after the storage write; an
    // error there is outside the retries of the write
    final var testThread = Thread.currentThread();
    final var backend =
        backendWithHook(
            method -> {
              if (method.getName().equals("weigh") && Thread.currentThread() != testThread) {
                throw new OutOfMemoryError("expected failure of the writer");
              }
            });
    store =
        new WriteBehindStore<>(
            backend, WriteBehindConfiguration.defaults().withFlushDelay(Duration.ZERO));

    // when
    commit(store, key(1), held(1));

    // then - without a failure, the committing thread would not notice the dead writer
    await()
        .untilAsserted(
            () ->
                assertThatThrownBy(() -> commit(store, key(2), held(2)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasRootCauseInstanceOf(OutOfMemoryError.class));
  }

  @Test
  void shouldGiveUpOnWriterThatIgnoresTheInterruptWhenClosing() throws Exception {
    // given - a writer that is stuck in a storage write, like in a native call, which an interrupt
    // doesn't end
    final var writing = new CountDownLatch(1);
    final var release = new CountDownLatch(1);
    final var backend =
        backendWithHook(
            method -> {
              if (method.getName().equals("newBatch")) {
                writing.countDown();
                awaitUninterruptibly(release);
              }
            });
    store =
        new WriteBehindStore<>(
            backend,
            new WriteBehindConfiguration(
                1L << 20, 1L << 20, 0, Duration.ofMillis(200), Duration.ZERO, 1L << 19));
    commit(store, key(1), held(1));
    assertThat(writing.await(10, TimeUnit.SECONDS)).isTrue();

    // when - then
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(10), store::close);
    } finally {
      release.countDown();
    }
  }

  /** A backend on in-memory storage that calls {@code hook} before each of its methods. */
  private static Backend<RawKey, HeldValue, DbValue> backendWithHook(final Hook hook) {
    final var raw = new RawBackend(new InMemoryRawDbAccess());
    @SuppressWarnings("unchecked")
    final var backend =
        (Backend<RawKey, HeldValue, DbValue>)
            Proxy.newProxyInstance(
                Backend.class.getClassLoader(),
                new Class<?>[] {Backend.class},
                (proxy, method, args) -> {
                  hook.before(method);
                  try {
                    return method.invoke(raw, args);
                  } catch (final InvocationTargetException e) {
                    throw e.getCause();
                  }
                });
    return backend;
  }

  private static void awaitUninterruptibly(final CountDownLatch latch) {
    boolean interrupted = false;
    while (true) {
      try {
        latch.await();
        break;
      } catch (final InterruptedException e) {
        interrupted = true;
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  @FunctionalInterface
  private interface Hook {
    void before(Method method);
  }
}
