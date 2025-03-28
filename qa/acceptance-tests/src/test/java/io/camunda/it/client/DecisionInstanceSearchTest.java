/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.EvaluateDecisionResponse;
import io.camunda.client.api.search.response.DecisionDefinitionType;
import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.client.api.search.response.DecisionInstanceState;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
class DecisionInstanceSearchTest {

  private static final String DECISION_DEFINITION_ID_1 = "decision_1";
  private static final String DECISION_DEFINITION_ID_2 = "invoiceAssignApprover";
  private static final String DECISION_DEFINITION_ID_3 = "invoiceClassification";
  private static CamundaClient camundaClient;
  // evaluated decisions mapped by decision definition id
  private static final Map<String, EvaluateDecisionResponse> EVALUATED_DECISIONS = new HashMap<>();
  private boolean initialized;

  @BeforeAll
  static void setUp() {

    List.of("decision_model.dmn", "invoiceBusinessDecisions_v_1.dmn")
        .forEach(
            dmn ->
                deployResource(camundaClient, String.format("decisions/%s", dmn)).getDecisions());
    EVALUATED_DECISIONS.put(
        DECISION_DEFINITION_ID_1,
        evaluateDecision(
            camundaClient, DECISION_DEFINITION_ID_1, "{\"age\": 20, \"income\": 20000}"));
    EVALUATED_DECISIONS.put(
        DECISION_DEFINITION_ID_2,
        evaluateDecision(
            camundaClient,
            DECISION_DEFINITION_ID_2,
            "{\"amount\": 100, \"invoiceCategory\": \"Misc\"}"));
    waitForDecisionsToBeEvaluated(
        camundaClient,
        EVALUATED_DECISIONS.values().stream()
            .mapToInt(v -> v.getEvaluatedDecisions().size())
            .sum());
  }

