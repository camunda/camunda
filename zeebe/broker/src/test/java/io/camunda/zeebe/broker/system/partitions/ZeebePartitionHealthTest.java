/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.zeebe.util.health.HealthStatus;
import org.junit.jupiter.api.Test;

final class ZeebePartitionHealthTest {

  private final ZeebePartitionHealth health =
      new ZeebePartitionHealth(1, mock(PartitionTransition.class));

  @Test
  void shouldBeStartingUntilServicesAreInstalledForTheFirstTime() {
    // when
    final var report = health.getHealthReport();

    // then
    assertThat(report.status()).isEqualTo(HealthStatus.STARTING);
    assertThat(report.issue().message()).isEqualTo("Services not installed yet");
  }

  @Test
  void shouldBeUnhealthyWhenServicesAreUninstalledLater() {
    // given - a role transition after the partition was serving
    health.setServicesInstalled(true);

    // when
    health.setServicesInstalled(false);

    // then
    final var report = health.getHealthReport();
    assertThat(report.status()).isEqualTo(HealthStatus.UNHEALTHY);
    assertThat(report.issue().message()).isEqualTo("Services not installed");
  }
}
