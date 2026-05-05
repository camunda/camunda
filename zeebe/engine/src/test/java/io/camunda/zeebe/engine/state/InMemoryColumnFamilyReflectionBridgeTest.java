/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;
import io.camunda.zeebe.db.impl.inmemory.InMemoryZeebeDb;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class InMemoryColumnFamilyReflectionBridgeTest {

  private ZeebeDb<DefaultColumnFamily> zeebeDb;
  private ColumnFamily<DbLong, PersistedBatchOperation> columnFamily;
  private DbLong key;

  @BeforeEach
  void setUp() {
    zeebeDb = new InMemoryZeebeDb<>();
    key = new DbLong();
    columnFamily =
        zeebeDb.createColumnFamily(
            DefaultColumnFamily.DEFAULT,
            zeebeDb.createContext(),
            key,
            new PersistedBatchOperation());
  }

  @AfterEach
  void tearDown() throws Exception {
    zeebeDb.close();
  }

  @Test
  void shouldInsertAndReadValueWhoseMethodSignaturesReferenceUnavailableTypes() {
    // given
    key.wrapLong(1L);
    final var value =
        new PersistedBatchOperation()
            .setKey(1L)
            .setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE);

    // when
    columnFamily.insert(key, value);
    final var stored = columnFamily.get(key, PersistedBatchOperation::new);

    // then
    assertThat(stored).isNotNull();
    assertThat(stored.getKey()).isEqualTo(1L);
    assertThat(stored.getBatchOperationType())
        .isEqualTo(BatchOperationType.CANCEL_PROCESS_INSTANCE);
  }
}
