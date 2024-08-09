/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.Decision;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.search.response.DecisionDefinition;
import io.camunda.zeebe.client.impl.search.response.DecisionDefinitionImpl;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class SearchDecisionDefinitionTest {
  private static final List<Decision> DEPLOYED_DECISIONS = new ArrayList<>();
  private static ZeebeClient zeebeClient;

  @TestZeebe
  private static TestStandaloneCamunda testStandaloneCamunda = new TestStandaloneCamunda();

  @BeforeAll
  public static void setup() {
    zeebeClient = testStandaloneCamunda.newClientBuilder().build();

    DEPLOYED_DECISIONS.addAll(deployResource("decision/decision_model.dmn").getDecisions());
    DEPLOYED_DECISIONS.addAll(deployResource("decision/decision_model_1.dmn").getDecisions());
    DEPLOYED_DECISIONS.addAll(deployResource("decision/decision_model_1_v2.dmn").getDecisions());

    waitForDecisionsBeingExported();
  }

  @Test
  void shouldRetrieveAllDecisionDefinitions() {
    // when
    final var result =
        zeebeClient.newDecisionDefinitionQuery().sort(b -> b.decisionKey().asc()).send().join();

    // then
    assertThat(result.items().size()).isEqualTo(3);
    assertThat(result.items())
        .isEqualTo(
            DEPLOYED_DECISIONS.stream()
                .map(this::toDecisionDefinition)
                .sorted(Comparator.comparing(DecisionDefinition::getDecisionKey))
                .toList());
  }

  @Test
  void shouldRetrieveByDecisionRequirementsKey() {
    // when
    final long decisionKey = DEPLOYED_DECISIONS.get(0).getDecisionKey();
    final var result =
        zeebeClient
            .newDecisionDefinitionQuery()
            .filter(f -> f.decisionKey(decisionKey))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().get(0)).isEqualTo(toDecisionDefinition(DEPLOYED_DECISIONS.get(0)));
  }

  private static void waitForDecisionsBeingExported() {
    Awaitility.await("should receive data from ES")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = zeebeClient.newDecisionDefinitionQuery().send().join();
              assertThat(result.items().size()).isEqualTo(3);
            });
  }

  private static DeploymentEvent deployResource(final String resourceName) {
    return zeebeClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(resourceName)
        .send()
        .join();
  }

  private DecisionDefinition toDecisionDefinition(final Decision decision) {
    return new DecisionDefinitionImpl(
        decision.getDmnDecisionId(),
        decision.getDmnDecisionName(),
        decision.getVersion(),
        decision.getDecisionKey(),
        decision.getDmnDecisionRequirementsId(),
        decision.getDecisionRequirementsKey(),
        decision.getTenantId());
  }
}
