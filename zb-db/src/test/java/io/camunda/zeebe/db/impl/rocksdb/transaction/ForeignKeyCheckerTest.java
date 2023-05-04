/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

final class ForeignKeyCheckerTest {
  //
  //  @Test
  //  void shouldFailOnMissingForeignKey() throws Exception {
  //    // given
  //    final var db = mock(ZeebeTransactionDb.class);
  //    final var tx = mock(ZeebeTransaction.class);
  //    final var check = new ForeignKeyChecker(db, context, new ConsistencyChecksSettings(true,
  // true));
  //    final var key = new DbLong();
  //    key.wrapLong(1);
  //
  //    // when
  //    when(tx.get(anyLong(), anyLong(), any(), anyInt())).thenReturn(null);
  //
  //    // then
  //    assertThatThrownBy(
  //            () ->
  //                check.assertExists(
  //                    tx, new DbForeignKey<>(key, TestColumnFamilies.TEST_COLUMN_FAMILY)))
  //        .isInstanceOf(ZeebeDbInconsistentException.class);
  //  }
  //
  //  @Test
  //  void shouldSucceedOnExistingForeignKey() throws Exception {
  //    // given
  //    final var db = mock(ZeebeTransactionDb.class);
  //    final var tx = mock(ZeebeTransaction.class);
  //    final var check = new ForeignKeyChecker(db, context, new ConsistencyChecksSettings(true,
  // true));
  //    final var key = new DbLong();
  //    key.wrapLong(1);
  //
  //    // when -- tx says every key exists
  //    when(tx.get(anyLong(), anyLong(), any(), anyInt())).thenReturn(new byte[] {});
  //
  //    // then -- check doesn't trow
  //    check.assertExists(tx, new DbForeignKey<>(key, TestColumnFamilies.TEST_COLUMN_FAMILY));
  //  }
  //
  //  @Test
  //  void shouldRespectSkipCondition() throws Exception {
  //    // given
  //    final var db = mock(ZeebeTransactionDb.class);
  //    final var tx = mock(ZeebeTransaction.class);
  //    final var check = new ForeignKeyChecker(db, context, new ConsistencyChecksSettings(true,
  // true));
  //    final var key = new DbLong();
  //
  //    // when -- tx says no key exists
  //    when(tx.get(anyLong(), anyLong(), any(), anyInt())).thenReturn(null);
  //
  //    // then
  //    assertDoesNotThrow(
  //        () ->
  //            check.assertExists(
  //                tx,
  //                new DbForeignKey<>(
  //                    key, TestColumnFamilies.TEST_COLUMN_FAMILY, MatchType.Full, (k) -> true)));
  //    assertThatThrownBy(
  //            () ->
  //                check.assertExists(
  //                    tx,
  //                    new DbForeignKey<>(
  //                        key, TestColumnFamilies.TEST_COLUMN_FAMILY, MatchType.Full, (k) ->
  // false)))
  //        .isInstanceOf(ZeebeDbInconsistentException.class)
  //        .hasMessageContaining("Foreign key");
  //  }
  //
  //  @Test
  //  void shouldCheckIfKeyIsNotSkipped() throws Exception {
  //    // given
  //    final var db = mock(ZeebeTransactionDb.class);
  //    final var tx = mock(ZeebeTransaction.class);
  //    final var check = new ForeignKeyChecker(db, context, new ConsistencyChecksSettings(true,
  // true));
  //    final var key = new DbLong();
  //
  //    // when -- tx says no key exists
  //    when(tx.get(anyLong(), anyLong(), any(), anyInt())).thenReturn(null);
  //
  //    // then
  //    assertThatThrownBy(
  //            () -> {
  //              key.wrapLong(5);
  //              check.assertExists(
  //                  tx,
  //                  new DbForeignKey<>(
  //                      key,
  //                      TestColumnFamilies.TEST_COLUMN_FAMILY,
  //                      MatchType.Full,
  //                      (k) -> k.getValue() == -1));
  //            })
  //        .isInstanceOf(ZeebeDbInconsistentException.class)
  //        .hasMessageContaining("Foreign key");
  //  }
  //
  //  @Test
  //  void shouldSucceedOnRealColumnFamily(@TempDir final File tempDir) throws Exception {
  //    // given
  //    final var db =
  // DefaultZeebeDbFactory.<TestColumnFamilies>getDefaultFactory().createDb(tempDir);
  //    final var txContext = db.createContext();
  //
  //    final var cf1 =
  //        db.createColumnFamily(
  //            TestColumnFamilies.TEST_COLUMN_FAMILY, txContext, new DbLong(), DbNil.INSTANCE);
  //
  //    final var check =
  //        new ForeignKeyChecker(
  //            (ZeebeTransactionDb<?>) db, context, new ConsistencyChecksSettings(true, true));
  //
  //    // when -- key 1 exists in first column family
  //    final var cf1Key = new DbLong();
  //    cf1Key.wrapLong(1);
  //    cf1.insert(cf1Key, DbNil.INSTANCE);
  //
  //    // then -- referring to key 1 does not throw
  //    assertDoesNotThrow(
  //        () ->
  //            check.assertExists(
  //                (ZeebeTransaction) txContext.getCurrentTransaction(),
  //                new DbForeignKey<>(cf1Key, TestColumnFamilies.TEST_COLUMN_FAMILY)));
  //
  //    db.close();
  //  }
  //
  //  @Test
  //  void shouldFindByPrefix(@TempDir final File tempDir) throws Exception {
  //    // given
  //    final var db =
  // DefaultZeebeDbFactory.<TestColumnFamilies>getDefaultFactory().createDb(tempDir);
  //    final var txContext = db.createContext();
  //
  //    final var cf1Key = new DbCompositeKey<>(new DbLong(), new DbString());
  //    cf1Key.first().wrapLong(1);
  //    cf1Key.second().wrapString("suffix");
  //    final var cf1 =
  //        db.createColumnFamily(
  //            TestColumnFamilies.TEST_COLUMN_FAMILY, txContext, cf1Key, DbNil.INSTANCE);
  //
  //    final var check =
  //        new ForeignKeyChecker(
  //            (ZeebeTransactionDb<?>) db, context, new ConsistencyChecksSettings(true, true));
  //
  //    // when -- key 1 exists in first column family
  //    cf1.insert(cf1Key, DbNil.INSTANCE);
  //
  //    // then -- referring to key 1 by prefix does not throw
  //    final var fk =
  //        new DbForeignKey<>(
  //            new DbLong(), TestColumnFamilies.TEST_COLUMN_FAMILY, MatchType.Prefix, (any) ->
  // false);
  //    fk.inner().wrapLong(cf1Key.first().getValue());
  //    assertDoesNotThrow(
  //        () -> check.assertExists((ZeebeTransaction) txContext.getCurrentTransaction(), fk));
  //
  //    db.close();
  //  }
  //
  //  @Test
  //  void shouldThrowWhenPrefixIsNotFound(@TempDir final File tempDir) throws Exception {
  //    // given
  //    final var db =
  // DefaultZeebeDbFactory.<TestColumnFamilies>getDefaultFactory().createDb(tempDir);
  //    final var txContext = db.createContext();
  //
  //    final var cf1Key = new DbCompositeKey<>(new DbLong(), new DbString());
  //    cf1Key.first().wrapLong(1);
  //    cf1Key.second().wrapString("suffix");
  //    final var cf1 =
  //        db.createColumnFamily(
  //            TestColumnFamilies.TEST_COLUMN_FAMILY, txContext, cf1Key, DbNil.INSTANCE);
  //
  //    final var check =
  //        new ForeignKeyChecker(
  //            (ZeebeTransactionDb<?>) db, context, new ConsistencyChecksSettings(true, true));
  //
  //    // when -- key 1 exists in first column family
  //    cf1.insert(cf1Key, DbNil.INSTANCE);
  //
  //    // then -- referring to a non-existing key prefix fails
  //    final var fk =
  //        new DbForeignKey<>(
  //            new DbLong(), TestColumnFamilies.TEST_COLUMN_FAMILY, MatchType.Prefix, (any) ->
  // false);
  //    fk.inner().wrapLong(cf1Key.first().getValue() + 1);
  //    assertThatThrownBy(
  //            () -> check.assertExists((ZeebeTransaction) txContext.getCurrentTransaction(), fk))
  //        .isInstanceOf(ZeebeDbInconsistentException.class)
  //        .hasMessageContaining("Foreign key");
  //
  //    db.close();
  //  }
  //
  //  private enum TestColumnFamilies {
  //    TEST_COLUMN_FAMILY
  //  }
}
