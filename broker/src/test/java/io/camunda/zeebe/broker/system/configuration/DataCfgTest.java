/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.broker.system.configuration.DiskCfg.FreeSpaceCfg;
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
    assertThat(dataCfg.getDisk().getFreeSpace().getMinFreeSpaceForProcessing("ignore"))
        .isEqualTo(DataSize.ofGigabytes(2).toBytes());
    assertThat(dataCfg.getDisk().getFreeSpace().getMinFreeSpaceForReplication("ignore"))
        .isEqualTo(DataSize.ofGigabytes(1).toBytes());
  }

  @Test
  public void shouldOverrideWhenOldWatermarksConfigProvided() {
    // when
    final DataCfg dataCfg = new DataCfg();
    final FreeSpaceCfg mockFreeSpaceCfg = mock(FreeSpaceCfg.class);
    dataCfg.getDisk().setFreeSpace(mockFreeSpaceCfg);
    dataCfg.setDiskUsageMonitoringInterval(Duration.ofMinutes(5));
    dataCfg.setDiskUsageMonitoringEnabled(false);
    dataCfg.setDiskUsageCommandWatermark(0.1);
    dataCfg.setDiskUsageReplicationWatermark(0.2);
    dataCfg.init(new BrokerCfg(), "/base");

    // then
    verify(mockFreeSpaceCfg, times(1)).setProcessing("90.0%");
    verify(mockFreeSpaceCfg, times(1)).setReplication("80.0%");
    assertThat(dataCfg.getDisk().isEnableMonitoring()).isFalse();
    assertThat(dataCfg.getDisk().getMonitoringInterval()).isEqualTo(Duration.ofMinutes(5));
  }
}
