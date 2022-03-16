/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import org.junit.jupiter.api.Test;

final class ForeignKeyCheckerTest {

  @Test
  void shouldFailOnMissingForeignKey() throws Exception {
    // given
    final var db = mock(ZeebeTransactionDb.class);
    final var tx = mock(ZeebeTransaction.class);
    final var check = new ForeignKeyChecker(db, new ConsistencyChecksSettings(true, true));
    final var key = new DbLong();
    key.wrapLong(1);

    // when
    when(tx.get(anyLong(), anyLong(), any(), anyInt())).thenReturn(null);

    // then
    assertThatThrownBy(
            () ->
                check.assertExists(
                    tx, new DbForeignKey<>(key, TestColumnFamilies.TEST_COLUMN_FAMILY)))
        .isInstanceOf(ZeebeDbInconsistentException.class);
  }

  @Test
  void shouldSucceedOnExistingForeignKey() throws Exception {
    // given
    final var db = mock(ZeebeTransactionDb.class);
    final var tx = mock(ZeebeTransaction.class);
    final var check = new ForeignKeyChecker(db, new ConsistencyChecksSettings(true, true));
    final var key = new DbLong();
    key.wrapLong(1);

    // when -- tx says every key exists
    when(tx.get(anyLong(), anyLong(), any(), anyInt())).thenReturn(new byte[] {});

    // then -- check doesn't trow
    check.assertExists(tx, new DbForeignKey<>(key, TestColumnFamilies.TEST_COLUMN_FAMILY));
  }

  private enum TestColumnFamilies {
    TEST_COLUMN_FAMILY
  }
}
