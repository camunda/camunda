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

  @AutoCloseResource private final ZeebeClient client = CLUSTER.newClientBuilder().build();

  @BeforeEach
  void beforeEach() {
    final var client = CLUSTER.newClientBuilder().build();
  }

  @Test
  void shouldSetFLowControl() {
    // given
    getActuator()
        .setFlowControlConfiguration(
            "{\n"
                + "  \"append\": {\n"
                + "    \"useWindowed\": false,\n"
                + "    \"algorithm\": \"VEGAS\",\n"
                + "    \"vegas\": {\n"
                + "      \"alpha\": 3,\n"
                + "      \"beta\": 6,\n"
                + "      \"initialLimit\": 20\n"
                + "    }\n"
                + "  },\n"
                + "  \"request\": {\n"
                + "    \"useWindowed\": false,\n"
                + "    \"algorithm\": \"VEGAS\",\n"
                + "    \"vegas\": {\n"
                + "      \"alpha\": 3,\n"
                + "      \"beta\": 6,\n"
                + "      \"initialLimit\": 50\n"
                + "    }\n"
                + "  }\n"
                + "}");
    final String flowControlConfiguration = getActuator().getFlowControlConfiguration();

    // then
    assertThat(flowControlConfiguration)
        .contains(
            "REQUEST=VegasLimit [limit=50, rtt_noload=0.0 ms]",
            "APPEND=VegasLimit [limit=20, rtt_noload=0.0 ms]");
  }

  private FlowControlActuator getActuator() {
    return new FlowControlActuator(
        GetFlowControlActuator.of(CLUSTER.availableGateway()),
        SetFlowControlActuator.of(CLUSTER.availableGateway()));
  }

  @Test
  void canConfigureJustOneOfTheLimits() {
    // given
    // to configure just one of the limits, we have to set the others to null
    getActuator()
        .setFlowControlConfiguration(
            "{ "
                + "  \"request\": null, "
                + "  \"append\": "
                + "  {"
                + "    \"enabled\":true,"
                + "    \"useWindowed\":false,"
                + "    \"fixed\":{\"limit\":20},"
                + "    \"algorithm\":\"FIXED\""
                + "  }"
                + "}");
    getActuator()
        .setFlowControlConfiguration(
            "{ "
                + "  \"append\": null, "
                + "  \"request\": "
                + "  {"
                + "    \"enabled\":true,"
                + "    \"useWindowed\":false,"
                + "    \"legacyVegas\": {"
                + "      \"maxConcurrency\": 32768"
                + "    },"
                + "    \"algorithm\":\"LEGACY_VEGAS\""
                + "  }"
                + "}");
    final String flowControlConfiguration = getActuator().getFlowControlConfiguration();

    // then
    assertThat(flowControlConfiguration)
        .contains(
            "APPEND=FixedLimit [limit=20]", "REQUEST=VegasLimit [limit=1024, rtt_noload=0.0 ms]");
  }

  @Test
  void canDisableALimit() {
    // given
    getActuator().setFlowControlConfiguration("{ \"request\": { \"enabled\": false } }");
    final String flowControlConfiguration = getActuator().getFlowControlConfiguration();

    // then
    assertThat(flowControlConfiguration).contains("REQUEST=null");
  }
}
