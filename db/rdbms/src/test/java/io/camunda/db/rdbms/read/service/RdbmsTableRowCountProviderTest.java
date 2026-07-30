/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.sql.TableMetricsMapper;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RdbmsTableRowCountProviderTest {

  private static final Duration DEFAULT_CACHE_DURATION = Duration.ofMinutes(15);

  private TableMetricsMapper mapper;
  private ExecutorService executor;

  @BeforeEach
  void setUp() {
    mapper = mock(TableMetricsMapper.class);
    // Single-threaded so a no-op task submitted after triggering a load can be used as a barrier:
    // once that no-op task completes, the earlier load is guaranteed to have finished too.
    executor = Executors.newSingleThreadExecutor();
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  private void awaitPendingLoad() throws Exception {
    executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
  }

  @Test
  void shouldReturnRowCountFromMapper() throws Exception {
    // given
    when(mapper.countTableRows("PROCESS_INSTANCE")).thenReturn(42L);
    final var provider = new RdbmsTableRowCountProvider(mapper, DEFAULT_CACHE_DURATION, executor);

    // when - the first load happens asynchronously, off this thread
    provider.getRowCount("PROCESS_INSTANCE");
    awaitPendingLoad();

    // then
    assertThat(provider.getRowCount("PROCESS_INSTANCE")).isEqualTo(42L);
  }

  @Test
  void shouldReturnNegativeOneWhileFirstLoadIsStillInFlight() throws Exception {
    // given - the mapper call is parked until the test releases it, simulating a slow/stuck
    // database
    final var loadStarted = new CountDownLatch(1);
    final var releaseLoad = new CountDownLatch(1);
    when(mapper.countTableRows("PROCESS_INSTANCE"))
        .thenAnswer(
            invocation -> {
              loadStarted.countDown();
              releaseLoad.await();
              return 42L;
            });
    final var provider = new RdbmsTableRowCountProvider(mapper, DEFAULT_CACHE_DURATION, executor);

    // when - the very first read triggers the load but must never block on it
    final long rowCountWhileLoading = provider.getRowCount("PROCESS_INSTANCE");
    assertThat(loadStarted.await(5, TimeUnit.SECONDS))
        .as("the async load should have started")
        .isTrue();

    // then
    assertThat(rowCountWhileLoading).isEqualTo(-1L);

    // and - once the load completes, the real value becomes available
    releaseLoad.countDown();
    awaitPendingLoad();
    assertThat(provider.getRowCount("PROCESS_INSTANCE")).isEqualTo(42L);
  }

  @Test
  void shouldCacheRowCountWithinCacheDuration() throws Exception {
    // given
    when(mapper.countTableRows("PROCESS_INSTANCE")).thenReturn(42L);
    final var provider = new RdbmsTableRowCountProvider(mapper, Duration.ofHours(1), executor);

    // when - wait for the first (async) load, then request the row count multiple times
    provider.getRowCount("PROCESS_INSTANCE");
    awaitPendingLoad();
    provider.getRowCount("PROCESS_INSTANCE");
    provider.getRowCount("PROCESS_INSTANCE");

    // then - mapper should only be called once due to caching
    verify(mapper, times(1)).countTableRows("PROCESS_INSTANCE");
  }

  @Test
  void shouldReturnNegativeOneOnMapperException() throws Exception {
    // given
    when(mapper.countTableRows("PROCESS_INSTANCE"))
        .thenThrow(new RuntimeException("Database error"));
    final var provider = new RdbmsTableRowCountProvider(mapper, DEFAULT_CACHE_DURATION, executor);

    // when - the exception is caught during the async load, not just the initial default
    provider.getRowCount("PROCESS_INSTANCE");
    awaitPendingLoad();

    // then
    assertThat(provider.getRowCount("PROCESS_INSTANCE")).isEqualTo(-1L);
    verify(mapper, times(1)).countTableRows("PROCESS_INSTANCE");
  }

  @Test
  void shouldKeepPreviousValueWhenRefreshFails() throws Exception {
    // given - the initial load succeeds; the subsequent refresh (triggered after cache expiry)
    // fails; the previously cached value must continue to be served
    final var refreshStarted = new CountDownLatch(1);
    final var releaseRefresh = new CountDownLatch(1);
    when(mapper.countTableRows("PROCESS_INSTANCE"))
        .thenReturn(42L)
        .thenAnswer(
            invocation -> {
              refreshStarted.countDown();
              releaseRefresh.await();
              throw new RuntimeException("Database error during refresh");
            });
    final var provider = new RdbmsTableRowCountProvider(mapper, Duration.ofMillis(1), executor);

    // load the initial value
    provider.getRowCount("PROCESS_INSTANCE");
    awaitPendingLoad();
    assertThat(provider.getRowCount("PROCESS_INSTANCE")).isEqualTo(42L);

    // when - let the cache entry become stale, then trigger the async refresh
    Thread.sleep(5); // 5× the 1 ms cache duration; ensures the entry is due for refresh
    provider.getRowCount("PROCESS_INSTANCE"); // triggers the async refresh off this thread
    assertThat(refreshStarted.await(5, TimeUnit.SECONDS))
        .as("the async refresh should have started")
        .isTrue();
    releaseRefresh.countDown(); // release the refresh, which then throws
    awaitPendingLoad(); // wait for the refresh task to finish

    // then - Caffeine retains the previous good value when reload() throws; must not return -1
    assertThat(provider.getRowCount("PROCESS_INSTANCE")).isEqualTo(42L);
  }

  @Test
  void shouldReturnNegativeOneForUnknownTable() {
    // given
    final var provider = new RdbmsTableRowCountProvider(mapper, DEFAULT_CACHE_DURATION, executor);

    // when
    final long rowCount = provider.getRowCount("UNKNOWN_TABLE");

    // then - should return -1 and NOT call the mapper (validation prevents SQL injection)
    assertThat(rowCount).isEqualTo(-1L);
    verify(mapper, times(0)).countTableRows("UNKNOWN_TABLE");
  }
}
