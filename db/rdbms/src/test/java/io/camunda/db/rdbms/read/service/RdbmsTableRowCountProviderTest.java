/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.config.VendorDatabasePropertiesLoader;
import io.camunda.db.rdbms.sql.TableMetricsMapper;
import io.camunda.db.rdbms.sql.TableMetricsMapper.TableRowCount;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

  private RdbmsTableRowCountProvider provider(final Duration cacheDuration) throws IOException {
    // mssql: batched lookup, no case folding.
    return new RdbmsTableRowCountProvider(
        mapper, vendorProperties("mssql"), "", cacheDuration, executor);
  }

  private static VendorDatabaseProperties vendorProperties(final String databaseId)
      throws IOException {
    return VendorDatabasePropertiesLoader.load(databaseId);
  }

  @Test
  void shouldReturnRowCountFromMapper() throws Exception {
    // given
    when(mapper.countTableRows(anyList()))
        .thenReturn(List.of(new TableRowCount("PROCESS_INSTANCE", 42L)));
    final var provider = provider(DEFAULT_CACHE_DURATION);

    // when - the first load happens asynchronously, off this thread
    provider.getRowCount("PROCESS_INSTANCE");
    awaitPendingLoad();

    // then
    assertThat(provider.getRowCount("PROCESS_INSTANCE")).isEqualTo(42L);
  }

  @Test
  void shouldReturnNegativeOneForATableMissingFromTheBatchedResult() throws Exception {
    // given
    when(mapper.countTableRows(anyList()))
        .thenReturn(List.of(new TableRowCount("PROCESS_INSTANCE", 42L)));
    final var provider = provider(DEFAULT_CACHE_DURATION);

    // when
    provider.getRowCount("PROCESS_INSTANCE");
    awaitPendingLoad();

    // then
    assertThat(provider.getRowCount("JOB")).isEqualTo(-1L);
  }

  @Test
  void shouldFetchEveryTableInASingleBatchedCall() throws Exception {
    // given
    when(mapper.countTableRows(anyList()))
        .thenReturn(List.of(new TableRowCount("PROCESS_INSTANCE", 42L)));
    final var provider = provider(DEFAULT_CACHE_DURATION);

    // when
    provider.getRowCount("PROCESS_INSTANCE");
    awaitPendingLoad();
    provider.getRowCount("JOB");
    provider.getRowCount("INCIDENT");

    // then
    verify(mapper, times(1)).countTableRows(anyList());
  }

  @Test
  void shouldReturnNegativeOneWhileFirstLoadIsStillInFlight() throws Exception {
    // given - the mapper call is parked until the test releases it, simulating a slow/stuck
    // database
    final var loadStarted = new CountDownLatch(1);
    final var releaseLoad = new CountDownLatch(1);
    when(mapper.countTableRows(anyList()))
        .thenAnswer(
            invocation -> {
              loadStarted.countDown();
              releaseLoad.await();
              return List.of(new TableRowCount("PROCESS_INSTANCE", 42L));
            });
    final var provider = provider(DEFAULT_CACHE_DURATION);

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
    when(mapper.countTableRows(anyList()))
        .thenReturn(List.of(new TableRowCount("PROCESS_INSTANCE", 42L)));
    final var provider = provider(Duration.ofHours(1));

    // when - wait for the first (async) load, then request the row count multiple times
    provider.getRowCount("PROCESS_INSTANCE");
    awaitPendingLoad();
    provider.getRowCount("PROCESS_INSTANCE");
    provider.getRowCount("PROCESS_INSTANCE");

    // then - mapper should only be called once due to caching
    verify(mapper, times(1)).countTableRows(anyList());
  }

  @Test
  void shouldReturnNegativeOneOnMapperException() throws Exception {
    // given
    when(mapper.countTableRows(anyList())).thenThrow(new RuntimeException("Database error"));
    final var provider = provider(DEFAULT_CACHE_DURATION);

    // when - the exception is caught during the async load, not just the initial default
    provider.getRowCount("PROCESS_INSTANCE");
    awaitPendingLoad();

    // then - the failed load is not cached; the next read retries (atLeastOnce: the retry count
    // is a Caffeine implementation detail)
    assertThat(provider.getRowCount("PROCESS_INSTANCE")).isEqualTo(-1L);
    verify(mapper, atLeastOnce()).countTableRows(anyList());
  }

  @Test
  void shouldRecoverOnNextAccessAfterAFailedFirstLoad() throws Exception {
    // given - the first load fails; the retry it triggers is parked until released, so its
    // in-flight state (-1) can be asserted deterministically instead of racing its completion
    final var retryStarted = new CountDownLatch(1);
    final var releaseRetry = new CountDownLatch(1);
    when(mapper.countTableRows(anyList()))
        .thenThrow(new RuntimeException("Database error"))
        .thenAnswer(
            invocation -> {
              retryStarted.countDown();
              releaseRetry.await();
              return List.of(new TableRowCount("PROCESS_INSTANCE", 42L));
            });
    final var provider = provider(DEFAULT_CACHE_DURATION);

    // when
    provider.getRowCount("PROCESS_INSTANCE");
    awaitPendingLoad();
    assertThat(provider.getRowCount("PROCESS_INSTANCE")).isEqualTo(-1L);
    assertThat(retryStarted.await(5, TimeUnit.SECONDS))
        .as("the retry should have started")
        .isTrue();
    assertThat(provider.getRowCount("PROCESS_INSTANCE")).isEqualTo(-1L);

    // then - recovery happens on the next access, not after a full cacheDuration
    releaseRetry.countDown();
    awaitPendingLoad();
    assertThat(provider.getRowCount("PROCESS_INSTANCE")).isEqualTo(42L);
  }

  @Test
  void shouldKeepPreviousValueWhenRefreshFails() throws Exception {
    // given - the initial load succeeds; the subsequent refresh (triggered after cache expiry)
    // fails; the previously cached value must continue to be served
    final var refreshStarted = new CountDownLatch(1);
    final var releaseRefresh = new CountDownLatch(1);
    when(mapper.countTableRows(anyList()))
        .thenReturn(List.of(new TableRowCount("PROCESS_INSTANCE", 42L)))
        .thenAnswer(
            invocation -> {
              refreshStarted.countDown();
              releaseRefresh.await();
              throw new RuntimeException("Database error during refresh");
            });
    final var provider = provider(Duration.ofMillis(1));

    // load the initial value
    provider.getRowCount("PROCESS_INSTANCE");
    awaitPendingLoad();
    assertThat(provider.getRowCount("PROCESS_INSTANCE")).isEqualTo(42L);

    // when - let the cache entry become stale, then trigger the async refresh
    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              provider.getRowCount("PROCESS_INSTANCE");
              assertThat(refreshStarted.getCount()).isZero();
            });
    releaseRefresh.countDown(); // release the refresh, which then throws
    awaitPendingLoad(); // wait for the refresh task to finish

    // then - Caffeine retains the previous good value when reload() throws; must not return -1
    assertThat(provider.getRowCount("PROCESS_INSTANCE")).isEqualTo(42L);
  }

  @Test
  void shouldReturnNegativeOneForUnknownTable() throws Exception {
    // given
    final var provider = provider(DEFAULT_CACHE_DURATION);

    // when
    final long rowCount = provider.getRowCount("UNKNOWN_TABLE");

    // then - should return -1 and NOT call the mapper (validation prevents SQL injection)
    assertThat(rowCount).isEqualTo(-1L);
    verify(mapper, times(0)).countTableRows(anyList());
  }

  @Test
  void shouldFoldAndPrefixSearchNamesPerVendorBeforeBinding() throws Exception {
    // given - PostgreSQL folds to lower case, and the prefix is applied before folding
    when(mapper.countTableRows(anyList()))
        .thenReturn(List.of(new TableRowCount("tenant_process_instance", 42L)));
    final var provider =
        new RdbmsTableRowCountProvider(
            mapper, vendorProperties("postgresql"), "TENANT_", DEFAULT_CACHE_DURATION, executor);

    // when
    provider.getRowCount("PROCESS_INSTANCE");
    awaitPendingLoad();

    // then - the folded, prefixed identifier was actually bound
    final var boundNames = ArgumentCaptor.forClass(List.class);
    verify(mapper).countTableRows(boundNames.capture());
    assertThat(boundNames.getValue()).contains("tenant_process_instance");

    // and
    assertThat(provider.getRowCount("PROCESS_INSTANCE")).isEqualTo(42L);
  }

  @Test
  void shouldUpperCaseFoldSearchNamesForOracle() throws Exception {
    // given - Oracle folds to upper case
    when(mapper.countTableRows(anyList()))
        .thenReturn(List.of(new TableRowCount("TENANT_PROCESS_INSTANCE", 7L)));
    final var provider =
        new RdbmsTableRowCountProvider(
            mapper, vendorProperties("oracle"), "tenant_", DEFAULT_CACHE_DURATION, executor);

    // when
    provider.getRowCount("PROCESS_INSTANCE");
    awaitPendingLoad();

    // then
    final var boundNames = ArgumentCaptor.forClass(List.class);
    verify(mapper).countTableRows(boundNames.capture());
    assertThat(boundNames.getValue()).contains("TENANT_PROCESS_INSTANCE");
    assertThat(provider.getRowCount("PROCESS_INSTANCE")).isEqualTo(7L);
  }

  @Test
  void shouldFetchOneTableAtATimeForAVendorWithoutBatchedLookup() throws Exception {
    // given - H2 counts one table at a time; JOB fails, PROCESS_INSTANCE must not be blanked
    when(mapper.countSingleTableRows("PROCESS_INSTANCE")).thenReturn(42L);
    when(mapper.countSingleTableRows("JOB")).thenThrow(new RuntimeException("no such table"));
    final var provider =
        new RdbmsTableRowCountProvider(
            mapper, vendorProperties("h2"), "", DEFAULT_CACHE_DURATION, executor);

    // when
    provider.getRowCount("PROCESS_INSTANCE");
    awaitPendingLoad();

    // then
    assertThat(provider.getRowCount("PROCESS_INSTANCE")).isEqualTo(42L);
    assertThat(provider.getRowCount("JOB")).isEqualTo(-1L);
    verify(mapper, times(0)).countTableRows(anyList());
  }

  @Test
  void shouldKeepPreviousValueWhenALiveCountFailsDuringRefresh() throws Exception {
    // given - a table that counted successfully before must not drop to -1 when a refresh fails
    final var refreshStarted = new CountDownLatch(1);
    final var releaseRefresh = new CountDownLatch(1);
    when(mapper.countSingleTableRows("PROCESS_INSTANCE")).thenReturn(42L);
    when(mapper.countSingleTableRows("JOB"))
        .thenReturn(7L)
        .thenAnswer(
            invocation -> {
              refreshStarted.countDown();
              releaseRefresh.await();
              throw new RuntimeException("Database error during refresh");
            });
    final var provider =
        new RdbmsTableRowCountProvider(
            mapper, vendorProperties("h2"), "", Duration.ofMillis(1), executor);

    // load the initial values
    provider.getRowCount("JOB");
    awaitPendingLoad();
    assertThat(provider.getRowCount("JOB")).isEqualTo(7L);

    // when - let the cache entry become stale, then trigger the async refresh
    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              provider.getRowCount("JOB");
              assertThat(refreshStarted.getCount()).isZero();
            });
    releaseRefresh.countDown(); // release the refresh, which then throws for JOB only
    awaitPendingLoad(); // wait for the refresh task to finish

    // then
    assertThat(provider.getRowCount("JOB")).isEqualTo(7L);
    assertThat(provider.getRowCount("PROCESS_INSTANCE")).isEqualTo(42L);
  }

  @Test
  void shouldNotReportAStaleCountForATableThatNeverLoadedSuccessfully() throws Exception {
    // given - a table that never counted successfully has no previous value to keep
    final var refreshStarted = new CountDownLatch(1);
    when(mapper.countSingleTableRows("PROCESS_INSTANCE")).thenReturn(42L);
    when(mapper.countSingleTableRows("JOB"))
        .thenThrow(new RuntimeException("no such table"))
        .thenAnswer(
            invocation -> {
              refreshStarted.countDown();
              throw new RuntimeException("no such table");
            });
    final var provider =
        new RdbmsTableRowCountProvider(
            mapper, vendorProperties("h2"), "", Duration.ofMillis(1), executor);

    // when
    provider.getRowCount("JOB");
    awaitPendingLoad();
    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              provider.getRowCount("JOB");
              assertThat(refreshStarted.getCount()).isZero();
            });
    awaitPendingLoad();

    // then
    assertThat(provider.getRowCount("JOB")).isEqualTo(-1L);
  }
}
