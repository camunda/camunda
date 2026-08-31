/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.fs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.camunda.zeebe.journal.fs.LibC.InvalidLibC;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class LibCTest {

  @Test
  void shouldLoadSystemLibC() {
    assertThatNoException().isThrownBy(LibC::ofNativeLibrary);
  }

  @Test
  void shouldReturnInvalidLibCWhenNotFound() {
    // given
    final var libraryName = "dzz";
    // when
    final var loaded = LibC.ofNativeLibrary(libraryName);
    // then
    assertThat(loaded).isInstanceOf(InvalidLibC.class);
  }

  /**
   * Regression test: {@link LibC#ofNativeLibrary()} must return a single, JVM-wide instance.
   *
   * <p>Binding a fresh instance per call means a fresh {@code LibraryLoader.loadLibrary} call each
   * time. Concurrent loads race on jnr-ffi's unsynchronized, JVM-global {@code
   * NativeRuntime.loadedLibraries} {@link java.util.WeakHashMap} — its {@code synchronized} load
   * methods lock the per-call {@code NativeLibrary} instance, so they provide no cross-thread
   * exclusion. A concurrent resize can then corrupt a bucket chain into a cycle, after which any
   * thread touching the poisoned bucket spins forever in {@code WeakHashMap.put} (RUNNABLE, no
   * monitor held — invisible to deadlock detection). This wedged raft partition bootstrap and
   * halted ScaleUpPartitionsTest, since each partition bootstrap used to trigger its own bind.
   *
   * <p>The livelock itself is probabilistic, so we assert the deterministic invariant that closes
   * the race window instead: all calls, from any thread, observe the same instance, proving the
   * native bind happens at most once per JVM.
   */
  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldReturnSameInstanceWhenLoadedConcurrently() throws Exception {
    // given
    final var threadCount = 16;
    final var callsPerThread = 32;
    final var barrier = new CyclicBarrier(threadCount);

    // when - all threads start binding at the same time, as concurrently as possible
    final List<LibC> instances;
    try (final var executor = Executors.newFixedThreadPool(threadCount)) {
      final List<Callable<List<LibC>>> tasks =
          IntStream.range(0, threadCount)
              .<Callable<List<LibC>>>mapToObj(
                  i ->
                      () -> {
                        barrier.await(10, TimeUnit.SECONDS);
                        return IntStream.range(0, callsPerThread)
                            .mapToObj(j -> LibC.ofNativeLibrary())
                            .collect(Collectors.toList());
                      })
              .collect(Collectors.toList());

      instances =
          executor.invokeAll(tasks, 20, TimeUnit.SECONDS).stream()
              .map(LibCTest::getUnchecked)
              .flatMap(List::stream)
              .collect(Collectors.toList());
    }

    // then - every call observed the exact same instance, i.e. the library was bound at most once
    assertThat(instances).hasSize(threadCount * callsPerThread);
    assertThat(instances).allSatisfy(libC -> assertThat(libC).isSameAs(instances.get(0)));
  }

  private static <T> T getUnchecked(final Future<T> future) {
    try {
      return future.get();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
