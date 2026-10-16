/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.sqlite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.db.conformance.ConformanceDbTestBase;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbEnumValue;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Point operations, transactions and snapshots of the SQLite backend. */
final class SqlitePointOperationsTest extends ConformanceDbTestBase {

  @TempDir Path snapshotParent;

  @Override
  protected <T extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ZeebeDbFactory<T> factory() {
    return new SqliteDbFactory<>();
  }

  @Test
  void shouldRejectInsertOfExistingKey() {
    // given
    final var cf = longColumnFamily(Cf.ONE);
    cf.insert(dbLong(1), dbLong(10));

    // then
    assertThatThrownBy(() -> cf.insert(dbLong(1), dbLong(20)))
        .isInstanceOf(ZeebeDbInconsistentException.class)
        .hasMessageContaining("already exists");
    assertThat(cf.get(dbLong(1)).getValue()).isEqualTo(10);
  }

  @Test
  void shouldRejectUpdateOfMissingKey() {
    // given
    final var cf = longColumnFamily(Cf.ONE);

    // then
    assertThatThrownBy(() -> cf.update(dbLong(1), dbLong(10)))
        .isInstanceOf(ZeebeDbInconsistentException.class)
        .hasMessageContaining("does not exist");
  }

  @Test
  void shouldUpdateExistingKey() {
    // given
    final var cf = longColumnFamily(Cf.ONE);
    cf.insert(dbLong(1), dbLong(10));

    // when
    cf.update(dbLong(1), dbLong(20));

    // then
    assertThat(cf.get(dbLong(1)).getValue()).isEqualTo(20);
  }

  @Test
  void shouldUpsertWhetherOrNotTheKeyExists() {
    // given
    final var cf = longColumnFamily(Cf.ONE);

    // when
    cf.upsert(dbLong(1), dbLong(10));
    cf.upsert(dbLong(1), dbLong(20));

    // then
    assertThat(cf.get(dbLong(1)).getValue()).isEqualTo(20);
    assertThat(cf.count()).isEqualTo(1);
  }

  @Test
  void shouldReturnNullForMissingKey() {
    // given
    final var cf = longColumnFamily(Cf.ONE);

    // then
    assertThat(cf.get(dbLong(1))).isNull();
    assertThat(cf.get(dbLong(1), DbLong::new)).isNull();
  }

  @Test
  void shouldReturnSharedValueInstanceFromGet() {
    // given
    final var value = new DbLong();
    final var cf = columnFamily(Cf.ONE, new DbLong(), value);
    cf.insert(dbLong(1), dbLong(10));
    cf.insert(dbLong(2), dbLong(20));

    // when
    final var first = cf.get(dbLong(1));
    final var second = cf.get(dbLong(2));

    // then
    assertThat(first).isSameAs(value).isSameAs(second);
    assertThat(first.getValue()).isEqualTo(20);
  }

  @Test
  void shouldAllocateValueViaSupplier() {
    // given
    final var cf = columnFamily(Cf.ONE, new DbLong(), new DbLong());
    cf.insert(dbLong(1), dbLong(10));
    cf.insert(dbLong(2), dbLong(20));

    // when
    final var first = cf.get(dbLong(1), DbLong::new);
    final var second = cf.get(dbLong(2), DbLong::new);

    // then
    assertThat(first).isNotSameAs(second);
    assertThat(first.getValue()).isEqualTo(10);
    assertThat(second.getValue()).isEqualTo(20);
  }

  @Test
  void shouldDeleteExistingOnlyIfItExists() {
    // given
    final var cf = longColumnFamily(Cf.ONE);
    cf.insert(dbLong(1), dbLong(10));

    // when
    cf.deleteExisting(dbLong(1));

    // then
    assertThat(cf.exists(dbLong(1))).isFalse();
    assertThatThrownBy(() -> cf.deleteExisting(dbLong(1)))
        .isInstanceOf(ZeebeDbInconsistentException.class)
        .hasMessageContaining("does not exist");
  }

  @Test
  void shouldIgnoreMissingKeyOnDeleteIfExists() {
    // given
    final var cf = longColumnFamily(Cf.ONE);
    cf.insert(dbLong(1), dbLong(10));

    // when
    cf.deleteIfExists(dbLong(2));
    cf.deleteIfExists(dbLong(1));

    // then
    assertThat(cf.isEmpty()).isTrue();
  }

