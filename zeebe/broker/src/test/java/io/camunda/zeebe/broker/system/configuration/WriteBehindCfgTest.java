/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.writebehind.KeyIndexPolicy;
import io.camunda.zeebe.db.writebehind.WriteBehindConfiguration;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

final class WriteBehindCfgTest {

  @Test
  void shouldBeDisabledWithWriteBehindDefaults() {
    // given
    final var cfg = new WriteBehindCfg();

    // when
    final var config = cfg.createWriteBehindConfiguration();

    // then
    assertThat(cfg.isEnabled()).isFalse();
    final var defaults = WriteBehindConfiguration.defaults();
    assertThat(config.maxPendingBytes()).isEqualTo(defaults.maxPendingBytes());
    assertThat(config.cacheMaxBytes()).isEqualTo(defaults.cacheMaxBytes());
    assertThat(config.flushDelay()).isEqualTo(defaults.flushDelay());
    assertThat(config.flushThresholdBytes()).isEqualTo(defaults.flushThresholdBytes());
    assertThat(config.keyIndexMaxKeys()).isEqualTo(defaults.keyIndexMaxKeys());
    assertThat(config.keyIndexPolicies())
        .hasSize(24)
        .containsEntry((long) ZbColumnFamilies.VARIABLES.getValue(), KeyIndexPolicy.KEEP_HIGHEST)
        .containsEntry(
            (long) ZbColumnFamilies.TIMER_DUE_DATES.getValue(), KeyIndexPolicy.KEEP_LOWEST);
  }

  @Test
  void shouldMapSettingsToWriteBehindConfiguration() {
    // given
    final var cfg = new WriteBehindCfg();
    cfg.setFlushDelay(Duration.ofMillis(5));
    cfg.setFlushThreshold(DataSize.ofMegabytes(4));
    cfg.setMaxPending(DataSize.ofMegabytes(8));
    cfg.setCacheSize(DataSize.ofMegabytes(1));
    cfg.setKeyIndexMaxKeys(42);

    // when
    final var config = cfg.createWriteBehindConfiguration();

    // then
    assertThat(config.flushDelay()).isEqualTo(Duration.ofMillis(5));
    assertThat(config.flushThresholdBytes()).isEqualTo(DataSize.ofMegabytes(4).toBytes());
    assertThat(config.maxPendingBytes()).isEqualTo(DataSize.ofMegabytes(8).toBytes());
    assertThat(config.cacheMaxBytes()).isEqualTo(DataSize.ofMegabytes(1).toBytes());
    assertThat(config.keyIndexMaxKeys()).isEqualTo(42);
  }

  @Test
  void shouldOverrideOneColumnFamilyAndKeepTheOthers() {
    // given
    final var cfg = new WriteBehindCfg();

    // when - names as bound from environment variables or YAML
    cfg.putKeyIndex("variables", KeyIndexPolicy.DISABLE);
    cfg.putKeyIndex("element.instance.key", KeyIndexPolicy.DISABLE);
    cfg.putKeyIndex("message-subscription-by-key", KeyIndexPolicy.KEEP_HIGHEST);

    // then
    assertThat(cfg.getKeyIndex())
        .hasSize(25)
        .containsEntry(ZbColumnFamilies.VARIABLES, KeyIndexPolicy.DISABLE)
        .containsEntry(ZbColumnFamilies.ELEMENT_INSTANCE_KEY, KeyIndexPolicy.DISABLE)
        .containsEntry(ZbColumnFamilies.MESSAGE_SUBSCRIPTION_BY_KEY, KeyIndexPolicy.KEEP_HIGHEST)
        .containsEntry(ZbColumnFamilies.JOBS, KeyIndexPolicy.KEEP_HIGHEST);
  }

  @Test
  void shouldRejectUnknownColumnFamily() {
    // given
    final var cfg = new WriteBehindCfg();

    // when / then
    assertThatThrownBy(() -> cfg.putKeyIndex("NOT_A_CF", KeyIndexPolicy.DISABLE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("'NOT_A_CF'")
        .hasMessageContaining("VARIABLES");
  }
}
