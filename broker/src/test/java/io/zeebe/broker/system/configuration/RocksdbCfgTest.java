/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public final class RocksdbCfgTest {

  public final Map<String, String> environment = new HashMap<>();

  @Test
  public void shouldSetColumnFamilyOptionsConfig() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("rocksdb-cfg", environment);
    final var rocksdb = cfg.getData().getRocksdb();

    // then
    final var columnFamilyOptions = rocksdb.getColumnFamilyOptions();
    assertThat(columnFamilyOptions).containsEntry("compaction_pri", "kOldestSmallestSeqFirst");
    assertThat(columnFamilyOptions).containsEntry("write_buffer_size", "67108864");
  }

  @Test
  public void shouldSetColumnFamilyOptionsConfigFromEnvironmentVariables() {
    // given
    environment.put("zeebe.broker.data.rocksdb.columnFamilyOptions.arena.block.size", "16777216");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("rocksdb-cfg", environment);
    final var rocksdb = cfg.getData().getRocksdb();

    // then keys should contain underscores
    final var columnFamilyOptions = rocksdb.getColumnFamilyOptions();
    assertThat(columnFamilyOptions).containsEntry("arena_block_size", "16777216");
  }
}