  @Test
  void shouldKeepNegativeAndPositiveKeysApart() {
    // given
    final var cf = longColumnFamily(Cf.ONE);
    cf.insert(dbLong(-1), dbLong(1));
    cf.insert(dbLong(1), dbLong(2));

    // then
    assertThat(cf.get(dbLong(-1)).getValue()).isEqualTo(1);
    assertThat(cf.get(dbLong(1)).getValue()).isEqualTo(2);
  }

  @Test
  void shouldStoreKeysOfDifferentColumnFamiliesSeparately() {
    // given
    final var one = longColumnFamily(Cf.ONE);
    final var two = longColumnFamily(Cf.TWO);

    // when
    one.insert(dbLong(1), dbLong(10));

    // then
    assertThat(two.exists(dbLong(1))).isFalse();
  }

  @Test
  void shouldTreatEmptyOmittableStringAsDistinctFromPresentOne() {
    // given a key whose string is not serialized for the GLOBAL scope
    final var cf = columnFamily(Cf.ONE, new ScopedKey(), new DbLong());
    cf.insert(ScopedKey.of(Scope.GLOBAL, ""), dbLong(1));
    cf.insert(ScopedKey.of(Scope.TENANT, "a"), dbLong(2));
    cf.insert(ScopedKey.of(Scope.TENANT, "b"), dbLong(3));

    // then
    assertThat(cf.get(ScopedKey.of(Scope.GLOBAL, "")).getValue()).isEqualTo(1);
    assertThat(cf.get(ScopedKey.of(Scope.TENANT, "a")).getValue()).isEqualTo(2);
    assertThat(cf.exists(ScopedKey.of(Scope.TENANT, "c"))).isFalse();
    assertThat(cf.count()).isEqualTo(3);
  }

  @Test
  void shouldCountPrefixEndingInsideOmittableColumn() {
    // given
    final var cf = columnFamily(Cf.ONE, new ScopedKey(), new DbLong());
    cf.insert(ScopedKey.of(Scope.GLOBAL, ""), dbLong(1));
    cf.insert(ScopedKey.of(Scope.TENANT, "a"), dbLong(2));
    cf.insert(ScopedKey.of(Scope.TENANT, "b"), dbLong(3));
    final var tenantOnly = new DbEnumValue<>(Scope.class);
    tenantOnly.setValue(Scope.TENANT);

    // then
    assertThat(cf.countEqualPrefix(tenantOnly)).isEqualTo(2);
  }

  @Test
  void shouldCountPrefixEndingInsideBlobColumn() {
    // given
    final var cf = longColumnFamily(Cf.ONE);
    cf.insert(dbLong(1), dbLong(0));
    cf.insert(dbLong(2), dbLong(0));
    cf.insert(dbLong(1L << 40), dbLong(0));
    // the first four bytes of the big-endian long 1 and 2 are zero, those of 1 << 40 are not
    final var prefix = new DbBytes();
    prefix.wrapBytes(new byte[4]);

    // then
    assertThat(cf.countEqualPrefix(prefix)).isEqualTo(2);
  }

  @Test
  void shouldCountPrefixOfStringComponent() {
    // given
    final var cf =
        columnFamily(Cf.ONE, new DbCompositeKey<>(new DbString(), new DbLong()), new DbLong());
    cf.insert(stringLong("a", 1), dbLong(0));
    cf.insert(stringLong("a", 2), dbLong(0));
    cf.insert(stringLong("aa", 1), dbLong(0));

    // then: "a" is no prefix of "aa" because of the length prefix, as in RocksDB
    assertThat(cf.countEqualPrefix(dbString("a"))).isEqualTo(2);
    assertThat(cf.countEqualPrefix(dbString("aa"))).isEqualTo(1);
  }