  @Test
  public void shouldRetrieveAllDecisionInstances() {
    // when
    final var result =
        camundaClient
            .newDecisionInstanceSearchRequest()
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
            EVALUATED_DECISIONS.values().stream()
                .map(EvaluateDecisionResponse::getDecisionInstanceKey)
                .collect(Collectors.toSet()));
  }

  @Test
  void shouldSearchByFromWithLimit() {
    // when
    final var resultAll = camundaClient.newDecisionInstanceSearchRequest().send().join();

    final var resultWithLimit =
        camundaClient.newDecisionInstanceSearchRequest().page(p -> p.limit(2)).send().join();
    assertThat(resultWithLimit.items().size()).isEqualTo(2);

    final var thirdKey = resultAll.items().get(2).getDecisionInstanceKey();

    final var resultSearchFrom =
        camundaClient
            .newDecisionInstanceSearchRequest()
            .page(p -> p.limit(2).from(2))
            .send()
            .join();

    // then
    assertThat(resultSearchFrom.items().stream().findFirst().get().getDecisionInstanceKey())
        .isEqualTo(thirdKey);
  }

  @Test
  public void shouldRetrieveDecisionInstanceByDecisionDefinitionKey(
      final CamundaClient camundaClient) {
    // when
    final long decisionDefinitionKey =
        EVALUATED_DECISIONS.get(DECISION_DEFINITION_ID_1).getDecisionKey();
    final var result =
        camundaClient
            .newDecisionInstanceSearchRequest()
            .filter(f -> f.decisionDefinitionKey(decisionDefinitionKey))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getDecisionDefinitionKey())
        .isEqualTo(decisionDefinitionKey);
    assertThat(result.items().getFirst().getDecisionInstanceKey())
        .isEqualTo(EVALUATED_DECISIONS.get(DECISION_DEFINITION_ID_1).getDecisionInstanceKey());
  }

  @Test
  public void shouldRetrieveDecisionInstanceByDecisionKeyFilterIn(
      final CamundaClient camundaClient) {
    // when
    final long decisionDefinitionKey =
        EVALUATED_DECISIONS.get(DECISION_DEFINITION_ID_1).getDecisionKey();
    final var result =
        camundaClient
            .newDecisionInstanceSearchRequest()
            .filter(f -> f.decisionDefinitionKey(b -> b.in(Long.MAX_VALUE, decisionDefinitionKey)))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getDecisionDefinitionKey())
        .isEqualTo(decisionDefinitionKey);
    assertThat(result.items().getFirst().getDecisionInstanceKey())
        .isEqualTo(EVALUATED_DECISIONS.get(DECISION_DEFINITION_ID_1).getDecisionInstanceKey());
  }

  @Test
  public void shouldRetrieveDecisionInstanceByDecisionKeyFilterNotIn(
      final CamundaClient camundaClient) {
    // when
    final long decisionDefinitionKey =
        EVALUATED_DECISIONS.get(DECISION_DEFINITION_ID_1).getDecisionKey();
    final var result =
        camundaClient
            .newDecisionInstanceSearchRequest()
            .filter(
                f -> f.decisionDefinitionKey(b -> b.notIn(Long.MAX_VALUE, decisionDefinitionKey)))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(result.items())
        .extracting(DecisionInstance::getDecisionDefinitionId)
        .containsExactlyInAnyOrder(DECISION_DEFINITION_ID_2, DECISION_DEFINITION_ID_3);
  }

  @Test
  public void shouldRetrieveDecisionInstanceByDecisionInstanceKey(
      final CamundaClient camundaClient) {
    // when
    final long decisionInstanceKey =
        EVALUATED_DECISIONS.get(DECISION_DEFINITION_ID_2).getDecisionInstanceKey();
    final var result =
        camundaClient
            .newDecisionInstanceSearchRequest()
            .filter(f -> f.decisionInstanceKey(decisionInstanceKey))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(result.items().getFirst().getDecisionInstanceKey()).isEqualTo(decisionInstanceKey);
    assertThat(result.items().getLast().getDecisionInstanceKey()).isEqualTo(decisionInstanceKey);
  }

  @Test
  public void shouldRetrieveDecisionInstanceByStateAndType() {
    // when
    final DecisionInstanceState state = DecisionInstanceState.EVALUATED;
    final DecisionDefinitionType type = DecisionDefinitionType.DECISION_TABLE;
    final var result =
        camundaClient
            .newDecisionInstanceSearchRequest()
            .filter(f -> f.state(state).decisionDefinitionType(type))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(3);
  }

  @Test
  public void shouldRetrieveDecisionInstanceByEvaluationDate() {
    // given
    final var allResult =
        camundaClient.newDecisionInstanceSearchRequest().page(p -> p.limit(1)).send().join();
    final var di = allResult.items().getFirst();

    // when
    final var result =
        camundaClient
            .newDecisionInstanceSearchRequest()
            .filter(f -> f.evaluationDate(OffsetDateTime.parse(di.getEvaluationDate())))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getDecisionInstanceKey())
        .isEqualTo(di.getDecisionInstanceKey());
  }

  @Test
  public void shouldRetrieveDecisionInstanceByEvaluationDateFilterGt(
      final CamundaClient camundaClient) {
    // given
    final var allResult =
        camundaClient
            .newDecisionInstanceSearchRequest()
            .sort(s -> s.evaluationDate().asc())
            .page(p -> p.limit(1))
            .send()
            .join();
    final var di = allResult.items().getFirst();
    final var requestDate = OffsetDateTime.parse(di.getEvaluationDate());

    // when
    final var result =
        camundaClient
            .newDecisionInstanceSearchRequest()
            .filter(f -> f.evaluationDate(b -> b.gt(requestDate)))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(2);
    assertThat(result.items())
        .extracting("evaluationDate", String.class)
        .allMatch(date -> requestDate.isBefore(OffsetDateTime.parse(date)));
    assertThat(result.items())
        .extracting("decisionInstanceKey", Long.class)
        .noneMatch(key -> di.getDecisionInstanceKey() == key);
  }

  @Test
  public void shouldRetrieveDecisionInstanceByEvaluationDateFilterGte(
      final CamundaClient camundaClient) {
    // given
    final var allResult =
        camundaClient
            .newDecisionInstanceSearchRequest()
            .sort(s -> s.evaluationDate().asc())
            .page(p -> p.limit(1))
            .send()
            .join();
    final var di = allResult.items().getFirst();
    final var requestDate = OffsetDateTime.parse(di.getEvaluationDate());

    // when
    final var result =
        camundaClient
            .newDecisionInstanceSearchRequest()
            .filter(f -> f.evaluationDate(b -> b.gte(requestDate)))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(3);
    assertThat(result.items())
        .extracting("evaluationDate", String.class)
        .allMatch(date -> !OffsetDateTime.parse(date).isBefore(requestDate));
    assertThat(result.items())
        .extracting("decisionInstanceKey", Long.class)
        .anyMatch(key -> di.getDecisionInstanceKey() == key);
  }

  @Test
  public void shouldRetrieveDecisionInstanceByDmnDecisionIdAndDecisionVersion(
      final CamundaClient camundaClient) {
    // when
    final String dmnDecisionId = EVALUATED_DECISIONS.get(DECISION_DEFINITION_ID_1).getDecisionId();
    final int decisionVersion =
        EVALUATED_DECISIONS.get(DECISION_DEFINITION_ID_1).getDecisionVersion();
    final var result =
        camundaClient
            .newDecisionInstanceSearchRequest()
            .filter(
                f ->
                    f.decisionDefinitionId(dmnDecisionId)
                        .decisionDefinitionVersion(decisionVersion))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().get(0).getDecisionInstanceKey())
        .isEqualTo(EVALUATED_DECISIONS.get(DECISION_DEFINITION_ID_1).getDecisionInstanceKey());
  }

  @Test
  void shouldGetDecisionInstance() {
    // when
    final long decisionInstanceKey =
        EVALUATED_DECISIONS.get(DECISION_DEFINITION_ID_2).getDecisionInstanceKey();
    final var decisionInstanceId = "%d-%d".formatted(decisionInstanceKey, 1);
    final var result =
        camundaClient.newDecisionInstanceGetRequest(decisionInstanceId).send().join();

    // then
    assertThat(result.getDecisionInstanceId()).isEqualTo(decisionInstanceId);
    assertThat(result.getDecisionInstanceKey()).isEqualTo(decisionInstanceKey);
  }

  @Test
  void shouldReturn404ForNotFoundDecisionInstance() {
    // when
    final var decisionInstanceId = "not-existing";
    final var problemException =
        assertThrows(
            ProblemException.class,
            () -> camundaClient.newDecisionInstanceGetRequest(decisionInstanceId).send().join());
    // then
    assertThat(problemException.code()).isEqualTo(404);
    assertThat(problemException.details().getDetail())
        .isEqualTo("Decision instance with key %s not found".formatted(decisionInstanceId));
  }

  private static DeploymentEvent deployResource(
      final CamundaClient camundaClient, final String resourceName) {
    return camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(resourceName)
        .send()
        .join();
  }

  private static EvaluateDecisionResponse evaluateDecision(
      final CamundaClient camundaClient,
      final String decisionDefinitionId,
      final String variables) {
    return camundaClient
        .newEvaluateDecisionCommand()
        .decisionId(decisionDefinitionId)
        .variables(variables)
        .send()
        .join();
  }

  private static void waitForDecisionsToBeEvaluated(
      final CamundaClient camundaClient, final int expectedCount) {
    Awaitility.await("should deploy decision definitions and import in Operate")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = camundaClient.newDecisionInstanceSearchRequest().send().join();
              assertThat(result.items().size()).isEqualTo(expectedCount);
            });
  }
}
