/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.camunda.secretstore.SecretStore;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SecretStoreResourcesTest {

  @AutoClose SecretStoreResources resources;

  @AutoClose(value = "shutdownNow")
  ExecutorService pool;

  SecretStore store;

  @BeforeEach
  void beforeEach() {
    resources = new SecretStoreResources();
    pool = Executors.newVirtualThreadPerTaskExecutor();
    store = mock(SecretStore.class);
  }

  @Test
  void shouldLetInFlightResolutionsFinishBeforeClosingTheStore() throws Exception {
    // given a resolution still running on the pool when close is requested
    final var poolTerminatedWhenStoreClosed = new AtomicBoolean();
    doAnswer(
            invocation -> {
              poolTerminatedWhenStoreClosed.set(pool.isTerminated());
              return null;
            })
        .when(store)
        .close();
    resources.addPool(pool);
    resources.addStore(store);
    final var started = new CountDownLatch(1);
    final var release = new CountDownLatch(1);
    final var finished = new AtomicBoolean();
    pool.execute(
        () -> {
          started.countDown();
          try {
            release.await();
            finished.set(true);
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
    started.await();
    final var closer = Thread.ofVirtual().start(resources::close);

    // when the resolution completes while close is draining
    await().until(pool::isShutdown);
    release.countDown();
    closer.join();

    // then it was not interrupted, and the store is closed only after the pool terminated
    assertThat(finished).isTrue();
    assertThat(poolTerminatedWhenStoreClosed).isTrue();
    verify(store).close();
  }

  @Test
  void shouldInterruptResolutionsAndStillCloseStoresWhenCloseIsInterrupted() throws Exception {
    // given a resolution that never finishes on its own
    resources.addPool(pool);
    resources.addStore(store);
    final var started = new CountDownLatch(1);
    final var resolutionInterrupted = new CountDownLatch(1);
    pool.execute(
        () -> {
          started.countDown();
          try {
            new CountDownLatch(1).await();
          } catch (final InterruptedException e) {
            resolutionInterrupted.countDown();
          }
        });
    started.await();
    final var interruptRestored = new AtomicBoolean();
    final var closer =
        Thread.ofPlatform()
            .start(
                () -> {
                  resources.close();
                  interruptRestored.set(Thread.currentThread().isInterrupted());
                });

    // when close is interrupted while draining
    await().until(pool::isShutdown);
    closer.interrupt();
    closer.join();

    // then the resolution is interrupted, the store still closed, and the interrupt preserved
    assertThat(resolutionInterrupted.await(1, TimeUnit.SECONDS)).isTrue();
    verify(store).close();
    assertThat(interruptRestored).isTrue();
  }

  @Test
  void shouldStopTheTenantPoolsAndStillCloseTheRemainingStoresWhenOneFailsToClose() {
    // given a pool and two stores, the first of which fails to close
    final var failing = mock(SecretStore.class);
    final var healthy = mock(SecretStore.class);
    doThrow(new IllegalStateException("boom")).when(failing).close();
    resources.addPool(pool);
    resources.addStore(failing);
    resources.addStore(healthy);

    // when
    resources.close();

    // then
    assertThat(pool.isShutdown()).isTrue();
    final var order = inOrder(failing, healthy);
    order.verify(failing).close();
    order.verify(healthy).close();
  }

  @Test
  void shouldCloseTheRemainingMeterRegistriesWhenOneFailsToClose() {
    // given two wrapped registries, the first of which fails to close
    final var parent = new SimpleMeterRegistry();
    final var failing = mock(CompositeMeterRegistry.class);
    doThrow(new IllegalStateException("boom")).when(failing).clear();
    final var healthy = new CompositeMeterRegistry();
    healthy.add(parent);
    healthy.counter("test.counter");
    resources.addMeterRegistry(failing);
    resources.addMeterRegistry(healthy);

    // when
    resources.close();

    // then the second is cleared even though the first failed
    assertThat(healthy.getMeters()).isEmpty();
  }
}
