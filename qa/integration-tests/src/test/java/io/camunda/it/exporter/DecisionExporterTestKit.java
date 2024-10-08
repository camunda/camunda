/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClient;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public interface DecisionExporterTestKit {

  ZeebeClient getClient();

  @Test
  default void shouldExportDecision() {
    // given
    final var resource =
        getClient()
            .newDeployResourceCommand()
            .addResourceFromClasspath("decisions/decision_model.dmn")
            .send()
            .join();
    final var expectedDecisionName = resource.getDecisions().get(0).getDmnDecisionName();

    // when
    // broker has exported
    // TODO: Add a generic way to wait until exporter has exported all records.

    Awaitility.await()
        .timeout(Duration.ofSeconds(30))
        .until(() -> !getClient().newDecisionDefinitionQuery().send().join().items().isEmpty());

    // then
    final var result = getClient().newDecisionDefinitionQuery().send().join();
    assertThat(result.items().get(0).getDmnDecisionName()).isEqualTo(expectedDecisionName);
  }
}
