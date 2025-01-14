/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao;

import static io.camunda.operate.webapp.api.v1.entities.DecisionDefinition.DECISION_REQUIREMENTS_NAME;
import static io.camunda.operate.webapp.api.v1.entities.DecisionDefinition.DECISION_REQUIREMENTS_VERSION;
import static io.camunda.webapps.schema.descriptors.operate.index.DecisionIndex.DECISION_ID;
import static io.camunda.webapps.schema.descriptors.operate.index.DecisionIndex.VERSION;
import static io.camunda.webapps.schema.entities.AbstractExporterEntity.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.DecisionDefinition;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.webapps.schema.descriptors.operate.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.operate.index.DecisionRequirementsIndex;
import io.camunda.webapps.schema.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.webapps.schema.entities.dmn.definition.DecisionRequirementsEntity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class DecisionDefinitionDaoIT extends OperateSearchAbstractIT {

  @Autowired private DecisionDefinitionDao dao;

  @Autowired private DecisionIndex decisionDefinitionIndex;

  @Autowired private DecisionRequirementsIndex decisionRequirementsIndex;

  @Override
  public void runAdditionalBeforeAllSetup() throws Exception {
    // Write the decision requirements
    final DecisionRequirementsEntity requirementsV1 =
        new DecisionRequirementsEntity()
            .setKey(2251799813685249L)
            .setId(String.valueOf(2251799813685249L))
            .setName("Invoice Business Decisions")
            .setVersion(1)
            .setDecisionRequirementsId("invoiceBusinessDecisions");
    final DecisionRequirementsEntity requirementsV2 =
        new DecisionRequirementsEntity()
            .setKey(2251799813685253L)
            .setId(String.valueOf(2251799813685253L))
            .setName("Invoice Business Decisions")
            .setVersion(2)
            .setDecisionRequirementsId("invoiceBusinessDecisions");

    String indexName = decisionRequirementsIndex.getFullQualifiedName();

    testSearchRepository.createOrUpdateDocumentFromObject(indexName, requirementsV1);
    testSearchRepository.createOrUpdateDocumentFromObject(indexName, requirementsV2);

    indexName = decisionDefinitionIndex.getFullQualifiedName();
    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new DecisionDefinitionEntity()
            .setId("2251799813685250")
            .setKey(2251799813685250L)
            .setDecisionId("invoiceAssignApprover")
            .setName("Assign Approver Group")
            .setVersion(requirementsV1.getVersion())
            .setDecisionRequirementsId(requirementsV1.getDecisionRequirementsId())
            .setDecisionRequirementsKey(requirementsV1.getKey())
            .setTenantId(DEFAULT_TENANT_ID));

    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new DecisionDefinitionEntity()
            .setId("2251799813685251")
            .setKey(2251799813685251L)
            .setDecisionId("invoiceClassification")
            .setName("Invoice Classification")
            .setVersion(requirementsV1.getVersion())
            .setDecisionRequirementsId(requirementsV1.getDecisionRequirementsId())
            .setDecisionRequirementsKey(requirementsV1.getKey())
            .setTenantId(DEFAULT_TENANT_ID));

    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new DecisionDefinitionEntity()
            .setId("2251799813685254")
            .setKey(2251799813685254L)
            .setDecisionId("invoiceAssignApprover")
            .setName("Assign Approver Group")
            .setVersion(requirementsV2.getVersion())
            .setDecisionRequirementsId(requirementsV2.getDecisionRequirementsId())
            .setDecisionRequirementsKey(requirementsV2.getKey())
            .setTenantId(DEFAULT_TENANT_ID));

    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new DecisionDefinitionEntity()
            .setId("2251799813685255")
            .setKey(2251799813685255L)
            .setDecisionId("invoiceClassification")
            .setName("Invoice Classification")
            .setVersion(requirementsV2.getVersion())
            .setDecisionRequirementsId(requirementsV2.getDecisionRequirementsId())
            .setDecisionRequirementsKey(requirementsV2.getKey())
            .setTenantId(DEFAULT_TENANT_ID));

    searchContainerManager.refreshIndices("*operate-decision*");
  }

  @Test
  public void shouldReturnDecisionDefinitionsOnSearch() {
    final Results<DecisionDefinition> decisionDefinitionResults = dao.search(new Query<>());

    assertThat(decisionDefinitionResults.getTotal()).isEqualTo(4);
    assertThat(decisionDefinitionResults.getItems())
        .extracting(DECISION_ID)
        .containsExactlyInAnyOrder(
            "invoiceAssignApprover",
            "invoiceClassification",
            "invoiceAssignApprover",
            "invoiceClassification");
    assertThat(decisionDefinitionResults.getItems())
        .extracting(VERSION)
        .containsExactlyInAnyOrder(1, 1, 2, 2);
    assertThat(decisionDefinitionResults.getItems())
        .extracting(DECISION_REQUIREMENTS_NAME)
        .containsExactlyInAnyOrder(
            "Invoice Business Decisions",
            "Invoice Business Decisions",
            "Invoice Business Decisions",
            "Invoice Business Decisions");
    assertThat(decisionDefinitionResults.getItems())
        .extracting(DECISION_REQUIREMENTS_VERSION)
        .containsExactlyInAnyOrder(1, 1, 2, 2);
  }

  @Test
  public void shouldSortDecisionDefinitionsDesc() {
    final var sorts =
        List.of(
            Query.Sort.of(DECISION_ID, Query.Sort.Order.DESC),
            Query.Sort.of(VERSION, Query.Sort.Order.DESC));
    final Results<DecisionDefinition> decisionDefinitionResults =
        dao.search(new Query<DecisionDefinition>().setSort(sorts));

    assertThat(decisionDefinitionResults.getTotal()).isEqualTo(4);

    assertThat(decisionDefinitionResults.getItems())
        .extracting(DECISION_ID)
        .containsExactly(
            "invoiceClassification",
            "invoiceClassification",
            "invoiceAssignApprover",
            "invoiceAssignApprover");
    assertThat(decisionDefinitionResults.getItems())
        .extracting(VERSION)
        .containsExactly(2, 1, 2, 1);
  }

  @Test
  public void shouldSortDecisionDefinitionsAsc() {
    final var sorts =
        List.of(
            Query.Sort.of(DECISION_ID, Query.Sort.Order.ASC),
            Query.Sort.of(VERSION, Query.Sort.Order.ASC));
    final Results<DecisionDefinition> decisionDefinitionResults =
        dao.search(new Query<DecisionDefinition>().setSort(sorts));

    assertThat(decisionDefinitionResults.getTotal()).isEqualTo(4);

    assertThat(decisionDefinitionResults.getItems())
        .extracting(DECISION_ID)
        .containsExactly(
            "invoiceAssignApprover",
            "invoiceAssignApprover",
            "invoiceClassification",
            "invoiceClassification");
    assertThat(decisionDefinitionResults.getItems())
        .extracting(VERSION)
        .containsExactly(1, 2, 1, 2);
  }

  @Test
  public void shouldPageDecisionDefinitions() {
    // Get first page of 3/4 results
    final var sorts =
        List.of(
            Query.Sort.of(DECISION_ID, Query.Sort.Order.DESC),
            Query.Sort.of(VERSION, Query.Sort.Order.DESC));
    Results<DecisionDefinition> decisionDefinitionResults =
        dao.search(new Query<DecisionDefinition>().setSort(sorts).setSize(3));

    assertThat(decisionDefinitionResults.getTotal()).isEqualTo(4);
    assertThat(decisionDefinitionResults.getItems()).hasSize(3);

    assertThat(decisionDefinitionResults.getItems())
        .extracting(DECISION_ID)
        .containsExactly("invoiceClassification", "invoiceClassification", "invoiceAssignApprover");
    assertThat(decisionDefinitionResults.getItems()).extracting(VERSION).containsExactly(2, 1, 2);

    final Object[] searchAfter = decisionDefinitionResults.getSortValues();

    // Get second page with last result
    decisionDefinitionResults =
        dao.search(
            new Query<DecisionDefinition>().setSort(sorts).setSize(3).setSearchAfter(searchAfter));

    assertThat(decisionDefinitionResults.getTotal()).isEqualTo(4);
    assertThat(decisionDefinitionResults.getItems()).hasSize(1);

    assertThat(decisionDefinitionResults.getItems().get(0).getDecisionId())
        .isEqualTo("invoiceAssignApprover");
    assertThat(decisionDefinitionResults.getItems().get(0).getVersion()).isEqualTo(1);
  }

  @Test
  public void shouldFilterDecisionDefinitions() {
    final Results<DecisionDefinition> decisionDefinitionResults =
        dao.search(
            new Query<DecisionDefinition>()
                .setFilter(new DecisionDefinition().setDecisionId("invoiceAssignApprover")));

    assertThat(decisionDefinitionResults.getItems()).hasSize(2);

    assertThat(decisionDefinitionResults.getItems())
        .extracting(DECISION_ID)
        .containsExactly("invoiceAssignApprover", "invoiceAssignApprover");
    assertThat(decisionDefinitionResults.getItems())
        .extracting(VERSION)
        .containsExactlyInAnyOrder(2, 1);
  }

  @Test
  public void shouldFilterByDecisionRequirements() {
    final Results<DecisionDefinition> decisionDefinitionResults =
        dao.search(
            new Query<DecisionDefinition>()
                .setFilter(
                    new DecisionDefinition()
                        .setName("Invoice Classification")
                        .setDecisionRequirementsVersion(2)));

    assertThat(decisionDefinitionResults.getItems()).hasSize(1);

    final DecisionDefinition result = decisionDefinitionResults.getItems().get(0);
    assertThat(result.getDecisionId()).isEqualTo("invoiceClassification");
    assertThat(result.getVersion()).isEqualTo(2);
    assertThat(result.getDecisionRequirementsVersion()).isEqualTo(2);
    assertThat(result.getDecisionRequirementsName()).isEqualTo("Invoice Business Decisions");
  }

  @Test
  public void shouldReturnWhenByKey() {
    final DecisionDefinition decisionDefinition = dao.byKey(2251799813685250L);

    assertThat(decisionDefinition.getKey()).isEqualTo(2251799813685250L);
    assertThat(decisionDefinition.getDecisionId()).isEqualTo("invoiceAssignApprover");
    assertThat(decisionDefinition.getVersion()).isEqualTo(1);
  }

  @Test
  public void shouldThrowWhenKeyNotExists() {
    assertThrows(ResourceNotFoundException.class, () -> dao.byKey(1L));
  }
}
