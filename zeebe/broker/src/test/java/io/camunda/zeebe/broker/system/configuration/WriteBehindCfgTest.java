/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.writebehind.WriteBehindConfiguration;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class WriteBehindCfgTest {

  @Test
  void shouldUseDefaultKeyIndexSettingsOfTheStore() {
    // given
    final var cfg = new WriteBehindCfg();

    // when
    final var configuration = cfg.createConfiguration();

    // then
    final var defaults = WriteBehindConfiguration.defaults();
    assertThat(configuration.keyIndexColumnFamilies()).isEqualTo(defaults.keyIndexColumnFamilies());
    assertThat(configuration.keyIndexMaxKeys()).isEqualTo(defaults.keyIndexMaxKeys());
  }

  @Test
  void shouldMapKeyIndexColumnFamiliesToTheirIds() {
    // given
    final var cfg = new WriteBehindCfg();
    cfg.setKeyIndexColumnFamilies(
        Set.of(ZbColumnFamilies.TIMER_DUE_DATES, ZbColumnFamilies.JOB_DEADLINES));
    cfg.setKeyIndexMaxKeys(42);

    // when
    final var configuration = cfg.createConfiguration();

    // then
    assertThat(configuration.keyIndexColumnFamilies())
        .containsExactlyInAnyOrder(
            (long) ZbColumnFamilies.TIMER_DUE_DATES.getValue(),
            (long) ZbColumnFamilies.JOB_DEADLINES.getValue());
    assertThat(configuration.keyIndexMaxKeys()).isEqualTo(42);
  }

  @Test
  void shouldDisableKeyIndexWithoutColumnFamilies() {
    // given
    final var cfg = new WriteBehindCfg();
    cfg.setKeyIndexColumnFamilies(Set.of());

    // then
    assertThat(cfg.createConfiguration().keyIndexColumnFamilies()).isEmpty();
  }
}
