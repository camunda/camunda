/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class PhysicalTenantActorPoolCfgTest {

  private static final List<String> TENANTS = List.of("default", "pt1", "pt2", "pt3");

  private final PhysicalTenantActorPoolCfg cfg = new PhysicalTenantActorPoolCfg();

  @Test
  void shouldNotLimitWithoutCpuLimit() {
    // given
    cfg.setCpuHeadroomCores(0.5);

    // when
    final double budget = cfg.cpuBudgetCores("pt1", TENANTS);

    // then
    assertThat(budget).isZero();
  }

  @Test
  void shouldShareCpuLimitWithoutHeadroomByWeight() {
    // given - weights 1, 1, 0.5, 0.5 and an unlisted tenant with the weight 1
    cfg.setCpuLimitCores(3);
    cfg.setCpuHeadroomCores(0.5);
    cfg.setWeights(Map.of("pt1", 1.0, "pt2", 0.5, "pt3", 0.5));

    // when
    final double heavy = cfg.cpuBudgetCores("pt1", TENANTS);
    final double light = cfg.cpuBudgetCores("pt2", TENANTS);
    final double unlisted = cfg.cpuBudgetCores("default", TENANTS);

    // then - 2.5 cores shared by the weights 1, 1, 0.5, 0.5 (sum 3)
    assertThat(heavy).isCloseTo(2.5 / 3, org.assertj.core.data.Offset.offset(1e-9));
    assertThat(light).isCloseTo(2.5 / 6, org.assertj.core.data.Offset.offset(1e-9));
    assertThat(unlisted).isEqualTo(heavy);
  }
}
