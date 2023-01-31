/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.springframework.util.unit.DataSize;

public final class RocksdbCfgTest {

  public final Map<String, String> environment = new HashMap<>();

  @Test
  public void shouldDisableStatisticsPerDefault() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("empty", environment);
    final var rocksdb = cfg.getExperimental().getRocksdb();

    // then
    assertThat(rocksdb.isEnableStatistics()).isFalse();
  }

  @Test
  public void shouldHaveEmptyPropertiesPerDefault() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("empty", environment);
    final var rocksdb = cfg.getExperimental().getRocksdb();

    // then
    assertThat(rocksdb.getColumnFamilyOptions()).isEmpty();
  }

  @Test
  public void shouldUseDefaultMemoryLimit() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("empty", environment);
    final var rocksdb = cfg.getExperimental().getRocksdb();

    // then
    assertThat(rocksdb.getMemoryLimit()).isEqualTo(DataSize.ofMegabytes(512));
  }

  @Test
  public void shouldUseDefaultMaxOpenFiles() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("empty", environment);
    final var rocksdb = cfg.getExperimental().getRocksdb();

    // then
    assertThat(rocksdb.getMaxOpenFiles()).isEqualTo(-1);
  }

  @Test
  public void shouldCreateRocksDbConfigurationFromDefault() {
    // given
    final BrokerCfg cfg = TestConfigReader.readConfig("empty", environment);
    final var rocksdb = cfg.getExperimental().getRocksdb();

    // when
    final var rocksDbConfiguration = rocksdb.createRocksDbConfiguration();

    // then
    assertThat(rocksDbConfiguration.getColumnFamilyOptions()).isEmpty();
    assertThat(rocksDbConfiguration.isStatisticsEnabled()).isFalse();
    assertThat(rocksDbConfiguration.getMemoryLimit())
        .isEqualTo(DataSize.ofMegabytes(512).toBytes());
    assertThat(rocksDbConfiguration.getMaxOpenFiles()).isEqualTo(-1);
    assertThat(rocksDbConfiguration.getMaxWriteBufferNumber()).isEqualTo(6);
    assertThat(rocksDbConfiguration.getMinWriteBufferNumberToMerge()).isEqualTo(3);
    assertThat(rocksDbConfiguration.getIoRateBytesPerSecond()).isZero();
    assertThat(rocksDbConfiguration.isWalDisabled()).isTrue();
  }

  @Test
  public void shouldCreateRocksDbConfigurationFromConfig() {
    // given
    final BrokerCfg cfg = TestConfigReader.readConfig("rocksdb-cfg", environment);
    final var rocksdb = cfg.getExperimental().getRocksdb();

    // when
    final var rocksDbConfiguration = rocksdb.createRocksDbConfiguration();

    // then
    assertThat(rocksDbConfiguration.getColumnFamilyOptions())
        .containsEntry("compaction_pri", "kOldestSmallestSeqFirst")
        .containsEntry("write_buffer_size", "67108864");
    assertThat(rocksDbConfiguration.isStatisticsEnabled()).isTrue();
    assertThat(rocksDbConfiguration.getMemoryLimit()).isEqualTo(DataSize.ofMegabytes(32).toBytes());
    assertThat(rocksDbConfiguration.getMaxOpenFiles()).isEqualTo(3);
  }

  @Test
  public void shouldSetColumnFamilyOptionsConfig() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("rocksdb-cfg", environment);
    final var rocksdb = cfg.getExperimental().getRocksdb();

    // then
    final var columnFamilyOptions = rocksdb.getColumnFamilyOptions();
    assertThat(columnFamilyOptions)
        .containsEntry("compaction_pri", "kOldestSmallestSeqFirst")
        .containsEntry("write_buffer_size", "67108864");
  }

  @Test
  public void shouldEnableStatisticsViaConfig() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("rocksdb-cfg", environment);
    final var rocksdb = cfg.getExperimental().getRocksdb();

    // then
    assertThat(rocksdb.isEnableStatistics()).isTrue();
  }

  @Test
  public void shouldSetMemoryLimitViaConfig() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("rocksdb-cfg", environment);
    final var rocksdb = cfg.getExperimental().getRocksdb();

    // then
    assertThat(rocksdb.getMemoryLimit()).isEqualTo(DataSize.ofMegabytes(32));
  }

  @Test
  public void shouldSetMaxOpenFilesViaConfig() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("rocksdb-cfg", environment);
    final var rocksdb = cfg.getExperimental().getRocksdb();

    // then
    assertThat(rocksdb.getMaxOpenFiles()).isEqualTo(3);
  }

  @Test
  public void shouldSetColumnFamilyOptionsConfigFromEnvironmentVariables() {
    // given
    environment.put(
        "zeebe.broker.experimental.rocksdb.columnFamilyOptions.arena.block.size", "16777216");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("rocksdb-cfg", environment);
    final var rocksdb = cfg.getExperimental().getRocksdb();

    // then keys should contain underscores
    final var columnFamilyOptions = rocksdb.getColumnFamilyOptions();
    assertThat(columnFamilyOptions).containsEntry("arena_block_size", "16777216");
  }

  @Test
  public void shouldEnableStatisticsViaEnvironmentVariables() {
    // given
    environment.put("zeebe.broker.experimental.rocksdb.enableStatistics", "true");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("rocksdb-cfg", environment);
    final var rocksdb = cfg.getExperimental().getRocksdb();

    // then
    assertThat(rocksdb.isEnableStatistics()).isTrue();
  }

  @Test
  public void shouldSetMemoryLimitViaEnvironmentVariables() {
    // given
    environment.put("zeebe.broker.experimental.rocksdb.memoryLimit", "16KB");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("rocksdb-cfg", environment);
    final var rocksdb = cfg.getExperimental().getRocksdb();

    // then
    assertThat(rocksdb.getMemoryLimit()).isEqualTo(DataSize.ofKilobytes(16));
  }

  @Test
  public void shouldSetMaxOpenFilesViaEnvironmentVariables() {
    // given
    environment.put("zeebe.broker.experimental.rocksdb.maxOpenFiles", "5");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("rocksdb-cfg", environment);
    final var rocksdb = cfg.getExperimental().getRocksdb();

    // then
    assertThat(rocksdb.getMaxOpenFiles()).isEqualTo(5);
  }

  @Test
  public void shouldSetMaxWriteBufferNumberViaConfig() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("rocksdb-cfg", environment);
    final var rocksdb = cfg.getExperimental().getRocksdb();

    // then
    assertThat(rocksdb.getMaxWriteBufferNumber()).isEqualTo(3);
  }

  @Test
  public void shouldSetMaxWriteBufferNumberViaEnvironmentVariables() {
    // given
    environment.put("zeebe.broker.experimental.rocksdb.maxWriteBufferNumber", "5");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("rocksdb-cfg", environment);
    final var rocksdb = cfg.getExperimental().getRocksdb();

    // then
    assertThat(rocksdb.getMaxWriteBufferNumber()).isEqualTo(5);
  }

  @Test
  public void shouldSetMinWriteBufferNumberToMergeViaConfig() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("rocksdb-cfg", environment);
    final var rocksdb = cfg.getExperimental().getRocksdb();

    // then
    assertThat(rocksdb.getMinWriteBufferNumberToMerge()).isEqualTo(3);
  }

  @Test
  public void shouldSetMinWriteBufferNumberToMergeViaEnvironmentVariables() {
    // given
    environment.put("zeebe.broker.experimental.rocksdb.minWriteBufferNumberToMerge", "5");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("rocksdb-cfg", environment);
    final var rocksdb = cfg.getExperimental().getRocksdb();

    // then
    assertThat(rocksdb.getMinWriteBufferNumberToMerge()).isEqualTo(5);
  }

  @Test
  public void shouldSetIoRateBytesPerSecondViaConfig() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("rocksdb-cfg", environment);
    final var rocksdb = cfg.getExperimental().getRocksdb();

    // then
    assertThat(rocksdb.getIoRateBytesPerSecond()).isEqualTo(4096);
  }

  @Test
  public void shouldSetIoRateBytesPerSecondViaEnvironmentVariables() {
    // given
    environment.put("zeebe.broker.experimental.rocksdb.ioRateBytesPerSecond", "4096");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("rocksdb-cfg", environment);
    final var rocksdb = cfg.getExperimental().getRocksdb();

    // then
    assertThat(rocksdb.getIoRateBytesPerSecond()).isEqualTo(4096);
  }

  @Test
  public void shouldSetIsDisableWalPerSecondViaConfig() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("rocksdb-cfg", environment);
    final var rocksdb = cfg.getExperimental().getRocksdb();

    // then
    assertThat(rocksdb.isDisableWal()).isTrue();
  }

  @Test
  public void shouldSetIsDisableWalViaEnvironmentVariables() {
    // given
    environment.put("zeebe.broker.experimental.rocksdb.disableWal", "true");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("rocksdb-cfg", environment);
    final var rocksdb = cfg.getExperimental().getRocksdb();

    // then
    assertThat(rocksdb.isDisableWal()).isTrue();
  }
}
