/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.management;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.partitioning.PartitionManager;
import io.camunda.zeebe.dynamic.config.api.ExportingStateController;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the exporting operations delegate to the durable {@link
 * ExportingStateController.ByTenant} (dynamic cluster configuration) rather than pausing exporting
 * only locally. Pausing locally would not survive a broker restart, which is the bug this wiring
 * fixes.
 */
final class BrokerAdminServiceImplTest {

  private final ExportingStateController.ByTenant exportingStateController =
      mock(ExportingStateController.ByTenant.class);
  private final BrokerAdminServiceImpl adminService =
      new BrokerAdminServiceImpl(mock(PartitionManager.class), exportingStateController);

  @BeforeEach
  void setUp() {
    when(exportingStateController.pauseExporting())
        .thenReturn(CompletableFuture.completedFuture(null));
    when(exportingStateController.softPauseExporting())
        .thenReturn(CompletableFuture.completedFuture(null));
    when(exportingStateController.resumeExporting())
        .thenReturn(CompletableFuture.completedFuture(null));
  }

  @Test
  void shouldPauseExportingViaDynamicConfig() {
    // when
    adminService.pauseExporting();

    // then
    verify(exportingStateController).pauseExporting();
  }

  @Test
  void shouldSoftPauseExportingViaDynamicConfig() {
    // when
    adminService.softPauseExporting();

    // then
    verify(exportingStateController).softPauseExporting();
  }

  @Test
  void shouldResumeExportingViaDynamicConfig() {
    // when
    adminService.resumeExporting();

    // then
    verify(exportingStateController).resumeExporting();
  }
}
