/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.Test;
import org.springframework.util.unit.DataSize;

public class DataCfgTest {

  @Test
  public void shouldUseDefaultFreeSpaceConfig() {
    // when
    final DataCfg dataCfg = new DataCfg();
    dataCfg.init(new BrokerCfg(), "/base");

    // then
    assertThat(dataCfg.getDisk().getFreeSpace().getProcessing()).isEqualTo(DataSize.ofGigabytes(2));
    assertThat(dataCfg.getDisk().getFreeSpace().getReplication())
        .isEqualTo(DataSize.ofGigabytes(1));
  }

  @Test
  public void shouldOverrideWhenOldWatermarksConfigProvided() {
    // when
    final DataCfg dataCfg = new DataCfg();
    dataCfg.setDiskUsageMonitoringInterval(Duration.ofMinutes(5));
    dataCfg.setDiskUsageMonitoringEnabled(false);
    dataCfg.init(new BrokerCfg(), "/base");

    // then
    assertThat(dataCfg.getDisk().getMonitoringInterval()).isEqualTo(Duration.ofMinutes(5));
    assertThat(dataCfg.getDisk().isEnableMonitoring()).isFalse();
  }
}
