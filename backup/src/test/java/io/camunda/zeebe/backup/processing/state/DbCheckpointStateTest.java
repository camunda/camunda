/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.processing.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DbCheckpointStateTest {

  @TempDir Path database;
  private DbCheckpointState state;
  private ZeebeDb zeebedb;

  @AfterEach
  void closeDb() throws Exception {
    zeebedb.close();
  }

  @BeforeEach
  void before() {
    zeebedb =
        new ZeebeRocksDbFactory<>(
                new RocksDbConfiguration(), new ConsistencyChecksSettings(true, true))
            .createDb(database.toFile());
    state = new DbCheckpointState(zeebedb, zeebedb.createContext());
  }

  @Test
  void shouldInitializeToZero() {
    // given

    // when-then
    assertThat(state.getCheckpointId()).isZero();
    assertThat(state.getCheckpointPosition()).isZero();
  }

  @Test
  void shouldSetAndGetCheckpointIdAndPosition() {
    // when
    state.setCheckpointInfo(5L, 10L);

    // then
    assertThat(state.getCheckpointId()).isEqualTo(5L);
    assertThat(state.getCheckpointPosition()).isEqualTo(10L);
  }

  @Test
  void shouldOverwriteCheckpointIdAndPosition() {
    // given
    state.setCheckpointInfo(5L, 10L);

    // when
    state.setCheckpointInfo(15L, 20L);

    // then
    assertThat(state.getCheckpointId()).isEqualTo(15L);
    assertThat(state.getCheckpointPosition()).isEqualTo(20L);
  }
}
