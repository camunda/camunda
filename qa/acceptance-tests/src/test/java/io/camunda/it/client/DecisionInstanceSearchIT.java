/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForElementInstances;
import static io.camunda.it.util.TestHelper.waitUntilProcessInstanceIsEnded;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static io.camunda.search.exception.ErrorMessages.ERROR_ENTITY_BY_ID_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.EvaluateDecisionResponse;
import io.camunda.client.api.search.response.DecisionDefinitionType;
import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.client.api.search.response.DecisionInstanceState;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest
class DecisionInstanceSearchIT {

  private static final String DECISION_DEFINITION_ID_1 = "decision_1";
  private static final String DECISION_DEFINITION_ID_2 = "invoiceAssignApprover";
  private static final String DECISION_DEFINITION_ID_3 = "invoiceClassification";
  private static final String PROCESS_DEFINITION_ID = "myProcessWithDMN";
  private static final String ELEMENT_ID_DMN_CALL = "dmnCall";

  private static CamundaClient camundaClient;
  private static long processInstanceKey;
  // evaluated decisions mapped by decision definition id
  private static final Map<String, EvaluateDecisionResponse> EVALUATED_DECISIONS = new HashMap<>();

  @BeforeAll
  static void setUp() {

    List.of("decision_model.dmn", "invoiceBusinessDecisions_v_1.dmn")
        .forEach(
            dmn ->
                deployResource(camundaClient, String.format("decisions/%s", dmn)).getDecisions());
    // creates one decision instance
    EVALUATED_DECISIONS.put(
        DECISION_DEFINITION_ID_1,
        evaluateDecision(
            camundaClient, DECISION_DEFINITION_ID_1, "{\"age\": 20, \"income\": 20000}"));
    // creates two decision instances
    EVALUATED_DECISIONS.put(
        DECISION_DEFINITION_ID_2,
        evaluateDecision(
            camundaClient,
            DECISION_DEFINITION_ID_2,
            "{\"amount\": 100, \"invoiceCategory\": \"Misc\"}"));

    deployResource(
        camundaClient,
        Bpmn.createExecutableProcess(PROCESS_DEFINITION_ID)
            .startEvent()
            .businessRuleTask(
                ELEMENT_ID_DMN_CALL,
                b ->
                    b.zeebeCalledDecisionId(DECISION_DEFINITION_ID_2).zeebeResultVariable("foobar"))
            .endEvent()
            .done(),
        "myProcessWithDMN.bpmn");
    processInstanceKey =
        // creates two decision instances
        startProcessInstance(
                camundaClient,
                PROCESS_DEFINITION_ID,
                "{\"amount\": 100, \"invoiceCategory\": \"Misc\"}")
            .getProcessInstanceKey();

    waitUntilProcessInstanceIsEnded(camundaClient, processInstanceKey);
    waitForDecisionsToBeEvaluated(camundaClient, 5);
    waitForElementInstances(camundaClient, f -> f.processInstanceKey(processInstanceKey), 3);
  }

