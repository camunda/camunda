/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.management;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.camunda.zeebe.broker.partitioning.PartitionManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The actuator's default error handling drops the exception message from the HTTP response body
 * (see {@code BrokerAdminServiceTest#shouldRejectExportingOperations} in the integration tests,
 * which can only assert on the status code), so the message pointing operators at the replacement
 * endpoints is verified here instead.
 */
final class BrokerAdminServiceImplTest {

  private final BrokerAdminServiceImpl service =
      new BrokerAdminServiceImpl(mock(PartitionManager.class));

  @ParameterizedTest
  @ValueSource(strings = {"pauseExporting", "softPauseExporting", "resumeExporting"})
  void shouldRejectExportingOperationsWithGuidanceMessage(final String operation) {
    // given
    final Runnable trigger =
        switch (operation) {
          case "pauseExporting" -> service::pauseExporting;
          case "softPauseExporting" -> service::softPauseExporting;
          default -> service::resumeExporting;
        };

    // when - then
    assertThatThrownBy(trigger::run)
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("no longer supported")
        .hasMessageContaining("/actuator/exporting");
  }
}
