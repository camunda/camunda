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

import io.camunda.it.utils.BrokerWithCamundaExporterITInvocationProvider;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.EvaluateDecisionResponse;
import io.camunda.zeebe.client.api.search.response.DecisionDefinitionType;
import io.camunda.zeebe.client.api.search.response.DecisionInstance;
import io.camunda.zeebe.client.api.search.response.DecisionInstanceState;
import io.camunda.zeebe.client.protocol.rest.BasicLongFilterProperty;
import io.camunda.zeebe.client.protocol.rest.DateTimeFilterProperty;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@TestInstance(Lifecycle.PER_CLASS)
@ExtendWith(BrokerWithCamundaExporterITInvocationProvider.class)
class DecisionInstanceQueryTest {

  private static final String DECISION_DEFINITION_ID_1 = "decision_1";
  private static final String DECISION_DEFINITION_ID_2 = "invoiceAssignApprover";
  // evaluated decisions mapped by decision definition id
  private final Map<String, EvaluateDecisionResponse> evaluatedDecisions = new HashMap<>();
  private boolean initialized;

  @BeforeEach
  void setUp(final ZeebeClient zeebeClient) {
    if (!initialized) {

      List.of("decision_model.dmn", "invoiceBusinessDecisions_v_1.dmn")
          .forEach(
              dmn ->
                  deployResource(zeebeClient, String.format("decisions/%s", dmn)).getDecisions());
      evaluatedDecisions.put(
          DECISION_DEFINITION_ID_1,
          evaluateDecision(
              zeebeClient, DECISION_DEFINITION_ID_1, "{\"age\": 20, \"income\": 20000}"));
      evaluatedDecisions.put(
          DECISION_DEFINITION_ID_2,
          evaluateDecision(
              zeebeClient,
              DECISION_DEFINITION_ID_2,
              "{\"amount\": 100, \"invoiceCategory\": \"Misc\"}"));
      waitForDecisionsToBeEvaluated(
          zeebeClient,
          evaluatedDecisions.values().stream()
              .mapToInt(v -> v.getEvaluatedDecisions().size())
              .sum());
      initialized = true;
    }
  }

  @TestTemplate
  public void shouldRetrieveAllDecisionInstances(final ZeebeClient zeebeClient) {
    // when
    final var result =
        zeebeClient
            .newDecisionInstanceQuery()
            .sort(b -> b.decisionInstanceKey().asc())
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(3);

    assertThat(
            result.items().stream()
                .map(DecisionInstance::getDecisionInstanceKey)
                .collect(Collectors.toSet()))
        .containsExactlyElementsOf(
            evaluatedDecisions.values().stream()
                .map(EvaluateDecisionResponse::getDecisionInstanceKey)
                .collect(Collectors.toSet()));
  }

