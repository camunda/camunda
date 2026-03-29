/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.historycleanup;

import static io.camunda.it.util.DmnBuilderHelper.getDmnModelInstance;
import static io.camunda.it.util.TestHelper.deployDmnModel;
import static io.camunda.it.util.TestHelper.evaluateDecision;
import static io.camunda.it.util.TestHelper.waitForDecisionInstanceCount;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension.DatabaseType;
import io.camunda.qa.util.multidb.HistoryMultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@HistoryMultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class StandaloneDecisionHistoryCleanupIT {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(StandaloneDecisionHistoryCleanupIT.class);

  private static CamundaClient camundaClient;
  private static DatabaseType databaseType;
  private static Duration cleanupTimeout;

  @BeforeAll
  static void setup() {
    cleanupTimeout =
        switch (databaseType) {
          case OS, AWS_OS -> Duration.ofMinutes(5);
          default -> Duration.ofSeconds(30);
        };
  }

  @Test
  void shouldCleanupStandaloneDecisionInstances() {
    // given — deploy a DMN and evaluate it outside any process context (standalone)
    final var decisionId = Strings.newRandomValidBpmnId();
    final var dmnModel = getDmnModelInstance(decisionId);
    final var decision =
        deployDmnModel(camundaClient, dmnModel, dmnModel.getModel().getModelName());

    evaluateDecision(camundaClient, decision.getDecisionKey(), "{}");
    evaluateDecision(camundaClient, decision.getDecisionKey(), "{}");

    // wait for both decision instances to be visible in secondary storage
    waitForDecisionInstanceCount(
        camundaClient, f -> f.decisionDefinitionId(decision.getDmnDecisionId()), 2);

    // then — the archiver should pick them up and ILM/ISM should delete them
    Awaitility.await("Wait for standalone decision instances to be cleaned up")
        .logging(LOGGER::trace)
        .timeout(cleanupTimeout)
        .pollInterval(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newDecisionInstanceSearchRequest()
                            .filter(f -> f.decisionDefinitionId(decision.getDmnDecisionId()))
                            .send()
                            .join()
                            .items())
                    .isEmpty());
  }
}
