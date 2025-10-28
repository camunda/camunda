/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering.dynamic;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.management.cluster.MessageCorrelationHashMod;
import io.camunda.zeebe.management.cluster.RequestHandlingAllPartitions;
import io.camunda.zeebe.management.cluster.RoutingState;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import org.awaitility.Awaitility;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ZeebeIntegration
public class ClusterRoutingEndpointIT {

  @TestZeebe(initMethod = "initTestStandaloneBroker")
  static TestStandaloneBroker broker;

  @SuppressWarnings("unused")
  static void initTestStandaloneBroker() {
    broker = new TestStandaloneBroker();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldPatchRoutingStateFromEngineState(final boolean dryRun) {
    //    broker.awaitCompleteTopology();
    final var actuator = ClusterActuator.of(broker);
    actuator.patchRoutingState(dryRun);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldPatchRoutingStateFromRequestBody(final boolean dryRun) {
    broker.awaitCompleteTopology();
    final var actuator = ClusterActuator.of(broker);
    final var routingState =
        new RoutingState()
            .requestHandling(new RequestHandlingAllPartitions(2).strategy("AllPartitions"))
            .messageCorrelation(new MessageCorrelationHashMod("HashMod", 2))
            .version(2L);
    actuator.patchRoutingState(routingState, dryRun);

    if (!dryRun) {
      Awaitility.await("Until routing state is updated)")
          .untilAsserted(
              () -> assertThat(actuator.getTopology().getRouting()).isEqualTo(routingState));
    }
  }
}