  @TestTemplate
  public void shouldRetrieveDecisionInstanceByDecisionDefinitionKey(final ZeebeClient zeebeClient) {
    // when
    final long decisionDefinitionKey =
        evaluatedDecisions.get(DECISION_DEFINITION_ID_1).getDecisionKey();
    final var result =
        zeebeClient
            .newDecisionInstanceQuery()
            .filter(f -> f.decisionDefinitionKey(decisionDefinitionKey))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getDecisionDefinitionKey())
        .isEqualTo(decisionDefinitionKey);
    assertThat(result.items().getFirst().getDecisionInstanceKey())
        .isEqualTo(evaluatedDecisions.get(DECISION_DEFINITION_ID_1).getDecisionInstanceKey());
  }

  @TestTemplate
  public void shouldRetrieveDecisionInstanceByDecisionKeyFilterIn(final ZeebeClient zeebeClient) {
    // when
    final long decisionDefinitionKey =
        evaluatedDecisions.get(DECISION_DEFINITION_ID_1).getDecisionKey();
    final BasicLongFilterProperty filter = new BasicLongFilterProperty();
    filter.set$In(List.of(Long.MAX_VALUE, decisionDefinitionKey));
    final var result =
        zeebeClient
            .newDecisionInstanceQuery()
            .filter(f -> f.decisionDefinitionKey(filter))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getDecisionDefinitionKey())
        .isEqualTo(decisionDefinitionKey);
    assertThat(result.items().getFirst().getDecisionInstanceKey())
        .isEqualTo(evaluatedDecisions.get(DECISION_DEFINITION_ID_1).getDecisionInstanceKey());
  }

  @TestTemplate
  public void shouldRetrieveDecisionInstanceByDecisionInstanceKey(final ZeebeClient zeebeClient) {
    // when
    final long decisionInstanceKey =
        evaluatedDecisions.get(DECISION_DEFINITION_ID_2).getDecisionInstanceKey();
    final var result =
        zeebeClient
            .newDecisionInstanceQuery()
            .filter(f -> f.decisionInstanceKey(decisionInstanceKey))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(result.items().getFirst().getDecisionInstanceKey()).isEqualTo(decisionInstanceKey);
    assertThat(result.items().getLast().getDecisionInstanceKey()).isEqualTo(decisionInstanceKey);
  }

  @TestTemplate
  public void shouldRetrieveDecisionInstanceByStateAndType(final ZeebeClient zeebeClient) {
    // when
    final DecisionInstanceState state = DecisionInstanceState.EVALUATED;
    final DecisionDefinitionType type = DecisionDefinitionType.DECISION_TABLE;
    final var result =
        zeebeClient
            .newDecisionInstanceQuery()
            .filter(f -> f.state(state).decisionDefinitionType(type))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(3);
  }

  @TestTemplate
  public void shouldRetrieveDecisionInstanceByEvaluationDate(final ZeebeClient zeebeClient) {
    // given
    final var allResult =
        zeebeClient.newDecisionInstanceQuery().page(p -> p.limit(1)).send().join();
    final var di = allResult.items().getFirst();

    // when
    final var result =
        zeebeClient
            .newDecisionInstanceQuery()
            .filter(f -> f.evaluationDate(OffsetDateTime.parse(di.getEvaluationDate())))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getDecisionInstanceKey())
        .isEqualTo(di.getDecisionInstanceKey());
  }

  @TestTemplate
  public void shouldRetrieveDecisionInstanceByEvaluationDateFilterGt(
      final ZeebeClient zeebeClient) {
    // given
    final var allResult =
        zeebeClient
            .newDecisionInstanceQuery()
            .sort(s -> s.evaluationDate().asc())
            .page(p -> p.limit(1))
            .send()
            .join();
    final var di = allResult.items().getFirst();
    final DateTimeFilterProperty filter = new DateTimeFilterProperty();
    filter.set$Gt(di.getEvaluationDate());

    // when
    final var result =
        zeebeClient.newDecisionInstanceQuery().filter(f -> f.evaluationDate(filter)).send().join();

    // then
    assertThat(result.items()).hasSize(2);
    final var requestDate = OffsetDateTime.parse(di.getEvaluationDate());
    assertThat(result.items())
        .extracting("evaluationDate", String.class)
        .allMatch(date -> requestDate.isBefore(OffsetDateTime.parse(date)));
    assertThat(result.items())
        .extracting("decisionInstanceKey", Long.class)
        .noneMatch(key -> di.getDecisionInstanceKey() == key);
  }

  @TestTemplate
  public void shouldRetrieveDecisionInstanceByEvaluationDateFilterGte(
      final ZeebeClient zeebeClient) {
    // given
    final var allResult =
        zeebeClient
            .newDecisionInstanceQuery()
            .sort(s -> s.evaluationDate().asc())
            .page(p -> p.limit(1))
            .send()
            .join();
    final var di = allResult.items().getFirst();
    final DateTimeFilterProperty filter = new DateTimeFilterProperty();
    filter.set$Gte(di.getEvaluationDate());

    // when
    final var result =
        zeebeClient.newDecisionInstanceQuery().filter(f -> f.evaluationDate(filter)).send().join();

    // then
    assertThat(result.items()).hasSize(3);
    final var requestDate = OffsetDateTime.parse(di.getEvaluationDate());
    assertThat(result.items())
        .extracting("evaluationDate", String.class)
        .allMatch(date -> !OffsetDateTime.parse(date).isBefore(requestDate));
    assertThat(result.items())
        .extracting("decisionInstanceKey", Long.class)
        .anyMatch(key -> di.getDecisionInstanceKey() == key);
  }

  @TestTemplate
  public void shouldRetrieveDecisionInstanceByDmnDecisionIdAndDecisionVersion(
      final ZeebeClient zeebeClient) {
    // when
    final String dmnDecisionId = evaluatedDecisions.get(DECISION_DEFINITION_ID_1).getDecisionId();
    final int decisionVersion =
        evaluatedDecisions.get(DECISION_DEFINITION_ID_1).getDecisionVersion();
    final var result =
        zeebeClient
            .newDecisionInstanceQuery()
            .filter(
                f ->
                    f.decisionDefinitionId(dmnDecisionId)
                        .decisionDefinitionVersion(decisionVersion))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().get(0).getDecisionInstanceKey())
        .isEqualTo(evaluatedDecisions.get(DECISION_DEFINITION_ID_1).getDecisionInstanceKey());
  }

  @TestTemplate
  void shouldGetDecisionInstance(final ZeebeClient zeebeClient) {
    // when
    final long decisionInstanceKey =
        evaluatedDecisions.get(DECISION_DEFINITION_ID_2).getDecisionInstanceKey();
    final var decisionInstanceId = "%d-%d".formatted(decisionInstanceKey, 1);
    final var result = zeebeClient.newDecisionInstanceGetRequest(decisionInstanceId).send().join();

    // then
    assertThat(result.getDecisionInstanceId()).isEqualTo(decisionInstanceId);
    assertThat(result.getDecisionInstanceKey()).isEqualTo(decisionInstanceKey);
  }

  @TestTemplate
  void shouldReturn404ForNotFoundDecisionInstance(final ZeebeClient zeebeClient) {
    // when
    final var decisionInstanceId = "not-existing";
    final var problemException =
        assertThrows(
            ProblemException.class,
            () -> zeebeClient.newDecisionInstanceGetRequest(decisionInstanceId).send().join());
    // then
    assertThat(problemException.code()).isEqualTo(404);
    assertThat(problemException.details().getDetail())
        .isEqualTo("Decision instance with key %s not found".formatted(decisionInstanceId));
  }

  private DeploymentEvent deployResource(final ZeebeClient zeebeClient, final String resourceName) {
    return zeebeClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(resourceName)
        .send()
        .join();
  }

  private EvaluateDecisionResponse evaluateDecision(
      final ZeebeClient zeebeClient, final String decisionDefinitionId, final String variables) {
    return zeebeClient
        .newEvaluateDecisionCommand()
        .decisionId(decisionDefinitionId)
        .variables(variables)
        .send()
        .join();
  }

  private void waitForDecisionsToBeEvaluated(
      final ZeebeClient zeebeClient, final int expectedCount) {
    Awaitility.await("should deploy decision definitions and import in Operate")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = zeebeClient.newDecisionInstanceQuery().send().join();
              assertThat(result.items().size()).isEqualTo(expectedCount);
            });
  }
}
