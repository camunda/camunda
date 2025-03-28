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

<<<<<<< HEAD
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
=======
import io.atomix.cluster.MemberId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
>>>>>>> 9f146675 (fix: initialize BrokerInfo from ClusterConfiguration instead of configuration)
import org.junit.Test;

public class BrokerHealthCheckServiceTest {

  private final MemberId member = MemberId.from("member-1");

  @Test
  public void shouldNotBeReadyHealthyOrStartedBeforePartitionManagerIsRegistered() {
    // given
<<<<<<< HEAD
    final var brokerInfo = mock(BrokerInfo.class);
    final var healthCheckService = new BrokerHealthCheckService(brokerInfo);
=======
    final var healthCheckService =
        new BrokerHealthCheckService(member, new HealthTreeMetrics(new SimpleMeterRegistry()));
>>>>>>> 9f146675 (fix: initialize BrokerInfo from ClusterConfiguration instead of configuration)

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
<<<<<<< HEAD
    final var brokerInfo = mock(BrokerInfo.class);
    final var healthCheckService = new BrokerHealthCheckService(brokerInfo);
=======
    final var healthCheckService =
        new BrokerHealthCheckService(member, new HealthTreeMetrics(new SimpleMeterRegistry()));
>>>>>>> 9f146675 (fix: initialize BrokerInfo from ClusterConfiguration instead of configuration)

    // when + then

    assertThatThrownBy(() -> healthCheckService.onBecameRaftFollower(0, 0))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> healthCheckService.onBecameRaftLeader(0, 0))
        .isInstanceOf(IllegalStateException.class);
  }
}
