/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao;

import static io.camunda.webapps.schema.entities.AbstractExporterEntity.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.DecisionInstance;
import io.camunda.operate.webapp.api.v1.entities.DecisionInstanceState;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity;
import io.camunda.webapps.schema.entities.dmn.DecisionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration test for standalone decision instances (evaluated without a process context).
 * These instances have processDefinitionKey=0 and processInstanceKey=0, which can trigger
 * ClassCastException if Jackson deserializes them as Integer instead of Long.
 */
public class StandaloneDecisionInstanceDaoIT extends OperateSearchAbstractIT {
  private final String evaluationDate = "2024-02-15T22:40:10.834+0000";
  private static final String DECISION_RESULT = "\"standalone result\"";

  @Autowired private DecisionInstanceDao dao;
  @Autowired private DecisionInstanceTemplate decisionInstanceIndex;
  @Autowired private OperateDateTimeFormatter dateTimeFormatter;

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    final String indexName = decisionInstanceIndex.getFullQualifiedName();

    // Create a standalone decision instance with processDefinitionKey=0 and processInstanceKey=0
    // This simulates decisions evaluated via the standalone Evaluate Decision API
    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new DecisionInstanceEntity()
            .setId("9999999999999999-1")
            .setKey(9999999999999999L)
            .setState(io.camunda.webapps.schema.entities.dmn.DecisionInstanceState.EVALUATED)
            .setEvaluationDate(dateTimeFormatter.parseGeneralDateTime(evaluationDate))
            .setProcessDefinitionKey(0L)  // Zero for standalone decisions
            .setProcessInstanceKey(0L)    // Zero for standalone decisions
            .setDecisionId("standaloneDecision")
            .setDecisionDefinitionId("9999999999999998")
            .setDecisionName("Standalone Decision")
            .setDecisionVersion(1)
            .setDecisionType(DecisionType.DECISION_TABLE)
            .setResult(DECISION_RESULT)
            .setTenantId(DEFAULT_TENANT_ID));

    // Create another standalone decision instance with explicit zero values
    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new DecisionInstanceEntity()
            .setId("9999999999999997-1")
            .setKey(9999999999999997L)
            .setState(io.camunda.webapps.schema.entities.dmn.DecisionInstanceState.EVALUATED)
            .setEvaluationDate(dateTimeFormatter.parseGeneralDateTime(evaluationDate))
            .setProcessDefinitionKey(0L)
            .setProcessInstanceKey(0L)
            .setDecisionId("anotherStandaloneDecision")
            .setDecisionDefinitionId("9999999999999996")
            .setDecisionName("Another Standalone Decision")
            .setDecisionVersion(2)
            .setDecisionType(DecisionType.DECISION_TABLE)
            .setResult(DECISION_RESULT)
            .setTenantId(DEFAULT_TENANT_ID));

    searchContainerManager.refreshIndices("*operate-decision*");
  }

  @Test
  public void shouldSearchStandaloneDecisionInstances() {
    // When searching for all decision instances
    final Results<DecisionInstance> results = dao.search(new Query<>());

    // Then standalone decisions should be returned without ClassCastException
    assertThat(results.getTotal()).isGreaterThanOrEqualTo(2);
    assertThat(results.getItems()).isNotEmpty();

    // Find our standalone decision instances
    final var standaloneDecisions = results.getItems().stream()
        .filter(di -> "standaloneDecision".equals(di.getDecisionId())
                   || "anotherStandaloneDecision".equals(di.getDecisionId()))
        .toList();

    assertThat(standaloneDecisions).hasSize(2);

    // Verify that zero values are correctly deserialized as Long, not Integer
    for (final DecisionInstance decision : standaloneDecisions) {
      assertThat(decision.getProcessDefinitionKey())
          .isNotNull()
          .isEqualTo(0L)
          .isInstanceOf(Long.class);
      assertThat(decision.getProcessInstanceKey())
          .isNotNull()
          .isEqualTo(0L)
          .isInstanceOf(Long.class);
      assertThat(decision.getState()).isEqualTo(DecisionInstanceState.EVALUATED);
    }
  }

  @Test
  public void shouldFindStandaloneDecisionInstanceById() {
    // When fetching a standalone decision instance by ID
    final DecisionInstance decision = dao.byId("9999999999999999-1");

    // Then it should be retrieved successfully without ClassCastException
    assertThat(decision).isNotNull();
    assertThat(decision.getDecisionId()).isEqualTo("standaloneDecision");
    assertThat(decision.getDecisionName()).isEqualTo("Standalone Decision");

    // Verify zero values are correctly deserialized
    assertThat(decision.getProcessDefinitionKey())
        .isNotNull()
        .isEqualTo(0L)
        .isInstanceOf(Long.class);
    assertThat(decision.getProcessInstanceKey())
        .isNotNull()
        .isEqualTo(0L)
        .isInstanceOf(Long.class);
  }

  @Test
  public void shouldFilterStandaloneDecisionsByProcessDefinitionKey() {
    // When filtering by processDefinitionKey=0
    final Results<DecisionInstance> results =
        dao.search(
            new Query<DecisionInstance>()
                .setFilter(new DecisionInstance().setProcessDefinitionKey(0L)));

    // Then standalone decisions should be found
    assertThat(results.getTotal()).isGreaterThanOrEqualTo(2);

    final var decisions = results.getItems();
    assertThat(decisions).isNotEmpty();

    // All returned decisions should have processDefinitionKey=0
    assertThat(decisions)
        .allMatch(d -> d.getProcessDefinitionKey() != null && d.getProcessDefinitionKey() == 0L);
  }

  @Test
  public void shouldSortStandaloneDecisionInstances() {
    // When sorting standalone decisions
    final Results<DecisionInstance> results =
        dao.search(
            new Query<DecisionInstance>()
                .setFilter(new DecisionInstance().setProcessDefinitionKey(0L))
                .setSort(Query.Sort.listOf(DecisionInstance.DECISION_VERSION, Query.Sort.Order.ASC)));

    // Then sorting should work correctly
    assertThat(results.getItems()).isNotEmpty();

    final var versions = results.getItems().stream()
        .filter(d -> d.getProcessDefinitionKey() == 0L)
        .map(DecisionInstance::getDecisionVersion)
        .toList();

    assertThat(versions).isSorted();
  }
}
