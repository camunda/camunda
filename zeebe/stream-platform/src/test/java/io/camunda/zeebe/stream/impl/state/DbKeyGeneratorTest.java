/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.stream.util.DefaultZeebeDbFactory;
import java.io.File;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DbKeyGeneratorTest {
  private static final int PARTITION_ID = 1;
  @TempDir private File tempFolder;
  private ZeebeDb<ZbColumnFamilies> db;
  private DbKeyGenerator dbKeyGenerator;

  @BeforeEach
  void createDb() {
    db = DefaultZeebeDbFactory.defaultFactory().createDb(tempFolder);
    dbKeyGenerator = new DbKeyGenerator(PARTITION_ID, db, db.createContext());
  }

  @AfterEach
  void closeDb() throws Exception {
    db.close();
  }

  @Test
  void shouldOverwriteKey() {
    // when
    dbKeyGenerator.overwriteKey(Protocol.encodePartitionId(PARTITION_ID, 1000L));
    final long nextKey = dbKeyGenerator.nextKey();

    // then
    assertThat(nextKey).isEqualTo(Protocol.encodePartitionId(PARTITION_ID, 1001L));
  }

  @Test
  void shouldSetMaxKey() {
    // when
    dbKeyGenerator.setMaxKeyValue(Protocol.encodePartitionId(PARTITION_ID, 1000L));

    // then
    assertThat(dbKeyGenerator.getMaxKeyValue())
        .isEqualTo(Protocol.encodePartitionId(PARTITION_ID, 1000L));
  }

  @Test
  void shouldFailIfKeyOfAnotherPartitionIsSet() {
    // given
    final long keyOfAnotherPartition = Protocol.encodePartitionId(PARTITION_ID + 1, 100);

    // when - then
    assertThatException()
        .isThrownBy(() -> dbKeyGenerator.setKeyIfHigher(keyOfAnotherPartition))
        .havingCause()
        .withMessageContaining(
            String.format(
                "Provided key %d does not belong to partition %d",
                keyOfAnotherPartition, Protocol.DEPLOYMENT_PARTITION));
  }

  @Test
  void shouldFailNextKeyGenerationWhenMaxKeyReached() {
    // given
    dbKeyGenerator.setMaxKeyValue(Protocol.encodePartitionId(PARTITION_ID, 1000L));
    dbKeyGenerator.overwriteKey(Protocol.encodePartitionId(PARTITION_ID, 1000L));

    // when
    assertThatException().isThrownBy(() -> dbKeyGenerator.nextKey());
  }

  @Test
  void shouldNotSetKeyIfGreaterThanMaxKey() {
    // given
    dbKeyGenerator.setMaxKeyValue(Protocol.encodePartitionId(PARTITION_ID, 1000L));
    final long currentKey = Protocol.encodePartitionId(PARTITION_ID, 50L);
    dbKeyGenerator.overwriteKey(currentKey);

    // when
    dbKeyGenerator.setKeyIfHigher(Protocol.encodePartitionId(PARTITION_ID, 2000L));

    // then
    assertThat(dbKeyGenerator.getCurrentKey()).isEqualTo(currentKey);
  }
}