  @Test
  public void shouldRetrieveAllDecisionInstances() {
    // when
    final var result = camundaClient.newDecisionInstanceSearchRequest().send().join();

    // then
    assertThat(result.items().size()).isEqualTo(5);
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
  void shouldSearchByLimit() {
    // when
    final var resultAll = camundaClient.newDecisionInstanceSearchRequest().send().join();

    final var resultWithLimit =
        camundaClient.newDecisionInstanceSearchRequest().page(p -> p.limit(2)).send().join();
    assertThat(resultWithLimit.items().size()).isEqualTo(2);

    final var firstTwoKeys =
        resultAll.items().subList(0, 2).stream()
            .map(DecisionInstance::getDecisionInstanceKey)
            .toList();

    final var resultSearchFrom =
        camundaClient.newDecisionInstanceSearchRequest().page(p -> p.limit(2)).send().join();

    // then
    assertThat(
            resultSearchFrom.items().stream()
                .map(DecisionInstance::getDecisionInstanceKey)
                .toList())
        .isEqualTo(firstTwoKeys);
  }

  @Test
  public void shouldRetrieveDecisionInstanceByDecisionDefinitionKey(
      final CamundaClient camundaClient) {
    // when
    final long decisionDefinitionKey =
        EVALUATED_DECISIONS
            .get(DECISION_DEFINITION_ID_1)
            .getEvaluatedDecisions()
            .getFirst()
            .getDecisionKey();
    final long decisionInstanceKey =
        EVALUATED_DECISIONS.get(DECISION_DEFINITION_ID_1).getDecisionEvaluationKey();
    final String decisionInstanceId = "%d-%d".formatted(decisionInstanceKey, 1);
    final var result =
        camundaClient
            .newDecisionInstanceSearchRequest()
            .filter(f -> f.decisionDefinitionKey(decisionDefinitionKey))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    final var decisionInstance = result.singleItem();

    assertThat(decisionInstance.getDecisionInstanceKey()).isEqualTo(decisionInstanceKey);
    assertThat(decisionInstance.getDecisionInstanceId()).isEqualTo(decisionInstanceId);
    assertThat(decisionInstance.getState()).isEqualTo(DecisionInstanceState.EVALUATED);
    assertThat(decisionInstance.getEvaluationFailure()).isNull();
    assertThat(decisionInstance.getProcessDefinitionKey()).isEqualTo(-1L);
    assertThat(decisionInstance.getProcessInstanceKey()).isEqualTo(-1L);
    assertThat(decisionInstance.getElementInstanceKey()).isEqualTo(-1L);
    assertThat(decisionInstance.getDecisionDefinitionId()).isEqualTo("decision_1");
    assertThat(decisionInstance.getDecisionDefinitionName()).isEqualTo("Loan Eligibility");
    assertThat(decisionInstance.getDecisionDefinitionVersion()).isEqualTo(1);
    assertThat(decisionInstance.getDecisionDefinitionType())
        .isEqualTo(DecisionDefinitionType.DECISION_TABLE);
    assertThat(decisionInstance.getTenantId()).isEqualTo("<default>");
    assertThat(decisionInstance.getDecisionDefinitionKey()).isEqualTo(decisionDefinitionKey);
    assertThat(decisionInstance.getRootDecisionDefinitionKey()).isEqualTo(decisionDefinitionKey);
    assertThat(decisionInstance.getResult()).isEqualTo("\"Eligible\"");
    assertThat(decisionInstance.getEvaluationDate()).isNotNull();
    // evaluated inputs and matched rules are not included in search results, only in get by id
    assertThat(decisionInstance.getEvaluatedInputs()).isNull();
    assertThat(decisionInstance.getMatchedRules()).isNull();
  }

  @Test
  public void shouldRetrieveDecisionInstanceByDecisionId(final CamundaClient camundaClient) {
    // when
    final long decisionDefinitionKey =
        EVALUATED_DECISIONS
            .get(DECISION_DEFINITION_ID_1)
            .getEvaluatedDecisions()
            .getFirst()
            .getDecisionKey();
    final long decisionInstanceKey =
        EVALUATED_DECISIONS.get(DECISION_DEFINITION_ID_1).getDecisionEvaluationKey();
    final String decisionInstanceId = "%d-%d".formatted(decisionInstanceKey, 1);
    final var result =
        camundaClient
            .newDecisionInstanceSearchRequest()
            .filter(f -> f.decisionInstanceId(decisionInstanceId))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    final var decisionInstance = result.singleItem();

    assertThat(decisionInstance.getDecisionInstanceKey()).isEqualTo(decisionInstanceKey);
    assertThat(decisionInstance.getDecisionInstanceId()).isEqualTo(decisionInstanceId);
    assertThat(decisionInstance.getState()).isEqualTo(DecisionInstanceState.EVALUATED);
    assertThat(decisionInstance.getEvaluationFailure()).isNull();
    assertThat(decisionInstance.getProcessDefinitionKey()).isEqualTo(-1L);
    assertThat(decisionInstance.getProcessInstanceKey()).isEqualTo(-1L);
    assertThat(decisionInstance.getElementInstanceKey()).isEqualTo(-1L);
    assertThat(decisionInstance.getDecisionDefinitionId()).isEqualTo("decision_1");
    assertThat(decisionInstance.getDecisionDefinitionName()).isEqualTo("Loan Eligibility");
    assertThat(decisionInstance.getDecisionDefinitionVersion()).isEqualTo(1);
    assertThat(decisionInstance.getDecisionDefinitionType())
        .isEqualTo(DecisionDefinitionType.DECISION_TABLE);
    assertThat(decisionInstance.getTenantId()).isEqualTo("<default>");
    assertThat(decisionInstance.getDecisionDefinitionKey()).isEqualTo(decisionDefinitionKey);
    assertThat(decisionInstance.getRootDecisionDefinitionKey()).isEqualTo(decisionDefinitionKey);
    assertThat(decisionInstance.getResult()).isEqualTo("\"Eligible\"");
    assertThat(decisionInstance.getEvaluationDate()).isNotNull();
    // evaluated inputs and matched rules are not included in search results, only in get by id
    assertThat(decisionInstance.getEvaluatedInputs()).isNull();
    assertThat(decisionInstance.getMatchedRules()).isNull();
  }

  @Test
  public void shouldRetrieveDecisionInstanceByDecisionIdIn(final CamundaClient camundaClient) {
    // when
    final long decisionDefinitionKey =
        EVALUATED_DECISIONS
            .get(DECISION_DEFINITION_ID_1)
            .getEvaluatedDecisions()
            .getFirst()
            .getDecisionKey();
    final long decisionInstanceKey =
        EVALUATED_DECISIONS.get(DECISION_DEFINITION_ID_1).getDecisionEvaluationKey();
    final String decisionInstanceId = "%d-%d".formatted(decisionInstanceKey, 1);
    final var result =
        camundaClient
            .newDecisionInstanceSearchRequest()
            .filter(f -> f.decisionInstanceId(d -> d.in(decisionInstanceId, "foobar")))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    final var decisionInstance = result.singleItem();

    assertThat(decisionInstance.getDecisionInstanceKey()).isEqualTo(decisionInstanceKey);
    assertThat(decisionInstance.getDecisionInstanceId()).isEqualTo(decisionInstanceId);
    assertThat(decisionInstance.getState()).isEqualTo(DecisionInstanceState.EVALUATED);
    assertThat(decisionInstance.getEvaluationFailure()).isNull();
    assertThat(decisionInstance.getProcessDefinitionKey()).isEqualTo(-1L);
    assertThat(decisionInstance.getProcessInstanceKey()).isEqualTo(-1L);
    assertThat(decisionInstance.getElementInstanceKey()).isEqualTo(-1L);
    assertThat(decisionInstance.getDecisionDefinitionId()).isEqualTo("decision_1");
    assertThat(decisionInstance.getDecisionDefinitionName()).isEqualTo("Loan Eligibility");
    assertThat(decisionInstance.getDecisionDefinitionVersion()).isEqualTo(1);
    assertThat(decisionInstance.getDecisionDefinitionType())
        .isEqualTo(DecisionDefinitionType.DECISION_TABLE);
    assertThat(decisionInstance.getTenantId()).isEqualTo("<default>");
    assertThat(decisionInstance.getDecisionDefinitionKey()).isEqualTo(decisionDefinitionKey);
    assertThat(decisionInstance.getRootDecisionDefinitionKey()).isEqualTo(decisionDefinitionKey);
    assertThat(decisionInstance.getResult()).isEqualTo("\"Eligible\"");
    assertThat(decisionInstance.getEvaluationDate()).isNotNull();
    // evaluated inputs and matched rules are not included in search results, only in get by id
    assertThat(decisionInstance.getEvaluatedInputs()).isNull();
    assertThat(decisionInstance.getMatchedRules()).isNull();
  }

  @Test
  public void shouldRetrieveDecisionInstanceByDecisionIdNotIn(final CamundaClient camundaClient) {
    // when
    final long decisionDefinitionKey =
        EVALUATED_DECISIONS
            .get(DECISION_DEFINITION_ID_1)
            .getEvaluatedDecisions()
            .getFirst()
            .getDecisionKey();
    final long decisionInstanceKey =
        EVALUATED_DECISIONS.get(DECISION_DEFINITION_ID_1).getDecisionEvaluationKey();
    final String decisionInstanceId = "%d-%d".formatted(decisionInstanceKey, 1);
    final var result =
        camundaClient
            .newDecisionInstanceSearchRequest()
            .filter(f -> f.decisionInstanceId(d -> d.notIn(decisionInstanceId, "foobar")))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(4);
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
        .isEqualTo(EVALUATED_DECISIONS.get(DECISION_DEFINITION_ID_1).getDecisionEvaluationKey());
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
    assertThat(result.items().size()).isEqualTo(4);
    assertThat(result.items())
        .extracting(DecisionInstance::getDecisionDefinitionId)
        .containsExactlyInAnyOrder(
            DECISION_DEFINITION_ID_2,
            DECISION_DEFINITION_ID_2,
            DECISION_DEFINITION_ID_3,
            DECISION_DEFINITION_ID_3);
  }

  @Test
  public void shouldRetrieveDecisionInstanceByRootDecisionDefinitionKey(
      final CamundaClient camundaClient) {
    // given
    final long rootDecisionDefinitionKey =
        EVALUATED_DECISIONS
            .get(DECISION_DEFINITION_ID_1)
            .getEvaluatedDecisions()
            .getFirst()
            .getDecisionKey();
    final long decisionInstanceKey =
        EVALUATED_DECISIONS.get(DECISION_DEFINITION_ID_1).getDecisionEvaluationKey();
    final String decisionInstanceId = "%d-%d".formatted(decisionInstanceKey, 1);
    // when
    final var result =
        camundaClient
            .newDecisionInstanceSearchRequest()
            .filter(f -> f.rootDecisionDefinitionKey(rootDecisionDefinitionKey))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    final var decisionInstance = result.singleItem();

    assertThat(decisionInstance.getDecisionInstanceKey()).isEqualTo(decisionInstanceKey);
    assertThat(decisionInstance.getDecisionInstanceId()).isEqualTo(decisionInstanceId);
    assertThat(decisionInstance.getState()).isEqualTo(DecisionInstanceState.EVALUATED);
    assertThat(decisionInstance.getEvaluationFailure()).isNull();
    assertThat(decisionInstance.getProcessDefinitionKey()).isEqualTo(-1L);
    assertThat(decisionInstance.getProcessInstanceKey()).isEqualTo(-1L);
    assertThat(decisionInstance.getElementInstanceKey()).isEqualTo(-1L);
    assertThat(decisionInstance.getDecisionDefinitionId()).isEqualTo("decision_1");
    assertThat(decisionInstance.getDecisionDefinitionName()).isEqualTo("Loan Eligibility");
    assertThat(decisionInstance.getDecisionDefinitionVersion()).isEqualTo(1);
    assertThat(decisionInstance.getDecisionDefinitionType())
        .isEqualTo(DecisionDefinitionType.DECISION_TABLE);
    assertThat(decisionInstance.getTenantId()).isEqualTo("<default>");
    assertThat(decisionInstance.getDecisionDefinitionKey()).isEqualTo(rootDecisionDefinitionKey);
    assertThat(decisionInstance.getRootDecisionDefinitionKey())
        .isEqualTo(rootDecisionDefinitionKey);
    assertThat(decisionInstance.getResult()).isEqualTo("\"Eligible\"");
    assertThat(decisionInstance.getEvaluationDate()).isNotNull();
    // evaluated inputs and matched rules are not included in search results, only in get by id
    assertThat(decisionInstance.getEvaluatedInputs()).isNull();
    assertThat(decisionInstance.getMatchedRules()).isNull();
  }

  @Test
  public void shouldRetrieveDecisionInstanceByRootDecisionDefinitionKeyFilterIn(
      final CamundaClient camundaClient) {
    // when
    final long rootDecisionDefinitionKey =
        EVALUATED_DECISIONS.get(DECISION_DEFINITION_ID_1).getDecisionKey();
    final var result =
        camundaClient
            .newDecisionInstanceSearchRequest()
            .filter(
                f ->
                    f.rootDecisionDefinitionKey(
                        b -> b.in(Long.MAX_VALUE, rootDecisionDefinitionKey)))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getRootDecisionDefinitionKey())
        .isEqualTo(rootDecisionDefinitionKey);
    assertThat(result.items().getFirst().getDecisionInstanceKey())
        .isEqualTo(EVALUATED_DECISIONS.get(DECISION_DEFINITION_ID_1).getDecisionEvaluationKey());
  }

