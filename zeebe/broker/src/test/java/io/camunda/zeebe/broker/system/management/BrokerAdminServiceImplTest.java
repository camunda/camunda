/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.zeebe.broker.partitioning.PartitionManager;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the exporting operations delegate to the durable {@link ExportingStateChanger}
 * (dynamic cluster configuration) rather than pausing exporting only locally. Pausing locally would
 * not survive a broker restart, which is the bug this wiring fixes.
 */
final class BrokerAdminServiceImplTest {

  private final List<ExportingState> requestedStates = new ArrayList<>();
  private final BrokerAdminServiceImpl adminService =
      new BrokerAdminServiceImpl(mock(PartitionManager.class), requestedStates::add);

  @Test
  void shouldPauseExportingViaDynamicConfig() {
    // when
    adminService.pauseExporting();

    // then
    assertThat(requestedStates).containsExactly(ExportingState.PAUSED);
  }

  @Test
  void shouldSoftPauseExportingViaDynamicConfig() {
    // when
    adminService.softPauseExporting();

    // then
    assertThat(requestedStates).containsExactly(ExportingState.SOFT_PAUSED);
  }

  @Test
  void shouldResumeExportingViaDynamicConfig() {
    // when
    adminService.resumeExporting();

    // then
    assertThat(requestedStates).containsExactly(ExportingState.EXPORTING);
  }
}
