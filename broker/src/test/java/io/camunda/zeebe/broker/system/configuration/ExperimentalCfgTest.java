/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class ExperimentalCfgTest {

  public final Map<String, String> environment = new HashMap<>();

  @Test
  public void shouldSetRaftRequestTimeoutFromConfig() {
    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("experimental-cfg", environment);
    final var raft = cfg.getExperimental().getRaft();

    // then
    assertThat(raft.getRequestTimeout()).isEqualTo(Duration.ofSeconds(10));
  }

  @Test
  public void shouldSetRaftRequestTimeoutFromEnv() {
    // given
    environment.put("zeebe.broker.experimental.raft.requestTimeout", "15s");

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("experimental-cfg", environment);
    final var raft = cfg.getExperimental().getRaft();

    // then
    assertThat(raft.getRequestTimeout()).isEqualTo(Duration.ofSeconds(15));
  }
}