  @Test
  public void shouldRetrieveDecisionInstanceByRootDecisionDefinitionKeyFilterNotIn(
      final CamundaClient camundaClient) {
    // when
    final long rootDecisionDefinitionKey =
        EVALUATED_DECISIONS.get(DECISION_DEFINITION_ID_1).getDecisionKey();
    final var result =
        camundaClient
            .newDecisionInstanceSearchRequest()
            .filter(
                f ->
                    f.rootDecisionDefinitionKey(
                        b -> b.notIn(Long.MAX_VALUE, rootDecisionDefinitionKey)))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(4);
    assertThat(result.items())
        .extracting(DecisionInstance::getDecisionDefinitionId)
        .containsExactlyInAnyOrder(
            DECISION_DEFINITION_ID_2,
            DECISION_DEFINITION_ID_2,
            DECISION_DEFINITION_ID_3,
            DECISION_DEFINITION_ID_3);
  }

  @Test
  public void shouldRetrieveDecisionInstanceByDecisionInstanceKey(
      final CamundaClient camundaClient) {
    // when
    final long decisionInstanceKey =
        EVALUATED_DECISIONS.get(DECISION_DEFINITION_ID_2).getDecisionEvaluationKey();
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
  public void shouldRetrieveDecisionInstanceByElementInstanceKeyFilterIn(
      final CamundaClient camundaClient) {
    // given
    final long elementInstanceKey =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.processInstanceKey(processInstanceKey).elementId(ELEMENT_ID_DMN_CALL))
            .send()
            .join()
            .singleItem()
            .getElementInstanceKey();
    // when
    final var result =
        camundaClient
            .newDecisionInstanceSearchRequest()
            .filter(f -> f.elementInstanceKey(b -> b.in(Long.MAX_VALUE, elementInstanceKey)))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(result.items())
        .extracting("elementInstanceKey", "decisionDefinitionId")
        .containsExactlyInAnyOrder(
            tuple(elementInstanceKey, DECISION_DEFINITION_ID_2),
            tuple(elementInstanceKey, DECISION_DEFINITION_ID_3));
  }