  @Test
  void shouldRejectNegativeValueForNonNegativeComponent() {
    // given a foreign key declares its numeric components non-negative, so they are an INTEGER
    final var cf = columnFamily(Cf.ONE, new DbForeignKey<>(new DbLong(), Cf.TWO), new DbLong());

    // then: storing it would sort it below zero where RocksDB sorts it above
    assertThatThrownBy(() -> cf.insert(new DbForeignKey<>(dbLong(-1), Cf.TWO), dbLong(0)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("non-negative");
  }

  @Test
  void shouldReportColumnFamilyThatWasNeverCreatedAsEmpty() {
    // then
    assertThat(db.isEmpty(Cf.THREE, context)).isTrue();
  }

  @Test
  void shouldReadOwnUncommittedWritesInTransaction() {
    // given
    final var cf = longColumnFamily(Cf.ONE);

    // when
    context.runInTransaction(
        () -> {
          cf.insert(dbLong(1), dbLong(10));

          // then
          assertThat(cf.get(dbLong(1)).getValue()).isEqualTo(10);
          assertThat(cf.exists(dbLong(1))).isTrue();
          assertThat(cf.count()).isEqualTo(1);
        });
  }

  @Test
  void shouldRollBackWhenOperationFails() {
    // given
    final var cf = longColumnFamily(Cf.ONE);

    // when
    assertThatThrownBy(
            () ->
                context.runInTransaction(
                    () -> {
                      cf.insert(dbLong(1), dbLong(10));
                      throw new IllegalArgumentException("fail");
                    }))
        .isInstanceOf(IllegalArgumentException.class);

    // then
    assertThat(cf.exists(dbLong(1))).isFalse();
  }

  @Test
  void shouldOnlyCommitOutermostTransaction() {
    // given
    final var cf = longColumnFamily(Cf.ONE);

    // when the inner call returns and the outer one then fails
    assertThatThrownBy(
            () ->
                context.runInTransaction(
                    () -> {
                      context.runInTransaction(() -> cf.insert(dbLong(1), dbLong(10)));
                      throw new IllegalArgumentException("fail");
                    }))
        .isInstanceOf(IllegalArgumentException.class);

    // then the inner call did not commit
    assertThat(cf.exists(dbLong(1))).isFalse();
  }

  @Test
  void shouldCommitAndRollbackExplicitTransaction() throws Exception {
    // given
    final var cf = longColumnFamily(Cf.ONE);

    // when
    final var transaction = context.getCurrentTransaction();
    cf.insert(dbLong(1), dbLong(10));
    transaction.rollback();
    context.getCurrentTransaction();
    cf.insert(dbLong(2), dbLong(20));
    context.getCurrentTransaction().commit();

    // then
    assertThat(cf.exists(dbLong(1))).isFalse();
    assertThat(cf.exists(dbLong(2))).isTrue();
  }

  @Test
  void shouldRecoverFromSnapshot() throws Exception {
    // given
    final var cf = longColumnFamily(Cf.ONE);
    cf.insert(dbLong(1), dbLong(10));
    final var snapshotDir = new File(snapshotParent.toFile(), "snapshot");

    // when
    db.createSnapshot(snapshotDir);
    cf.insert(dbLong(2), dbLong(20));

    // then
    try (final var snapshot = this.<Cf>factory().createDb(snapshotDir)) {
      final var restored =
          snapshot.createColumnFamily(Cf.ONE, snapshot.createContext(), new DbLong(), new DbLong());
      assertThat(restored.get(dbLong(1)).getValue()).isEqualTo(10);
      assertThat(restored.exists(dbLong(2))).isFalse();
    }
  }

  enum Scope {
    GLOBAL,
    TENANT
  }

  /** Like the cluster variable scope key: the string is only serialized for the TENANT scope. */
  private static final class ScopedKey implements io.camunda.zeebe.db.DbKey {
    private final DbEnumValue<Scope> scope = new DbEnumValue<>(Scope.class);
    private final DbString scopeKey = new DbString();

    static ScopedKey of(final Scope scope, final String scopeKey) {
      final var key = new ScopedKey();
      key.scope.setValue(scope);
      key.scopeKey.wrapString(scopeKey);
      return key;
    }

    @Override
    public void wrap(final org.agrona.DirectBuffer buffer, final int offset, final int length) {
      scope.wrap(buffer, offset, length);
      if (scope.getValue() == Scope.GLOBAL) {
        scopeKey.wrapString("");
      } else {
        scopeKey.wrap(buffer, offset + scope.getLength(), length - scope.getLength());
      }
    }

    @Override
    public void describe(final io.camunda.zeebe.db.KeyStructure structure) {
      scope.describe(structure);
      structure.omittableString("scopeKey");
    }

    @Override
    public int getLength() {
      return scope.getLength() + (scope.getValue() == Scope.GLOBAL ? 0 : scopeKey.getLength());
    }

    @Override
    public int write(final org.agrona.MutableDirectBuffer buffer, final int offset) {
      int written = scope.write(buffer, offset);
      if (scope.getValue() != Scope.GLOBAL) {
        written += scopeKey.write(buffer, offset + written);
      }
      return written;
    }
  }
}
