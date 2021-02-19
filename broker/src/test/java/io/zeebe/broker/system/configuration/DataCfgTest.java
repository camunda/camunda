/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class DataCfgTest {

  @Test
  public void shouldSanitizeDirectories() {
    // given
    final DataCfg sutDataCfg = new DataCfg();
    final List<String> input = Arrays.asList("", "foo ", null, "   ", "bar");
    final List<String> expected = Arrays.asList("foo", "bar");

    // when
    sutDataCfg.setDirectories(input);

    // then
    final List<String> actual = sutDataCfg.getDirectories();

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void shouldSetWatermarksTo1IfDisabled() {
    // given
    final DataCfg dataCfg = new DataCfg();
    dataCfg.setDiskUsageCommandWatermark(0.1);
    dataCfg.setDiskUsageReplicationWatermark(0.2);
    dataCfg.setDiskUsageMonitoringEnabled(false);

    // when
    dataCfg.init(new BrokerCfg(), "/base");

    // then
    assertThat(dataCfg.isDiskUsageMonitoringEnabled()).isFalse();
    assertThat(dataCfg.getDiskUsageCommandWatermark()).isEqualTo(1.0);
    assertThat(dataCfg.getDiskUsageReplicationWatermark()).isEqualTo(1.0);
  }
}