  @Test
  public void shouldRetrieveDecisionInstanceByProcessInstanceKeyFilter(
      final CamundaClient camundaClient) {
    // when
    final var result =
        camundaClient
            .newDecisionInstanceSearchRequest()
            .filter(f -> f.processInstanceKey(processInstanceKey))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(result.items())
        .extracting("processInstanceKey", "rootProcessInstanceKey", "decisionDefinitionId")
        .containsExactlyInAnyOrder(
            tuple(processInstanceKey, processInstanceKey, DECISION_DEFINITION_ID_2),
            tuple(processInstanceKey, processInstanceKey, DECISION_DEFINITION_ID_3));
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
    assertThat(result.items().size()).isEqualTo(5);
  }

  @Test
  public void shouldRetrieveDecisionInstanceByStateIn() {
    // when
    final DecisionInstanceState state = DecisionInstanceState.EVALUATED;
    final var result =
        camundaClient
            .newDecisionInstanceSearchRequest()
            .filter(f -> f.state(s -> s.in(state)))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(5);
  }

  @Test
  public void shouldRetrieveDecisionInstanceByStateNotIn() {
    // when
    final DecisionInstanceState state = DecisionInstanceState.FAILED;
    final var result =
        camundaClient
            .newDecisionInstanceSearchRequest()
            .filter(f -> f.state(s -> s.notIn(state)))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(5);
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
            .filter(f -> f.evaluationDate(di.getEvaluationDate()))
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
    final var requestDate = di.getEvaluationDate();

    // when
    final var result =
        camundaClient
            .newDecisionInstanceSearchRequest()
            .filter(f -> f.evaluationDate(b -> b.gt(requestDate)))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(4);
    assertThat(result.items())
        .extracting("evaluationDate", OffsetDateTime.class)
        .allMatch(requestDate::isBefore);
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
    final var requestDate = di.getEvaluationDate();

    // when
    final var result =
        camundaClient
            .newDecisionInstanceSearchRequest()
            .filter(f -> f.evaluationDate(b -> b.gte(requestDate)))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(5);
    assertThat(result.items())
        .extracting("evaluationDate", OffsetDateTime.class)
        .allMatch(date -> !date.isBefore(requestDate));
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
    assertThat(result.singleItem().getDecisionInstanceKey())
        .isEqualTo(EVALUATED_DECISIONS.get(DECISION_DEFINITION_ID_1).getDecisionEvaluationKey());
  }

