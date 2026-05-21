/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.system.configuration.partitioning.RegionCfg;
import org.junit.Test;

public class RegionTest {
  @Test
  public void shouldMapRegionCorrectly() {
    // given
    final var region = new Region("zone", 16, 3, 1000);

    // when
    final var converted = region.toRegionCfg();

    // then'
    assertThat(converted)
        .returns(region.name(), RegionCfg::name)
        .returns(region.numberOfBrokers(), RegionCfg::numberOfBrokers)
        .returns(region.numberOfReplicas(), RegionCfg::numberOfReplicas)
        .returns(region.priority(), RegionCfg::priority);
  }
}
