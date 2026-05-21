/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import org.junit.jupiter.api.Test;

final class ClusterConfigFactoryTest {

  @Test
  void shouldSetZoneAwareMemberIdAndZoneIdWhenZoneIsConfigured() {
    // given
    final var cfg = new BrokerCfg();
    cfg.getCluster().setNodeId(2);
    cfg.getCluster().setZone("eu-central-1a");
    cfg.getNetwork().getInternalApi().setHost("localhost");
    cfg.getNetwork().getInternalApi().setPort(26502);
    cfg.getNetwork().getInternalApi().setAdvertisedHost("localhost");
    cfg.getNetwork().getInternalApi().setAdvertisedPort(26502);

    // when
    final var clusterConfig = new ClusterConfigFactory().mapConfiguration(cfg);

    // then
    assertThat(clusterConfig.getNodeConfig().getId().id()).isEqualTo("eu-central-1a/2");
    assertThat(clusterConfig.getNodeConfig().getZoneId()).isEqualTo("eu-central-1a");
  }

  @Test
  void shouldSetBareMemberIdWhenZoneIsNull() {
    // given
    final var cfg = new BrokerCfg();
    cfg.getCluster().setNodeId(2);
    cfg.getCluster().setZone(null);
    cfg.getNetwork().getInternalApi().setHost("localhost");
    cfg.getNetwork().getInternalApi().setPort(26502);
    cfg.getNetwork().getInternalApi().setAdvertisedHost("localhost");
    cfg.getNetwork().getInternalApi().setAdvertisedPort(26502);

    // when
    final var clusterConfig = new ClusterConfigFactory().mapConfiguration(cfg);

    // then
    assertThat(clusterConfig.getNodeConfig().getId().id()).isEqualTo("2");
    assertThat(clusterConfig.getNodeConfig().getZoneId()).isNull();
  }
}
