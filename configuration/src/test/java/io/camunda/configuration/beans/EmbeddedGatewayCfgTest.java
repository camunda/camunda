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
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

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

  @Test
  void shouldBindLegacyZeebeBrokerGatewayKeys() {
    // given
    final var source =
        new MapConfigurationPropertySource(
            Map.of(
                "zeebe.broker.gateway.enable", "false",
                "zeebe.broker.gateway.network.port", "1234",
                "zeebe.broker.gateway.cluster.request-timeout", "5s",
                "zeebe.broker.gateway.long-polling.enabled", "false",
                "zeebe.broker.gateway.security.enabled", "true"));

    // when
    final var properties =
        new Binder(source).bind("zeebe.broker", LegacyBrokerBasedProperties.class).get();

    // then
    final var gateway = properties.getGateway();
    assertThat(gateway.isEnable()).isFalse();
    assertThat(gateway.getNetwork().getPort()).isEqualTo(1234);
    assertThat(gateway.getCluster().getRequestTimeout()).hasSeconds(5);
    assertThat(gateway.getLongPolling().isEnabled()).isFalse();
    assertThat(gateway.getSecurity().isEnabled()).isTrue();
  }
}
