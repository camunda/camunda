/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import static io.camunda.zeebe.broker.system.syspartition.RegionTopologyTestSupport.threeByThreeByOne;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.broker.system.configuration.partitioning.ZoneAwareCfg;
import io.camunda.zeebe.broker.system.configuration.partitioning.ZoneCfg;
import io.camunda.zeebe.broker.system.configuration.partitioning.ZoneRole;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SystemPartitionCfgTest {

  @Test
  void shouldAcceptControlZoneWithoutReplicas() {
    // given
    final var brokerCfg = threeByThreeByOne(5);

    // when - then
    assertThatCode(() -> brokerCfg.getCluster().init(brokerCfg, "")).doesNotThrowAnyException();
  }

  @Test
  void shouldRejectControlZoneWithReplicas() {
    // given
    final var brokerCfg = threeByThreeByOne(5);
    brokerCfg
        .getExperimental()
        .getPartitioning()
        .setZoneAware(
            new ZoneAwareCfg(
                List.of(
                    new ZoneCfg("region-a", 3, 2, 1000),
                    new ZoneCfg("region-b", 3, 2, 500),
                    new ZoneCfg("control", 1, 1, 1, ZoneRole.CONTROL))));

    // when - then
    assertThatThrownBy(() -> brokerCfg.getCluster().init(brokerCfg, ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Control zone 'control'");
  }

  @Test
  void shouldRejectOnlyControlZones() {
    // given
    final var brokerCfg = threeByThreeByOne(1);
    brokerCfg.getCluster().setZone("control");
    brokerCfg
        .getExperimental()
        .getPartitioning()
        .setZoneAware(new ZoneAwareCfg(List.of(new ZoneCfg("control", 1, 0, 1, ZoneRole.CONTROL))));

    // when - then
    assertThatThrownBy(() -> brokerCfg.getCluster().init(brokerCfg, ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one data zone");
  }

  @Test
  void shouldRejectReplicationFactorAboveBrokerCount() {
    // given
    final var brokerCfg = threeByThreeByOne(8);

    // when - then
    assertThatThrownBy(() -> brokerCfg.getCluster().init(brokerCfg, ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("[1, 7]");
  }

  @Test
  void shouldRejectReplicationFactorBelowOne() {
    // given
    final var brokerCfg = threeByThreeByOne(0);

    // when - then
    assertThatThrownBy(() -> brokerCfg.getCluster().init(brokerCfg, ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("replication factor");
  }
}
