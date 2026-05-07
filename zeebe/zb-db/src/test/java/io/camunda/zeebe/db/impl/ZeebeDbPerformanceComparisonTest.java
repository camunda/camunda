/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDbFactory;
import java.io.File;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Comparative performance test for RocksDB, InMemory, and Layered DB implementations.
 *
 * <p>Simulates a typical engine workload with a realistic DB size: 10,000 background entries are
 * pre-populated before the measured run. The workload uses composite keys {@code (scopeKey,
 * varIndex)} to mirror the real VARIABLES column family layout, and includes prefix iteration
 * ({@code whileEqualPrefix}) and prefix delete ({@code deleteByPrefix}) which are the hot paths for
 * variable collection and scope cleanup.
 */
final class ZeebeDbPerformanceComparisonTest {

  private static final int BACKGROUND_ENTRIES = 10_000;
  private static final int SCOPE_COUNT = 5_000;
  private static final int VARS_PER_SCOPE = 10;
  private static final int WARMUP_SCOPES = 500;

  static Stream<Arguments> dbFactories() {
    return Stream.of(
        Arguments.of("RocksDB", DefaultZeebeDbFactory.getDefaultFactory()),
        Arguments.of("InMemory", DefaultZeebeDbFactory.inMemoryFactory()),
        Arguments.of("Layered", DefaultZeebeDbFactory.layeredFactory()));
  }

  /** Workload with individual per-key deletes. */
  @ParameterizedTest(name = "{0} – point-delete")
  @MethodSource("dbFactories")
  void shouldMeasureWorkloadWithPointDeletes(
      final String label,
      final ZeebeDbFactory<DefaultColumnFamily> factory,
      @TempDir final File dir)
      throws Exception {

    try (final var db = factory.createDb(dir)) {
      final var ctx = db.createContext();
      final var scopeKey = new DbLong();
      final var varIndex = new DbLong();
      final var key = new DbCompositeKey<>(scopeKey, varIndex);
      final var value = new DbLong();
      final var cf = db.createColumnFamily(DefaultColumnFamily.DEFAULT, ctx, key, value);

      populateBackground(cf, ctx, scopeKey, varIndex, key, value);
      final long bgCount = cf.count();

      runWorkload(cf, ctx, scopeKey, varIndex, key, value, WARMUP_SCOPES, 0, false);

      final long start = System.nanoTime();
      runWorkload(cf, ctx, scopeKey, varIndex, key, value, SCOPE_COUNT, WARMUP_SCOPES, false);
      final long elapsed = System.nanoTime() - start;

      printResult(label + " point-delete", elapsed);
      assertThat(cf.count()).as("only background entries remain").isEqualTo(bgCount);
    }
  }

  /** Same workload but scope cleanup uses deleteByPrefix. */
  @ParameterizedTest(name = "{0} – prefix-delete")
  @MethodSource("dbFactories")
  void shouldMeasureWorkloadWithPrefixDeletes(
      final String label,
      final ZeebeDbFactory<DefaultColumnFamily> factory,
      @TempDir final File dir)
      throws Exception {

    try (final var db = factory.createDb(dir)) {
      final var ctx = db.createContext();
      final var scopeKey = new DbLong();
      final var varIndex = new DbLong();
      final var key = new DbCompositeKey<>(scopeKey, varIndex);
      final var value = new DbLong();
      final var cf = db.createColumnFamily(DefaultColumnFamily.DEFAULT, ctx, key, value);

      populateBackground(cf, ctx, scopeKey, varIndex, key, value);
      final long bgCount = cf.count();

      runWorkload(cf, ctx, scopeKey, varIndex, key, value, WARMUP_SCOPES, 0, true);

      final long start = System.nanoTime();
      runWorkload(cf, ctx, scopeKey, varIndex, key, value, SCOPE_COUNT, WARMUP_SCOPES, true);
      final long elapsed = System.nanoTime() - start;

      printResult(label + " prefix-delete", elapsed);
      assertThat(cf.count()).as("only background entries remain").isEqualTo(bgCount);
    }
  }

  // ---------------------------------------------------------------------------

  private void runWorkload(
      final ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbLong> cf,
      final TransactionContext ctx,
      final DbLong scopeKey,
      final DbLong varIndex,
      final DbCompositeKey<DbLong, DbLong> key,
      final DbLong value,
      final int scopes,
      final int scopeOffset,
      final boolean usePrefixDelete) {

    for (int s = 0; s < scopes; s++) {
      final long sk = s + scopeOffset + 1L;

      // insert
      ctx.runInTransaction(
          () -> {
            scopeKey.wrapLong(sk);
            for (int v = 0; v < VARS_PER_SCOPE; v++) {
              varIndex.wrapLong(v);
              value.wrapLong(v * 100L);
              cf.insert(key, value);
            }
          });

      // point reads
      ctx.runInTransaction(
          () -> {
            scopeKey.wrapLong(sk);
            for (int v = 0; v < VARS_PER_SCOPE; v++) {
              varIndex.wrapLong(v);
              cf.get(key);
            }
          });

      // prefix iteration — hot path for getVariablesAsDocument
      ctx.runInTransaction(
          () -> {
            scopeKey.wrapLong(sk);
            cf.whileEqualPrefix(scopeKey, (k, val) -> true);
          });

      // update
      ctx.runInTransaction(
          () -> {
            scopeKey.wrapLong(sk);
            for (int v = 0; v < VARS_PER_SCOPE; v++) {
              varIndex.wrapLong(v);
              value.wrapLong(v * 200L);
              cf.update(key, value);
            }
          });

      // read again
      ctx.runInTransaction(
          () -> {
            scopeKey.wrapLong(sk);
            for (int v = 0; v < VARS_PER_SCOPE; v++) {
              varIndex.wrapLong(v);
              cf.get(key);
            }
          });

      // delete scope
      if (usePrefixDelete) {
        ctx.runInTransaction(
            () -> {
              scopeKey.wrapLong(sk);
              cf.deleteByPrefix(scopeKey);
            });
      } else {
        ctx.runInTransaction(
            () -> {
              scopeKey.wrapLong(sk);
              for (int v = 0; v < VARS_PER_SCOPE; v++) {
                varIndex.wrapLong(v);
                cf.deleteExisting(key);
              }
            });
      }
    }
  }

  private void populateBackground(
      final ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbLong> cf,
      final TransactionContext ctx,
      final DbLong scopeKey,
      final DbLong varIndex,
      final DbCompositeKey<DbLong, DbLong> key,
      final DbLong value) {
    final int bgScopes = BACKGROUND_ENTRIES / 10;
    for (int s = 0; s < bgScopes; s++) {
      final int scope = s;
      ctx.runInTransaction(
          () -> {
            scopeKey.wrapLong(-(scope + 1L));
            for (int v = 0; v < 10; v++) {
              varIndex.wrapLong(v);
              value.wrapLong(scope * 1000L + v);
              cf.insert(key, value);
            }
          });
    }
  }

  private void printResult(final String label, final long elapsedNanos) {
    final long totalOps = (long) SCOPE_COUNT * VARS_PER_SCOPE * 6L;
    final double opsPerSec = totalOps / (elapsedNanos / 1_000_000_000.0);
    final double msTotal = elapsedNanos / 1_000_000.0;
    System.out.printf(
        "[%-25s] %,d bg + %,d scopes × %d vars  →  %,.0f ops/sec  (%.0f ms)%n",
        label, BACKGROUND_ENTRIES, SCOPE_COUNT, VARS_PER_SCOPE, opsPerSec, msTotal);
  }
}
