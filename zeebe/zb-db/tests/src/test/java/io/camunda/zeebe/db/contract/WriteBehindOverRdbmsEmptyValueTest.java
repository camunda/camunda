/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.contract;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.cache.WriteBehindConfiguration;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;
import org.junit.jupiter.api.Test;

final class WriteBehindOverRdbmsEmptyValueTest extends EmptyValueContractTest {

  private static final WriteBehindConfiguration NO_READ_CACHE =
      new WriteBehindConfiguration(
          1,
          WriteBehindConfiguration.DEFAULT.maxPendingBytes(),
          WriteBehindConfiguration.DEFAULT.batchSize(),
          WriteBehindConfiguration.DEFAULT.maxWriteDelay(),
          WriteBehindConfiguration.DEFAULT.snapshotCatchUpTimeout());

  @Override
  protected ZeebeDbFactory<DefaultColumnFamily> createFactory() {
    return WriteBehindOverRdbmsTestFactory.create(NO_READ_CACHE);
  }

  @Test
  void shouldReadAnEmptyValueBackFromTheStorage() {
    // given - the snapshot's catch-up writes the value to the database
    columnFamily().upsert(dbLong(1), emptyValue());
    db().createSnapshot(directory().resolve("snapshot").toFile());

    // when - then
    assertThat(columnFamily().get(dbLong(1), DbBytes::new))
        .isNotNull()
        .extracting(DbBytes::getLength)
        .isEqualTo(0);
    assertThat(columnFamily().get(dbLong(1))).isNull();
    assertThat(columnFamily().exists(dbLong(1))).isFalse();
  }
}
