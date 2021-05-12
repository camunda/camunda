/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.Test;

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
  public void shouldUseDefaultMaxOpenFiles() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("empty", environment);
    final var rocksdb = cfg.getExperimental().getRocksdb();

    // then
    assertThat(rocksdb.getMaxOpenFiles()).isEqualTo(RocksDbConfiguration.DEFAULT_MAX_OPEN_FILES);
  }

  @Test
  public void shouldCreateRocksDbConfigurationFromDefault() {
    // given
    final BrokerCfg cfg = TestConfigReader.readConfig("empty", environment);
    final var rocksdb = cfg.getExperimental().getRocksdb();
    final Properties columnFamilyOptions = cfg.getData().getRocksdb().getColumnFamilyOptions();

    // when
    final var rocksDbConfiguration = rocksdb.createRocksDbConfiguration(columnFamilyOptions);

    // then
    assertThat(rocksDbConfiguration.getColumnFamilyOptions()).isEmpty();
    assertThat(rocksDbConfiguration.isStatisticsEnabled()).isFalse();
    assertThat(rocksDbConfiguration.getMaxOpenFiles())
        .isEqualTo(RocksDbConfiguration.DEFAULT_MAX_OPEN_FILES);
    assertThat(rocksDbConfiguration.getIoRateBytesPerSecond()).isZero();
    assertThat(rocksDbConfiguration.isWalDisabled()).isFalse();
  }

  @Test
  public void shouldCreateRocksDbConfigurationFromConfig() {
    // given
    final BrokerCfg cfg = TestConfigReader.readConfig("rocksdb-cfg", environment);
    final var rocksdb = cfg.getExperimental().getRocksdb();
    final Properties columnFamilyOptions = cfg.getData().getRocksdb().getColumnFamilyOptions();

    // when
    final var rocksDbConfiguration = rocksdb.createRocksDbConfiguration(columnFamilyOptions);

    // then
    assertThat(rocksDbConfiguration.getColumnFamilyOptions())
        .containsEntry("compaction_pri", "kOldestSmallestSeqFirst")
        .containsEntry("write_buffer_size", "67108864");
    assertThat(rocksDbConfiguration.isStatisticsEnabled()).isTrue();
    assertThat(rocksDbConfiguration.getMaxOpenFiles()).isEqualTo(3);
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
  public void shouldSetMaxOpenFilesViaConfig() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("rocksdb-cfg", environment);
    final var rocksdb = cfg.getExperimental().getRocksdb();

    // then
    assertThat(rocksdb.getMaxOpenFiles()).isEqualTo(3);
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
