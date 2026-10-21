/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

final class LogArchiveCfgTest {

  @Test
  void shouldDefaultToClusterCompactionMode() {
    // given
    final var cfg = new LogArchiveCfg();

    // then
    assertThat(cfg.getCompactionMode()).isEqualTo("CLUSTER");
  }

  @Test
  void shouldAcceptAuthorityCompactionMode() {
    // given
    final var cfg = new LogArchiveCfg();

    // when
    cfg.setCompactionMode("AUTHORITY");

    // then
    assertThat(cfg.getCompactionMode()).isEqualTo("AUTHORITY");
  }

  @Test
  void shouldRejectUnknownCompactionMode() {
    // given
    final var cfg = new LogArchiveCfg();

    // when / then
    assertThatThrownBy(() -> cfg.setCompactionMode("authority"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CLUSTER or AUTHORITY");
  }
}
