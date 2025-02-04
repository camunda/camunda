/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.management;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.NullNode;
import io.camunda.zeebe.qa.util.actuator.FlowControlActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class FlowControlEndpointIT {
  @TestZeebe(initMethod = "initTestCluster")
  private static TestCluster cluster;

  @SuppressWarnings("unused")
  static void initTestCluster() {
    cluster =
        TestCluster.builder()
            .useRecordingExporter(true)
            .withBrokersCount(2)
            .withPartitionsCount(2)
            .withReplicationFactor(1)
            .withEmbeddedGateway(true)
            .build();
  }

  @Test
  void shouldSetFLowControl() {
    // given
    final var actuator = FlowControlActuator.of(cluster.availableGateway());
    actuator.setFlowControlConfiguration(
        // language=JSON
        """
        {
         "write": {
           "rampUp": 0,
           "enabled": true,
           "limit": 999
          },
          "request": {
            "useWindowed": false,
            "algorithm": "VEGAS",
            "vegas": {
              "alpha": 3,
              "beta": 6,
              "initialLimit": 50
            }
          }
        }""");
    final var flowControlConfiguration = actuator.getFlowControlConfiguration();

    // then
    final var requestLimiter = flowControlConfiguration.get(1).get("requestLimiter");
    assertThat(requestLimiter.get("limit").asInt()).isEqualTo(50);
    assertThat(requestLimiter.get("estimatedLimit").asDouble()).isEqualTo(50.0);
    assertThat(requestLimiter.get("rtt_noload").asInt()).isEqualTo(0);
    assertThat(requestLimiter.get("maxLimit").asInt()).isEqualTo(1000);
    assertThat(requestLimiter.get("smoothing").asDouble()).isEqualTo(1.0);

    final var writeRateLimit = flowControlConfiguration.get(1).get("writeRateLimit");
    assertThat(writeRateLimit.get("enabled").asBoolean()).isTrue();
    assertThat(writeRateLimit.get("limit").asInt()).isEqualTo(999);
    assertThat(writeRateLimit.get("rampUp").asDouble()).isEqualTo(0.0);
  }

  @Test
  void canConfigureJustOneOfTheLimits() {
    // given
    final var actuator = FlowControlActuator.of(cluster.availableGateway());
    // to configure just one of the limits, we have to set the others to null
    actuator.setFlowControlConfiguration(
        // language=JSON
        """
        {
          "request": null,
          "write": {
             "rampUp": 0,
             "enabled": true,
             "limit": 5000
          }
        }""");
    actuator.setFlowControlConfiguration(
        // language=JSON
        """
        {
          "write": null,
          "request": {
            "enabled":true,
            "useWindowed":false,
            "legacyVegas": {
              "maxConcurrency": 32768
            },
            "algorithm":"LEGACY_VEGAS"
          }
        }""");

    // then
    final var flowControlConfiguration = actuator.getFlowControlConfiguration();
    final var requestLimiter = flowControlConfiguration.get(1).get("requestLimiter");
    assertThat(requestLimiter.get("limit").asInt()).isEqualTo(1024);
    assertThat(requestLimiter.get("estimatedLimit").asDouble()).isEqualTo(1024.0);
    assertThat(requestLimiter.get("rtt_noload").asInt()).isEqualTo(0);
    assertThat(requestLimiter.get("maxLimit").asInt()).isEqualTo(32768);
    assertThat(requestLimiter.get("smoothing").asDouble()).isEqualTo(1.0);
    final var writeRateLimit = flowControlConfiguration.get(1).get("writeRateLimit");
    assertThat(writeRateLimit.get("enabled").asBoolean()).isTrue();
    assertThat(writeRateLimit.get("limit").asInt()).isEqualTo(5000);
    assertThat(writeRateLimit.get("rampUp").asDouble()).isEqualTo(0.0);
  }

  @Test
  void canDisableALimit() {
    // given
    final var actuator = FlowControlActuator.of(cluster.availableGateway());
    actuator.setFlowControlConfiguration(
        // language=JSON
        """
        {
          "request": {
            "enabled": false
          },
          "write": {
            "enabled": false,
            "rampUp": 0,
            "limit": 1000
          }
        }
        """);

    // then
    final var flowControlConfiguration = actuator.getFlowControlConfiguration();
    assertThat(flowControlConfiguration.get(1).get("requestLimiter")).isInstanceOf(NullNode.class);
    final var writeRateLimit = flowControlConfiguration.get(1).get("writeRateLimit");
    assertThat(writeRateLimit.get("enabled").asBoolean()).isFalse();
    assertThat(writeRateLimit.get("limit").asInt()).isEqualTo(1000);
    assertThat(writeRateLimit.get("rampUp").asDouble()).isEqualTo(0.0);
  }
}
