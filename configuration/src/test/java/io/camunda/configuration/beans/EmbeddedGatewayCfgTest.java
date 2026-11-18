/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beans;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import org.junit.jupiter.api.Test;

final class EmbeddedGatewayCfgTest {

  private static final int DEFAULT_GATEWAY_PORT = 26500;

  @Test
  void shouldTakeHostFromBrokerNetwork() {
    // given
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getNetwork().setHost("10.1.1.1");
    final var gatewayCfg = new EmbeddedGatewayCfg();

    // when
    gatewayCfg.init(brokerCfg, "base");

    // then
    assertThat(gatewayCfg.getNetwork().getHost()).isEqualTo("10.1.1.1");
  }

  @Test
  void shouldShiftPortByTenTimesThePortOffset() {
    // given
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getNetwork().setPortOffset(2);
    final var gatewayCfg = new EmbeddedGatewayCfg();

    // when
    gatewayCfg.init(brokerCfg, "base");

    // then
    assertThat(gatewayCfg.getNetwork().getPort()).isEqualTo(DEFAULT_GATEWAY_PORT + 2 * 10);
  }

  @Test
  void shouldNotShiftPortWithoutPortOffset() {
    // given
    final var brokerCfg = new BrokerCfg();
    final var gatewayCfg = new EmbeddedGatewayCfg();

    // when
    gatewayCfg.init(brokerCfg, "base");

    // then
    assertThat(gatewayCfg.getNetwork().getPort()).isEqualTo(DEFAULT_GATEWAY_PORT);
  }
}
