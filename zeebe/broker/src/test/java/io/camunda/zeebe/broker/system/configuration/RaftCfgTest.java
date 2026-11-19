/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.system.configuration.RaftCfg.FlushConfig;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class RaftCfgTest {

  @Test
  void shouldEnableFlushWithoutDelayByDefault() {
    // when
    final var flush =
        TestConfigReader.readConfig("empty", Map.of()).getCluster().getRaft().getFlush();

    // then
    assertThat(flush).isEqualTo(new FlushConfig(true, Duration.ZERO));
  }

  @Test
  void shouldKeepFlushEnabledWhenOnlyDelayTimeIsSet() {
    // given
    final var environment = Map.of("zeebe.broker.cluster.raft.flush.delayTime", "15s");

    // when
    final var flush =
        TestConfigReader.readConfig("empty", environment).getCluster().getRaft().getFlush();

    // then
    assertThat(flush).isEqualTo(new FlushConfig(true, Duration.ofSeconds(15)));
  }

  @Test
  void shouldNotDelayFlushWhenOnlyEnabledIsSet() {
    // given
    final var environment = Map.of("zeebe.broker.cluster.raft.flush.enabled", "true");

    // when
    final var flush =
        TestConfigReader.readConfig("empty", environment).getCluster().getRaft().getFlush();

    // then
    assertThat(flush).isEqualTo(new FlushConfig(true, Duration.ZERO));
  }

  @Test
  void shouldDisableFlushWhenExplicitlyDisabled() {
    // given
    final var environment = Map.of("zeebe.broker.cluster.raft.flush.enabled", "false");

    // when
    final var flush =
        TestConfigReader.readConfig("empty", environment).getCluster().getRaft().getFlush();

    // then
    assertThat(flush.enabled()).isFalse();
  }
}
