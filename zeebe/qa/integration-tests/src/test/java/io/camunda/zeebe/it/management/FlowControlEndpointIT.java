/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.management;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.qa.util.actuator.FlowControlActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import java.util.Map;
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
    final Map<Integer, JsonNode> flowControlConfiguration =
        getActuator().getFlowControlConfiguration();

    // then
    assertThat(flowControlConfiguration.get(1).toString())
        .contains(
            "\"request\":{\"limit\":50,\"estimatedLimit\":50.0,\"rtt_noload\":0,\"maxLimit\":1000,\"smoothing\":1.0}",
            "\"append\":{\"limit\":20,\"estimatedLimit\":20.0,\"rtt_noload\":0,\"maxLimit\":1000,\"smoothing\":1.0}");
  }

  private FlowControlActuator getActuator() {
    return FlowControlActuator.of(CLUSTER.availableGateway());
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
    final Map<Integer, JsonNode> flowControlConfiguration =
        getActuator().getFlowControlConfiguration();

    // then
    assertThat(flowControlConfiguration.get(1).toString())
        .contains(
            "\"request\":{\"limit\":1024,\"estimatedLimit\":1024.0,\"rtt_noload\":0,\"maxLimit\":32768,\"smoothing\":1.0}",
            "\"append\":{\"limit\":20}");
  }

  @Test
  void canDisableALimit() {
    // given
    getActuator()
        .setFlowControlConfiguration("{ \"request\": { \"enabled\": false }, \"append\": null }");
    final Map<Integer, JsonNode> flowControlConfiguration =
        getActuator().getFlowControlConfiguration();

    // then
    assertThat(flowControlConfiguration.get(1).toString()).contains("\"request\":null");
  }
}
