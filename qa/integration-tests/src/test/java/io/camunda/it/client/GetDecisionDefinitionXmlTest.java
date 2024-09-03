/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.client.api.response.Decision;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletionException;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class GetDecisionDefinitionXmlTest {

  private static final List<Decision> DEPLOYED_DECISIONS = new ArrayList<>();
  private static ZeebeClient zeebeClient;

  @TestZeebe
  private static TestStandaloneCamunda testStandaloneCamunda = new TestStandaloneCamunda();

  @BeforeAll
  public static void setup() {
    zeebeClient = testStandaloneCamunda.newClientBuilder().build();

    DEPLOYED_DECISIONS.addAll(deployResource("decision/decision_model.dmn").getDecisions());

    waitForDecisionsBeingExported();
  }

  @Test
  void shouldGetDecisionDefinitionXml() throws IOException {
    // when
    final long decisionKey = DEPLOYED_DECISIONS.get(0).getDecisionKey();
    final var result = zeebeClient.newDecisionDefinitionGetXmlRequest(decisionKey).send().join();

    // then
    final String expected =
        Files.readString(
            Paths.get(
                Objects.requireNonNull(
                        getClass().getClassLoader().getResource("decision/decision_model.dmn"))
                    .getPath()));
    assertThat(result).isNotEmpty();
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void shouldReturn404ForNotFoundDecisionKey() throws IOException {
    // when
    final long decisionKey = new Random().nextLong();
    final var exception =
        assertThrows(
            CompletionException.class,
            () -> zeebeClient.newDecisionDefinitionGetXmlRequest(decisionKey).send().join());
    // then
    assertThat(exception.getCause()).isInstanceOf(ProblemException.class);
    final var problemException = (ProblemException) exception.getCause();
    assertThat(problemException.code()).isEqualTo(404);
    assertThat(problemException.details().getDetail())
        .isEqualTo("DecisionDefinition with decisionKey=%d cannot be found".formatted(decisionKey));
  }

  private static DeploymentEvent deployResource(final String resourceName) {
    return zeebeClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(resourceName)
        .send()
        .join();
  }

  private static void waitForDecisionsBeingExported() {
    Awaitility.await("should receive data from ES")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = zeebeClient.newDecisionDefinitionQuery().send().join();
              assertThat(result.items().size()).isEqualTo(DEPLOYED_DECISIONS.size());
            });
  }
}
