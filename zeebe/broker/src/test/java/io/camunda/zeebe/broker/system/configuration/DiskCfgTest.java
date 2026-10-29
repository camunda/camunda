/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

final class DiskCfgTest {

  private final BrokerCfg brokerCfg = new BrokerCfg();
  private final DiskCfg diskCfg = brokerCfg.getData().getDisk();

  @Test
  void shouldNotLimitUsedSpaceByDefault() {
    // when
    diskCfg.init(brokerCfg, "/base");

    // then
    assertThat(diskCfg.getUsedSpace().isLimited()).isFalse();
    assertThat(diskCfg.getUsedSpace().getProcessing()).isNull();
    assertThat(diskCfg.getUsedSpace().getReplication()).isNull();
  }

  @Test
  void shouldAcceptReplicationLimitAboveProcessingLimit() {
    // given
    diskCfg.getUsedSpace().setProcessing(DataSize.ofGigabytes(10));
    diskCfg.getUsedSpace().setReplication(DataSize.ofGigabytes(12));

    // when
    diskCfg.init(brokerCfg, "/base");

    // then
    assertThat(diskCfg.getUsedSpace().isLimited()).isTrue();
  }

  @Test
  void shouldAcceptProcessingLimitAlone() {
    // given
    diskCfg.getUsedSpace().setProcessing(DataSize.ofGigabytes(10));

    // when - then
    assertThatCode(() -> diskCfg.init(brokerCfg, "/base")).doesNotThrowAnyException();
  }

  @Test
  void shouldRejectReplicationLimitNotAboveProcessingLimit() {
    // given
    diskCfg.getUsedSpace().setProcessing(DataSize.ofGigabytes(10));
    diskCfg.getUsedSpace().setReplication(DataSize.ofGigabytes(10));

    // when - then
    assertThatThrownBy(() -> diskCfg.init(brokerCfg, "/base"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("usedSpace.replication")
        .hasMessageContaining("usedSpace.processing");
  }

  @Test
  void shouldRejectProcessingLimitNotAboveLogSegmentSize() {
    // given
    brokerCfg.getData().setLogSegmentSize(DataSize.ofMegabytes(128));
    diskCfg.getUsedSpace().setProcessing(DataSize.ofMegabytes(128));

    // when - then
    assertThatThrownBy(() -> diskCfg.init(brokerCfg, "/base"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("log segment size");
  }

  @Test
  void shouldRejectReplicationLimitNotAboveLogSegmentSize() {
    // given
    brokerCfg.getData().setLogSegmentSize(DataSize.ofMegabytes(128));
    diskCfg.getUsedSpace().setReplication(DataSize.ofMegabytes(64));

    // when - then
    assertThatThrownBy(() -> diskCfg.init(brokerCfg, "/base"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("log segment size");
  }

  @Test
  void shouldRemoveUsedSpaceLimitsWhenMonitoringIsDisabled() {
    // given
    diskCfg.setEnableMonitoring(false);
    diskCfg.getUsedSpace().setProcessing(DataSize.ofGigabytes(10));
    diskCfg.getUsedSpace().setReplication(DataSize.ofGigabytes(12));

    // when
    diskCfg.init(brokerCfg, "/base");

    // then
    assertThat(diskCfg.getUsedSpace().isLimited()).isFalse();
  }
}
