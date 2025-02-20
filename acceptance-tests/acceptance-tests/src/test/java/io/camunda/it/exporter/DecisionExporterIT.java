/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.it.utils.BrokerITInvocationProvider;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(BrokerITInvocationProvider.class)
final class DecisionExporterIT {

  @TestTemplate
  void shouldExportDecision(final TestStandaloneBroker testBroker) {
    // given
    final var client = testBroker.newClientBuilder().build();

    final var resource =
        client
            .newDeployResourceCommand()
            .addResourceFromClasspath("decisions/decision_model.dmn")
            .send()
            .join();
    final var expectedDecisionName = resource.getDecisions().get(0).getDmnDecisionName();

    // when
    // broker has exported
    Awaitility.await()
        .ignoreExceptions()
        .timeout(Duration.ofSeconds(30))
        .until(() -> !client.newDecisionDefinitionQuery().send().join().items().isEmpty());

    // then
    final var result = client.newDecisionDefinitionQuery().send().join();
    assertThat(result.items().get(0).getDmnDecisionName()).isEqualTo(expectedDecisionName);
  }
}
