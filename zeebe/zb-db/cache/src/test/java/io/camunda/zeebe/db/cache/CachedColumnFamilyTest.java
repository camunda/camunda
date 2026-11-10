/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import static io.camunda.zeebe.db.cache.TestColumnFamily.FIRST;
import static io.camunda.zeebe.db.cache.TestColumnFamily.SECOND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbForeignKey.MatchType;
import io.camunda.zeebe.db.impl.DbLong;
import java.time.Duration;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class CachedColumnFamilyTest {

  private static final ConsistencyChecksSettings CHECKS_ON =
      new ConsistencyChecksSettings(true, true);

  private final InMemoryBackend backend = new InMemoryBackend();
  private final WriteBehindStore<TestColumnFamily> store = stalledStore();
  private final CachedTransactionContext<TestColumnFamily> context =
      new CachedTransactionContext<>(store, backend.db());
  private final CachedTransactionContext<TestColumnFamily> otherContext =
      new CachedTransactionContext<>(store, backend.db());

  @AfterEach
  void closeStore() {
    store.close();
  }

  @Test
  void shouldSeeOwnWritesBeforeTheyCommit() {
    // given
    final var columnFamily = longColumnFamily(context, FIRST, CHECKS_ON);
    final var otherColumnFamily = longColumnFamily(otherContext, FIRST, CHECKS_ON);

    // when
    context.runInTransaction(
        () -> {
          columnFamily.insert(dbLong(1), dbLong(10));

          // then
          assertThat(columnFamily.get(dbLong(1))).extracting(DbLong::getValue).isEqualTo(10L);
          assertThat(otherColumnFamily.exists(dbLong(1))).isFalse();
        });
  }

  @Test
  void shouldPublishWritesToOtherContextsOnCommit() {
    // given
    final var columnFamily = longColumnFamily(context, FIRST, CHECKS_ON);
    final var otherColumnFamily = longColumnFamily(otherContext, FIRST, CHECKS_ON);

    // when
    context.runInTransaction(
        () -> {
          columnFamily.insert(dbLong(1), dbLong(10));
          columnFamily.insert(dbLong(2), dbLong(20));
        });

    // then
    assertThat(otherColumnFamily.get(dbLong(1))).extracting(DbLong::getValue).isEqualTo(10L);
    assertThat(otherColumnFamily.get(dbLong(2))).extracting(DbLong::getValue).isEqualTo(20L);
    assertThat(backend.get(FIRST, serialize(dbLong(1)))).isNull();
  }

  @Test
  void shouldDiscardWritesWhenTheTransactionFails() {
    // given
    final var columnFamily = longColumnFamily(context, FIRST, CHECKS_ON);
    final var otherColumnFamily = longColumnFamily(otherContext, FIRST, CHECKS_ON);

    // when
    assertThatThrownBy(
            () ->
                context.runInTransaction(
                    () -> {
                      columnFamily.insert(dbLong(1), dbLong(10));
                      throw new IllegalStateException("expected");
                    }))
        .hasMessage("expected");

    // then
    assertThat(columnFamily.exists(dbLong(1))).isFalse();
    assertThat(otherColumnFamily.exists(dbLong(1))).isFalse();
  }

  @Test
  void shouldReadDeletesOfTheTransactionAndOfOtherCommits() {
    // given
    final var columnFamily = longColumnFamily(context, FIRST, CHECKS_ON);
    final var otherColumnFamily = longColumnFamily(otherContext, FIRST, CHECKS_ON);
    columnFamily.insert(dbLong(1), dbLong(10));
    columnFamily.insert(dbLong(2), dbLong(20));

    // when
    context.runInTransaction(
        () -> {
          columnFamily.deleteExisting(dbLong(1));
          columnFamily.deleteIfExists(dbLong(2));

          assertThat(columnFamily.get(dbLong(1))).isNull();
          assertThat(otherColumnFamily.exists(dbLong(1))).isTrue();
        });

    // then
    assertThat(otherColumnFamily.get(dbLong(1))).isNull();
    assertThat(otherColumnFamily.exists(dbLong(2))).isFalse();
  }

  @Test
  void shouldReturnANewValueInstanceFromTheSupplier() {
    // given
    final var columnFamily = longColumnFamily(context, FIRST, CHECKS_ON);
    columnFamily.insert(dbLong(1), dbLong(10));
    final var instance = new DbLong();

    // when
    final var value = columnFamily.get(dbLong(1), () -> instance);

    // then
    assertThat(value).isSameAs(instance).extracting(DbLong::getValue).isEqualTo(10L);
  }

  @Test
  void shouldCheckPreconditionsAgainstCommitsTheBackendDoesNotHaveYet() {
    // given
    longColumnFamily(context, FIRST, CHECKS_ON).insert(dbLong(1), dbLong(10));
    final var otherColumnFamily = longColumnFamily(otherContext, FIRST, CHECKS_ON);

    // then
    assertThatThrownBy(() -> otherColumnFamily.insert(dbLong(1), dbLong(11)))
        .isInstanceOf(ZeebeDbInconsistentException.class)
        .hasMessageContaining("already exists");
    assertThatThrownBy(() -> otherColumnFamily.update(dbLong(2), dbLong(20)))
        .isInstanceOf(ZeebeDbInconsistentException.class)
        .hasMessageContaining("does not exist");
    assertThatThrownBy(() -> otherColumnFamily.deleteExisting(dbLong(2)))
        .isInstanceOf(ZeebeDbInconsistentException.class)
        .hasMessageContaining("does not exist");
  }

  @Test
  void shouldSkipPreconditionsWhenTheyAreDisabled() {
    // given
    final var columnFamily =
        longColumnFamily(context, FIRST, new ConsistencyChecksSettings(false, false));
    columnFamily.insert(dbLong(1), dbLong(10));

    // when
    columnFamily.insert(dbLong(1), dbLong(11));
    columnFamily.update(dbLong(2), dbLong(20));

    // then
    assertThat(columnFamily.get(dbLong(1))).extracting(DbLong::getValue).isEqualTo(11L);
    assertThat(columnFamily.get(dbLong(2))).extracting(DbLong::getValue).isEqualTo(20L);
  }

  @Test
  void shouldCheckForeignKeysAgainstCommitsTheBackendDoesNotHaveYet() {
    // given
    final var referencing =
        new CachedColumnFamily<>(
            store, otherContext, SECOND, foreignKey(0), new DbLong(), CHECKS_ON);

    // when - then
    assertThatThrownBy(() -> referencing.insert(foreignKey(1), dbLong(10)))
        .isInstanceOf(ZeebeDbInconsistentException.class)
        .hasMessageContaining("Foreign key");

    // when
    longColumnFamily(context, FIRST, CHECKS_ON).insert(dbLong(1), dbLong(10));
    referencing.insert(foreignKey(1), dbLong(10));

    // then
    assertThat(referencing.exists(foreignKey(1))).isTrue();
  }

  @Test
  void shouldCheckPrefixForeignKeysAgainstCommitsTheBackendDoesNotHaveYet() {
    // given
    final var referencing =
        new CachedColumnFamily<>(
            store,
            otherContext,
            SECOND,
            new DbForeignKey<>(new DbLong(), FIRST, MatchType.Prefix),
            new DbLong(),
            CHECKS_ON);
    final var prefixKey = new DbForeignKey<>(dbLong(1), FIRST, MatchType.Prefix);

    // when - then
    assertThatThrownBy(() -> referencing.insert(prefixKey, dbLong(10)))
        .isInstanceOf(ZeebeDbInconsistentException.class)
        .hasMessageContaining("does not exist as prefix");

    // when
    longColumnFamily(context, FIRST, CHECKS_ON).insert(dbLong(1), dbLong(10));
    referencing.insert(prefixKey, dbLong(10));

    // then
    assertThat(referencing.exists(prefixKey)).isTrue();
  }

  private CachedColumnFamily<TestColumnFamily, DbLong, DbLong> longColumnFamily(
      final CachedTransactionContext<TestColumnFamily> context,
      final TestColumnFamily columnFamily,
      final ConsistencyChecksSettings settings) {
    return new CachedColumnFamily<>(
        store, context, columnFamily, new DbLong(), new DbLong(), settings);
  }

  private WriteBehindStore<TestColumnFamily> stalledStore() {
    return new WriteBehindStore<>(
        backend.db(), new TestConfiguration().maxWriteDelay(Duration.ofHours(1)).build());
  }

  private static DbForeignKey<DbLong> foreignKey(final long value) {
    return new DbForeignKey<>(dbLong(value), FIRST);
  }

  private static DbLong dbLong(final long value) {
    final var dbLong = new DbLong();
    dbLong.wrapLong(value);
    return dbLong;
  }

  private static byte[] serialize(final DbLong key) {
    final var buffer = new UnsafeBuffer(new byte[key.getLength()]);
    key.write(buffer, 0);
    return buffer.byteArray();
  }
}
