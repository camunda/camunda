/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Test;

public class BrokerHealthCheckServiceTest {

  @Test
  public void shouldNotBeReadyHealthyOrStartedBeforePartitionManagerIsRegistered() {
    // given
    final var brokerInfo = mock(BrokerInfo.class);
    final var healthCheckService =
        new BrokerHealthCheckService(brokerInfo, new HealthTreeMetrics(new SimpleMeterRegistry()));

    // when

    // ... no partition manager is registered

    // ... and we ask about the health status
    final var healthyActual = healthCheckService.isBrokerHealthy();
    final var startedActual = healthCheckService.isBrokerStarted();
    final var readyActual = healthCheckService.isBrokerReady();

    // then
    assertThat(healthyActual).isFalse();
    assertThat(startedActual).isFalse();
    assertThat(readyActual).isFalse();
  }

  @Test
  public void shouldThrowIllegalStateExceptionIfStatusIsUpdatedBeforePartitionsAreKnown() {
    // given
    final var brokerInfo = mock(BrokerInfo.class);
    final var healthCheckService =
        new BrokerHealthCheckService(brokerInfo, new HealthTreeMetrics(new SimpleMeterRegistry()));

    // when + then

    assertThatThrownBy(() -> healthCheckService.onBecameRaftFollower(0, 0))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> healthCheckService.onBecameRaftLeader(0, 0))
        .isInstanceOf(IllegalStateException.class);
  }
}
