/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.clustering.dynamic;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.management.cluster.CompletedChange;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class CancelChangeTest {
  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .useRecordingExporter(true)
          .withEmbeddedGateway(true)
          .withBrokersCount(1)
          .build();

  @Test
  void shouldCancelOngoingChange() {
    // given
    final ClusterActuator actuator = ClusterActuator.of(cluster.availableGateway());
    final var initialTopology = actuator.getTopology();

    // broker does not exist, so operation will never complete
    final var addResponse = actuator.addBroker(1);
    Utils.assertChangeIsPlanned(addResponse);

    // when
    final var cancelResponse = actuator.cancelChange(addResponse.getChangeId());

    // then
    assertThat(cancelResponse.getLastChange().getStatus())
        .isEqualTo(CompletedChange.StatusEnum.CANCELLED);
    assertThat(cancelResponse.getLastChange().getId()).isEqualTo(addResponse.getChangeId());
    assertThat(cancelResponse.getBrokers())
        .describedAs("Topology is not changed")
        .isEqualTo(initialTopology.getBrokers());
    assertThat(cancelResponse).isEqualTo(actuator.getTopology());
  }
}
