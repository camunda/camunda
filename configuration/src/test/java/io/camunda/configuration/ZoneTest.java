/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.broker.system.configuration.partitioning.ZoneCfg;
import org.junit.jupiter.api.Test;

public class ZoneTest {
  @Test
  void shouldRejectInvalidZoneNameAtConfigTime() {
    // when / then
    assertThatThrownBy(() -> new Zone("zone_with_underscores", 1, 1, 100))
        .isInstanceOf(UnifiedConfigurationException.class);
  }

  @Test
  public void shouldMapRegionCorrectly() {
    // given
    final var region = new Zone("zone", 16, 3, 1000);

    // when
    final var converted = region.toZoneCfg();

    // then'
    assertThat(converted)
        .returns(region.name(), ZoneCfg::name)
        .returns(region.numberOfBrokers(), ZoneCfg::numberOfBrokers)
        .returns(region.numberOfReplicas(), ZoneCfg::numberOfReplicas)
        .returns(region.priority(), ZoneCfg::priority);
  }
}
