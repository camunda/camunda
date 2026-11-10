/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import static io.camunda.zeebe.db.cache.TestColumnFamily.FIRST;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Scans over three levels: the backend holds 1, 3 and 5; another context has committed, but not yet
 * flushed, 2, a delete of 3 and 55 for 5; the scanning transaction writes 4, deletes 1 and writes
 * 6. The merged view is 2, 4, 5 = 55, 6.
 */
final class CachedColumnFamilyScanTest {

  private static final ConsistencyChecksSettings CHECKS_ON =
      new ConsistencyChecksSettings(true, true);

  private final InMemoryBackend backend = new InMemoryBackend();
  private final WriteBehindStore<TestColumnFamily> store =
      new WriteBehindStore<>(
          backend.db(), new TestConfiguration().maxWriteDelay(Duration.ofHours(1)).build());
  private final CachedTransactionContext<TestColumnFamily> context =
      new CachedTransactionContext<>(store, backend.db());
  private final CachedColumnFamily<TestColumnFamily, DbLong, DbLong> columnFamily =
      new CachedColumnFamily<>(store, context, FIRST, new DbLong(), new DbLong(), CHECKS_ON);

  @BeforeEach
  void setUpBackendAndUnflushedCommits() {
    backend.put(FIRST, serialize(dbLong(1)), serialize(dbLong(10)));
    backend.put(FIRST, serialize(dbLong(3)), serialize(dbLong(30)));
    backend.put(FIRST, serialize(dbLong(5)), serialize(dbLong(50)));

    final var otherContext = new CachedTransactionContext<>(store, backend.db());
    final var other =
        new CachedColumnFamily<>(store, otherContext, FIRST, new DbLong(), new DbLong(), CHECKS_ON);
    otherContext.runInTransaction(
        () -> {
          other.insert(dbLong(2), dbLong(20));
          other.deleteExisting(dbLong(3));
          other.update(dbLong(5), dbLong(55));
        });
  }

  @AfterEach
  void closeStore() {
    store.close();
  }

  @Test
  void shouldMergeAllLevelsInKeyOrder() {
    // when
    final var entries = new ArrayList<List<Long>>();
    inScanningTransaction(
        () ->
            columnFamily.forEach(
                (key, value) -> entries.add(List.of(key.getValue(), value.getValue()))));

    // then
    assertThat(entries)
        .containsExactly(List.of(2L, 20L), List.of(4L, 40L), List.of(5L, 55L), List.of(6L, 60L));
  }

  @Test
  void shouldMergeAllLevelsInReverse() {
    // when
    final var keys = new ArrayList<Long>();
    inScanningTransaction(
        () ->
            columnFamily.whileTrueReverse(
                dbLong(6),
                (key, value) -> {
                  keys.add(key.getValue());
                  return true;
                }));

    // then
    assertThat(keys).containsExactly(6L, 5L, 4L, 2L);
  }

  @Test
  void shouldStartAtTheGivenKey() {
    // when
    final var keys = new ArrayList<Long>();
    inScanningTransaction(
        () ->
            columnFamily.whileTrue(
                dbLong(3),
                (key, value) -> {
                  keys.add(key.getValue());
                  return true;
                }));

    // then
    assertThat(keys).containsExactly(4L, 5L, 6L);
  }

  @Test
  void shouldScanKeysOnly() {
    // when
    final var keys = new ArrayList<Long>();
    inScanningTransaction(() -> columnFamily.forEachKey(key -> keys.add(key.getValue())));

    // then
    assertThat(keys).containsExactly(2L, 4L, 5L, 6L);
  }

  @Test
  void shouldStopWhenTheVisitorSaysSo() {
    // when
    final var keys = new ArrayList<Long>();
    inScanningTransaction(
        () ->
            columnFamily.whileTrue(
                (key, value) -> {
                  keys.add(key.getValue());
                  return keys.size() < 2;
                }));

    // then
    assertThat(keys).containsExactly(2L, 4L);
  }

  @Test
  void shouldCountAndCheckEmptinessOverTheMergedView() {
    inScanningTransaction(
        () -> {
          assertThat(columnFamily.count()).isEqualTo(4);
          assertThat(columnFamily.isEmpty()).isFalse();

          List.of(2L, 4L, 5L, 6L).forEach(key -> columnFamily.deleteExisting(dbLong(key)));

          assertThat(columnFamily.count()).isZero();
          assertThat(columnFamily.isEmpty()).isTrue();
        });
  }

  @Test
  void shouldHideKeysDeletedWhileIterating() {
    // when - visiting 2 deletes 5, which only the unflushed commit has the latest version of
    final var keys = new ArrayList<Long>();
    inScanningTransaction(
        () ->
            columnFamily.forEach(
                (key, value) -> {
                  keys.add(key.getValue());
                  if (key.getValue() == 2L) {
                    columnFamily.deleteExisting(dbLong(5));
                  }
                }));

    // then
    assertThat(keys).containsExactly(2L, 4L, 6L);
  }

  @Test
  void shouldStayWithinThePrefix() {
    // given
    final var firstPart = new DbString();
    final var secondPart = new DbLong();
    final var compositeKey = new DbCompositeKey<>(firstPart, secondPart);
    final var composite =
        new CachedColumnFamily<>(
            store, context, TestColumnFamily.SECOND, compositeKey, new DbLong(), CHECKS_ON);
    backend.put(TestColumnFamily.SECOND, serialize(compositeKey("a", 1)), serialize(dbLong(1)));
    backend.put(TestColumnFamily.SECOND, serialize(compositeKey("b", 1)), serialize(dbLong(3)));

    // when
    final var secondParts = new ArrayList<Long>();
    context.runInTransaction(
        () -> {
          firstPart.wrapString("a");
          secondPart.wrapLong(2);
          composite.insert(compositeKey, dbLong(2));
          firstPart.wrapString("a");
          composite.whileEqualPrefix(
              firstPart,
              (key, value) -> {
                secondParts.add(key.second().getValue());
                return true;
              });
        });

    // then
    assertThat(secondParts).containsExactly(1L, 2L);
  }

  private void inScanningTransaction(final Runnable scan) {
    context.runInTransaction(
        () -> {
          columnFamily.insert(dbLong(4), dbLong(40));
          columnFamily.deleteExisting(dbLong(1));
          columnFamily.insert(dbLong(6), dbLong(60));
          scan.run();
        });
  }

  private static DbCompositeKey<DbString, DbLong> compositeKey(
      final String first, final long second) {
    final var firstPart = new DbString();
    firstPart.wrapString(first);
    return new DbCompositeKey<>(firstPart, dbLong(second));
  }

  private static DbLong dbLong(final long value) {
    final var dbLong = new DbLong();
    dbLong.wrapLong(value);
    return dbLong;
  }

  private static byte[] serialize(final BufferWriter writer) {
    final var buffer = new UnsafeBuffer(new byte[writer.getLength()]);
    writer.write(buffer, 0);
    return buffer.byteArray();
  }
}
