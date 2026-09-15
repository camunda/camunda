/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.system;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.core.util.Yaml;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class SystemConfigurationContractTest {

  @Test
  void shouldExposeRequiredWaitStatesEnabledWithInheritedDeploymentVersion() throws IOException {
    // given
    final JsonNode spec;
    try (final var input = new ClassPathResource("v2/system.yaml").getInputStream()) {
      spec = Yaml.mapper().readTree(input);
    }

    // when
    final var operation = spec.at("/paths/~1system~1configuration/get");
    final var responseRef =
        operation.at("/responses/200/content/application~1json/schema/$ref").asText();
    final var response = spec.at(responseRef.substring(1));
    final var deploymentRef = response.at("/properties/deployment/$ref").asText();
    final var deployment = spec.at(deploymentRef.substring(1));
    final var waitStatesEnabled = deployment.at("/properties/waitStatesEnabled");

    // then
    assertThat(operation.path("x-scope").asText()).isEqualTo("physical-tenant");
    assertThat(response.path("required")).extracting(JsonNode::asText).contains("deployment");
    assertThat(deployment.path("required"))
        .extracting(JsonNode::asText)
        .contains("waitStatesEnabled");
    assertThat(waitStatesEnabled.path("type").asText()).isEqualTo("boolean");
    assertThat(waitStatesEnabled.path("nullable").asBoolean()).isFalse();
    assertThat(response.path("x-properties-added-in-version"))
        .anySatisfy(
            annotation -> {
              assertThat(annotation.path("propertyName").asText()).isEqualTo("deployment");
              assertThat(annotation.path("addedInVersion").asText()).isEqualTo("8.10");
            });
    assertThat(deployment.path("x-properties-added-in-version"))
        .noneSatisfy(
            annotation ->
                assertThat(annotation.path("propertyName").asText())
                    .isEqualTo("waitStatesEnabled"));
  }
}