  @Test
  void shouldGetDecisionInstance() {
    // when
    final long decisionInstanceKey =
        EVALUATED_DECISIONS.get(DECISION_DEFINITION_ID_2).getDecisionEvaluationKey();
    final var decisionInstanceId = "%d-%d".formatted(decisionInstanceKey, 1);
    final long decisionDefinitionKey =
        EVALUATED_DECISIONS
            .get(DECISION_DEFINITION_ID_2)
            .getEvaluatedDecisions()
            .getFirst()
            .getDecisionKey();
    final var result =
        camundaClient.newDecisionInstanceGetRequest(decisionInstanceId).send().join();

    // then
    assertThat(result.getDecisionInstanceKey()).isEqualTo(decisionInstanceKey);
    assertThat(result.getDecisionInstanceId()).isEqualTo(decisionInstanceId);
    assertThat(result.getState()).isEqualTo(DecisionInstanceState.EVALUATED);
    assertThat(result.getEvaluationFailure()).isNull();
    assertThat(result.getProcessDefinitionKey()).isEqualTo(-1L);
    assertThat(result.getProcessInstanceKey()).isEqualTo(-1L);
    assertThat(result.getElementInstanceKey()).isEqualTo(-1L);
    assertThat(result.getDecisionDefinitionId()).isEqualTo("invoiceClassification");
    assertThat(result.getDecisionDefinitionName()).isEqualTo("Invoice Classification");
    assertThat(result.getDecisionDefinitionVersion()).isEqualTo(1);
    assertThat(result.getDecisionDefinitionType()).isEqualTo(DecisionDefinitionType.DECISION_TABLE);
    assertThat(result.getTenantId()).isEqualTo("<default>");
    assertThat(result.getDecisionDefinitionKey()).isEqualTo(decisionDefinitionKey);
    assertThat(result.getResult()).isEqualTo("\"day-to-day expense\"");
    assertThat(result.getEvaluationDate()).isNotNull();

    // Assert evaluated inputs
    assertThat(result.getEvaluatedInputs()).hasSize(2);
    assertThat(result.getEvaluatedInputs().getFirst().getInputId()).isEqualTo("clause1");
    assertThat(result.getEvaluatedInputs().getFirst().getInputName()).isEqualTo("Invoice Amount");
    assertThat(result.getEvaluatedInputs().getFirst().getInputValue()).isEqualTo("100");
    assertThat(result.getEvaluatedInputs().get(1).getInputId()).isEqualTo("InputClause_15qmk0v");
    assertThat(result.getEvaluatedInputs().get(1).getInputName()).isEqualTo("Invoice Category");
    assertThat(result.getEvaluatedInputs().get(1).getInputValue()).isEqualTo("\"Misc\"");

    // Assert matched rules
    assertThat(result.getMatchedRules()).hasSize(1);
    assertThat(result.getMatchedRules().getFirst().getRuleId()).isEqualTo("DecisionRule_1of5a87");
    assertThat(result.getMatchedRules().getFirst().getRuleIndex()).isEqualTo(1);
    assertThat(result.getMatchedRules().getFirst().getEvaluatedOutputs()).hasSize(1);
    assertThat(result.getMatchedRules().getFirst().getEvaluatedOutputs().getFirst().getOutputId())
        .isEqualTo("clause3");
    assertThat(result.getMatchedRules().getFirst().getEvaluatedOutputs().getFirst().getOutputName())
        .isEqualTo("Classification");
    assertThat(
            result.getMatchedRules().getFirst().getEvaluatedOutputs().getFirst().getOutputValue())
        .isEqualTo("\"day-to-day expense\"");
  }

  @Test
  void shouldReturnNotFoundOnGetWhenIdDoesNotExist() {
    // when / then
    assertThatThrownBy(
            () -> camundaClient.newDecisionInstanceGetRequest("someDecisionInstance").send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            ERROR_ENTITY_BY_ID_NOT_FOUND.formatted(
                "Decision Instance", "id", "someDecisionInstance"));
  }

  @Test
  void shouldReturn404ForNotFoundDecisionInstance() {
    // when
    final var decisionInstanceId = "not-existing";
    final var problemException =
        assertThatExceptionOfType(ProblemException.class)
            .isThrownBy(
                () -> camundaClient.newDecisionInstanceGetRequest(decisionInstanceId).send().join())
            .actual();
    // then
    assertThat(problemException.code()).isEqualTo(404);
    assertThat(problemException.details().getDetail())
        .contains("Decision Instance with id '%s' not found".formatted(decisionInstanceId));
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
