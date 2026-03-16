/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.decisioninstance;

import static io.camunda.it.rdbms.db.fixtures.DecisionInstanceFixtures.createAndSaveDecisionInstance;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.DecisionInstanceDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel;
import io.camunda.it.rdbms.db.fixtures.CommonFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceState;
import io.camunda.search.query.DecisionInstanceQuery;
import java.time.OffsetDateTime;
import java.util.Collections;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Integration test for standalone decision instances (evaluated without a process context).
 * These instances have processDefinitionKey=0 and processInstanceKey=0.
 *
 * <p>This test verifies that the system correctly handles standalone decisions across all
 * supported database layers (Elasticsearch and RDBMS), ensuring no ClassCastException occurs
 * when numeric zero values are deserialized.
 */
@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class StandaloneDecisionInstanceIT {

  public static final int PARTITION_ID = 0;

  @TestTemplate
  public void shouldSaveAndFindStandaloneDecisionInstanceById(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DecisionInstanceDbReader decisionInstanceReader =
        rdbmsService.getDecisionInstanceReader();

    // Create a standalone decision instance with zero keys
    final var standaloneDecision =
        new DecisionInstanceDbModel.Builder()
            .decisionInstanceId("standalone-1")
            .decisionInstanceKey(123456789L)
            .processInstanceKey(0L) // Zero indicates standalone evaluation
            .rootProcessInstanceKey(0L)
            .processDefinitionKey(0L) // Zero indicates standalone evaluation
            .processDefinitionId("")
            .flowNodeInstanceKey(0L)
            .flowNodeId("")
            .decisionRequirementsKey(987654321L)
            .decisionRequirementsId("drd-1")
            .decisionDefinitionKey(111111111L)
            .decisionDefinitionId("standalone-decision-1")
            .rootDecisionDefinitionKey(111111111L)
            .evaluationDate(OffsetDateTime.now())
            .result("\"approved\"")
            .evaluationFailure(null)
            .evaluationFailureMessage(null)
            .decisionType(DecisionDefinitionType.DECISION_TABLE)
            .tenantId(CommonFixtures.DEFAULT_TENANT_ID)
            .state(DecisionInstanceState.EVALUATED)
            .evaluatedInputs(Collections.emptyList())
            .evaluatedOutputs(Collections.emptyList())
            .build();

    createAndSaveDecisionInstance(rdbmsWriters, standaloneDecision);

    // When finding the standalone decision by ID
    final var actual =
        decisionInstanceReader.findOne(standaloneDecision.decisionInstanceId()).orElseThrow();

    // Then it should be retrieved successfully with zero keys
    assertThat(actual).isNotNull();
    assertThat(actual.decisionInstanceId()).isEqualTo(standaloneDecision.decisionInstanceId());
    assertThat(actual.decisionInstanceKey()).isEqualTo(standaloneDecision.decisionInstanceKey());

    // Verify zero keys are correctly stored and retrieved
    assertThat(actual.processDefinitionKey())
        .isEqualTo(0L)
        .describedAs("processDefinitionKey should be 0 for standalone decisions");
    assertThat(actual.processInstanceKey())
        .isEqualTo(0L)
        .describedAs("processInstanceKey should be 0 for standalone decisions");
    assertThat(actual.rootProcessInstanceKey())
        .isEqualTo(0L)
        .describedAs("rootProcessInstanceKey should be 0 for standalone decisions");

    assertThat(actual.state()).isEqualTo(DecisionInstanceState.EVALUATED);
  }

  @TestTemplate
  public void shouldSearchStandaloneDecisionInstances(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DecisionInstanceDbReader decisionInstanceReader =
        rdbmsService.getDecisionInstanceReader();

    // Create multiple standalone decision instances
    final var standalone1 =
        new DecisionInstanceDbModel.Builder()
            .decisionInstanceId("standalone-search-1")
            .decisionInstanceKey(223456789L)
            .processInstanceKey(0L)
            .rootProcessInstanceKey(0L)
            .processDefinitionKey(0L)
            .processDefinitionId("")
            .flowNodeInstanceKey(0L)
            .flowNodeId("")
            .decisionRequirementsKey(987654321L)
            .decisionRequirementsId("drd-2")
            .decisionDefinitionKey(222222222L)
            .decisionDefinitionId("standalone-decision-2")
            .rootDecisionDefinitionKey(222222222L)
            .evaluationDate(OffsetDateTime.now())
            .result("\"result-1\"")
            .decisionType(DecisionDefinitionType.DECISION_TABLE)
            .tenantId(CommonFixtures.DEFAULT_TENANT_ID)
            .state(DecisionInstanceState.EVALUATED)
            .evaluatedInputs(Collections.emptyList())
            .evaluatedOutputs(Collections.emptyList())
            .build();

    final var standalone2 =
        new DecisionInstanceDbModel.Builder()
            .decisionInstanceId("standalone-search-2")
            .decisionInstanceKey(323456789L)
            .processInstanceKey(0L)
            .rootProcessInstanceKey(0L)
            .processDefinitionKey(0L)
            .processDefinitionId("")
            .flowNodeInstanceKey(0L)
            .flowNodeId("")
            .decisionRequirementsKey(987654321L)
            .decisionRequirementsId("drd-2")
            .decisionDefinitionKey(222222222L)
            .decisionDefinitionId("standalone-decision-2")
            .rootDecisionDefinitionKey(222222222L)
            .evaluationDate(OffsetDateTime.now().plusSeconds(1))
            .result("\"result-2\"")
            .decisionType(DecisionDefinitionType.DECISION_TABLE)
            .tenantId(CommonFixtures.DEFAULT_TENANT_ID)
            .state(DecisionInstanceState.EVALUATED)
            .evaluatedInputs(Collections.emptyList())
            .evaluatedOutputs(Collections.emptyList())
            .build();

    createAndSaveDecisionInstance(rdbmsWriters, standalone1);
    createAndSaveDecisionInstance(rdbmsWriters, standalone2);

    // When searching for standalone decisions by processDefinitionKey=0
    final var searchResult =
        decisionInstanceReader.search(
            DecisionInstanceQuery.of(
                b -> b.filter(f -> f.processDefinitionKeys(0L)).page(p -> p.size(10))));

    // Then standalone decisions should be found
    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isGreaterThanOrEqualTo(2);
    assertThat(searchResult.items()).isNotEmpty();

    // Verify all returned items have zero process keys
    assertThat(searchResult.items())
        .allMatch(item -> item.processDefinitionKey() == 0L)
        .allMatch(item -> item.processInstanceKey() == 0L);
  }

  @TestTemplate
  public void shouldHandleStandaloneDecisionWithSmallNumericValues(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DecisionInstanceDbReader decisionInstanceReader =
        rdbmsService.getDecisionInstanceReader();

    // Create a standalone decision with decisionVersion=1 (small integer)
    // This tests that small integers are correctly handled alongside zero Long values
    final var standaloneDecision =
        new DecisionInstanceDbModel.Builder()
            .decisionInstanceId("standalone-small-num-1")
            .decisionInstanceKey(423456789L)
            .processInstanceKey(0L)
            .rootProcessInstanceKey(0L)
            .processDefinitionKey(0L)
            .processDefinitionId("")
            .flowNodeInstanceKey(0L)
            .flowNodeId("")
            .decisionRequirementsKey(987654321L)
            .decisionRequirementsId("drd-3")
            .decisionDefinitionKey(333333333L)
            .decisionDefinitionId("standalone-decision-3")
            .rootDecisionDefinitionKey(333333333L)
            .evaluationDate(OffsetDateTime.now())
            .result("\"small-num-result\"")
            .decisionType(DecisionDefinitionType.DECISION_TABLE)
            .tenantId(CommonFixtures.DEFAULT_TENANT_ID)
            .state(DecisionInstanceState.EVALUATED)
            .evaluatedInputs(Collections.emptyList())
            .evaluatedOutputs(Collections.emptyList())
            .build();

    createAndSaveDecisionInstance(rdbmsWriters, standaloneDecision);

    // When retrieving the decision
    final var actual =
        decisionInstanceReader.findOne(standaloneDecision.decisionInstanceId()).orElseThrow();

    // Then both zero Long values and small integer should be correctly deserialized
    assertThat(actual.processDefinitionKey()).isEqualTo(0L);
    assertThat(actual.processInstanceKey()).isEqualTo(0L);
    assertThat(actual.decisionInstanceKey()).isEqualTo(423456789L);
  }
}
