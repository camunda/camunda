/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.management;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.qa.util.actuator.FlowControlActuator;
import io.camunda.zeebe.qa.util.actuator.GetFlowControlActuator;
import io.camunda.zeebe.qa.util.actuator.SetFlowControlActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
@AutoCloseResources
final class FlowControlEndpointIT {
  @TestZeebe
  private static final TestCluster CLUSTER =
      TestCluster.builder()
          .useRecordingExporter(true)
          .withBrokersCount(2)
          .withPartitionsCount(2)
          .withReplicationFactor(1)
          .withEmbeddedGateway(true)
          .build();

  final String requestConfig =
      "{\n"
          + "        \"useWindowed\": false,\n"
          + "        \"vegas\":\n"
          + "        {\n"
          + "                \"alpha\": 3,\n"
          + "                \"beta\": 6,\n"
          + "                \"initialLimit\": 50\n"
          + "        },\n"
          + "        \"algorithm\": \"VEGAS\"\n"
          + "}\n";
  final String appendConfig =
      "{\n"
          + "        \"useWindowed\": false,\n"
          + "        \"vegas\":\n"
          + "        {\n"
          + "                \"alpha\": 3,\n"
          + "                \"beta\": 6,\n"
          + "                \"initialLimit\": 20\n"
          + "        },\n"
          + "        \"algorithm\": \"VEGAS\"\n"
          + "}\n";

  @AutoCloseResource private final ZeebeClient client = CLUSTER.newClientBuilder().build();

  @BeforeEach
  void beforeEach() {
    final var client = CLUSTER.newClientBuilder().build();
  }

  @Test
  void shouldSetFLowControl() {
    // given
    getActuator().setFlowControlConfiguration(requestConfig, appendConfig);
    final List<String> flowControlConfiguration = getActuator().getFlowControlConfiguration();

    // then
    assertThat(flowControlConfiguration.getFirst())
        .contains(
            "REQUEST=VegasLimit [limit=50, rtt_noload=0.0 ms]}",
            "APPEND=VegasLimit [limit=20, rtt_noload=0.0 ms]");
  }

  @Test
  void failsToParseJsonBody() {
    // given
    final String body = getActuator().setFlowControlConfiguration("{{", "null");
    assertThat(body).contains("Failed to parse flow control configuration");
  }

  private FlowControlActuator getActuator() {
    return new FlowControlActuator(
        GetFlowControlActuator.of(CLUSTER.availableGateway()),
        SetFlowControlActuator.of(CLUSTER.availableGateway()));
  }
}
