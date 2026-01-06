/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.management;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.qa.util.actuator.ActorClockActuator;
import io.camunda.zeebe.qa.util.actuator.ActorClockActuator.AddTimeRequest;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class ActorClockEndpointIT {

  @TestZeebe(initMethod = "initTestCluster")
  private static TestCluster cluster;

  @SuppressWarnings("unused")
  static void initTestCluster() {
    cluster =
        TestCluster.builder()
            .useRecordingExporter(true)
            .withEmbeddedGateway(true)
            .withBrokersCount(1)
            .withPartitionsCount(1)
            .withReplicationFactor(1)
            .withBrokerConfig(
                testStandaloneBroker ->
                    testStandaloneBroker.withProperty("zeebe.clock.controlled", true))
            .build();
  }

  @Test
  void shouldIncreaseActorClockTime() {
    final var duration = Duration.ofMinutes(15);
    final var actuator = actorClockActuator();

    final var before = actuator.getCurrentClock().instant();
    actuator.addTime(new AddTimeRequest(duration.toMillis()));
    final var after = actuator.getCurrentClock().instant();

    assertThat(Duration.between(before, after)).isGreaterThan(duration);
  }

  private ActorClockActuator actorClockActuator() {
    final var broker = cluster.brokers().get(MemberId.from("0"));
    return ActorClockActuator.of(broker);
  }
}
