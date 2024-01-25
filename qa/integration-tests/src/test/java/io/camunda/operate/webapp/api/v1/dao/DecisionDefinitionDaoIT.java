/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.DecisionDefinition;
import io.camunda.operate.webapp.api.v1.entities.DecisionRequirements;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static io.camunda.operate.schema.indices.DecisionIndex.DECISION_ID;
import static io.camunda.operate.schema.indices.DecisionIndex.VERSION;
import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;
import static io.camunda.operate.webapp.api.v1.entities.DecisionDefinition.DECISION_REQUIREMENTS_NAME;
import static io.camunda.operate.webapp.api.v1.entities.DecisionDefinition.DECISION_REQUIREMENTS_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DecisionDefinitionDaoIT extends OperateSearchAbstractIT {

  @Autowired
  private DecisionDefinitionDao dao;

  @Autowired
  private DecisionIndex decisionDefinitionIndex;

  @Autowired
  private DecisionRequirementsIndex decisionRequirementsIndex;

  @Override
  public void runAdditionalBeforeAllSetup() throws Exception {
    // Write the decision requirements
    DecisionRequirements requirementsV1 = new DecisionRequirements().setKey(2251799813685249L)
        .setId(String.valueOf(2251799813685249L)).setName("Invoice Business Decisions").setVersion(1)
        .setDecisionRequirementsId("invoiceBusinessDecisions");
    DecisionRequirements requirementsV2 = new DecisionRequirements().setKey(2251799813685253L)
        .setId(String.valueOf(2251799813685253L)).setName("Invoice Business Decisions").setVersion(2)
        .setDecisionRequirementsId("invoiceBusinessDecisions");

    String indexName = decisionRequirementsIndex.getFullQualifiedName();

    testSearchRepository.createOrUpdateDocumentFromObject(indexName, requirementsV1);
    testSearchRepository.createOrUpdateDocumentFromObject(indexName, requirementsV2);

    indexName = decisionDefinitionIndex.getFullQualifiedName();
    testSearchRepository.createOrUpdateDocumentFromObject(indexName, new DecisionDefinition().setId("2251799813685250")
        .setKey(2251799813685250L).setDecisionId("invoiceAssignApprover").setName("Assign Approver Group")
        .setVersion(requirementsV1.getVersion()).setDecisionRequirementsId(requirementsV1.getDecisionRequirementsId())
        .setDecisionRequirementsKey(requirementsV1.getKey()).setTenantId(DEFAULT_TENANT_ID));

    testSearchRepository.createOrUpdateDocumentFromObject(indexName, new DecisionDefinition().setId("2251799813685251")
        .setKey(2251799813685251L).setDecisionId("invoiceClassification").setName("Invoice Classification")
        .setVersion(requirementsV1.getVersion()).setDecisionRequirementsId(requirementsV1.getDecisionRequirementsId())
        .setDecisionRequirementsKey(requirementsV1.getKey()).setTenantId(DEFAULT_TENANT_ID));

    testSearchRepository.createOrUpdateDocumentFromObject(indexName, new DecisionDefinition().setId("2251799813685254")
        .setKey(2251799813685254L).setDecisionId("invoiceAssignApprover").setName("Assign Approver Group")
        .setVersion(requirementsV2.getVersion()).setDecisionRequirementsId(requirementsV2.getDecisionRequirementsId())
        .setDecisionRequirementsKey(requirementsV2.getKey()).setTenantId(DEFAULT_TENANT_ID));

    testSearchRepository.createOrUpdateDocumentFromObject(indexName, new DecisionDefinition().setId("2251799813685255")
        .setKey(2251799813685255L).setDecisionId("invoiceClassification").setName("Invoice Classification")
        .setVersion(requirementsV2.getVersion()).setDecisionRequirementsId(requirementsV2.getDecisionRequirementsId())
        .setDecisionRequirementsKey(requirementsV2.getKey()).setTenantId(DEFAULT_TENANT_ID));

    searchContainerManager.refreshIndices("*operate-decision*");
  }

  @Test
  public void shouldReturnDecisionDefinitionsOnSearch() {
    Results<DecisionDefinition> decisionDefinitionResults = dao.search(new Query<>());

    assertThat(decisionDefinitionResults.getTotal()).isEqualTo(4);
    assertThat(decisionDefinitionResults.getItems()).extracting(DECISION_ID)
        .containsExactlyInAnyOrder("invoiceAssignApprover", "invoiceClassification", "invoiceAssignApprover", "invoiceClassification");
    assertThat(decisionDefinitionResults.getItems()).extracting(VERSION).containsExactlyInAnyOrder(1, 1, 2, 2);
    assertThat(decisionDefinitionResults.getItems()).extracting(DECISION_REQUIREMENTS_NAME)
        .containsExactlyInAnyOrder("Invoice Business Decisions", "Invoice Business Decisions", "Invoice Business Decisions", "Invoice Business Decisions");
    assertThat(decisionDefinitionResults.getItems()).extracting(DECISION_REQUIREMENTS_VERSION).containsExactlyInAnyOrder(1, 1, 2, 2);
  }

  @Test
  public void shouldSortDecisionDefinitionsDesc() {
    var sorts = List.of(Query.Sort.of(DECISION_ID, Query.Sort.Order.DESC), Query.Sort.of(VERSION, Query.Sort.Order.DESC));
    Results<DecisionDefinition> decisionDefinitionResults = dao.search(new Query<DecisionDefinition>().setSort(sorts));

    assertThat(decisionDefinitionResults.getTotal()).isEqualTo(4);

    assertThat(decisionDefinitionResults.getItems()).extracting(DECISION_ID)
        .containsExactly("invoiceClassification", "invoiceClassification", "invoiceAssignApprover", "invoiceAssignApprover");
    assertThat(decisionDefinitionResults.getItems()).extracting(VERSION).containsExactly(2, 1, 2, 1);
  }

  @Test
  public void shouldSortDecisionDefinitionsAsc() {
    var sorts = List.of(Query.Sort.of(DECISION_ID, Query.Sort.Order.ASC), Query.Sort.of(VERSION, Query.Sort.Order.ASC));
    Results<DecisionDefinition> decisionDefinitionResults = dao.search(new Query<DecisionDefinition>().setSort(sorts));

    assertThat(decisionDefinitionResults.getTotal()).isEqualTo(4);

    assertThat(decisionDefinitionResults.getItems()).extracting(DECISION_ID)
        .containsExactly("invoiceAssignApprover", "invoiceAssignApprover", "invoiceClassification", "invoiceClassification");
    assertThat(decisionDefinitionResults.getItems()).extracting(VERSION).containsExactly(1, 2, 1, 2);
  }

  @Test
  public void shouldPageDecisionDefinitions() {
    // Get first page of 3/4 results
    var sorts = List.of(Query.Sort.of(DECISION_ID, Query.Sort.Order.DESC), Query.Sort.of(VERSION, Query.Sort.Order.DESC));
    Results<DecisionDefinition> decisionDefinitionResults = dao.search(new Query<DecisionDefinition>().setSort(sorts).setSize(3));

    assertThat(decisionDefinitionResults.getTotal()).isEqualTo(4);
    assertThat(decisionDefinitionResults.getItems()).hasSize(3);

    assertThat(decisionDefinitionResults.getItems()).extracting(DECISION_ID)
        .containsExactly("invoiceClassification", "invoiceClassification", "invoiceAssignApprover");
    assertThat(decisionDefinitionResults.getItems()).extracting(VERSION).containsExactly(2, 1, 2);

    Object[] searchAfter = decisionDefinitionResults.getSortValues();

    // Get second page with last result
    decisionDefinitionResults = dao.search(new Query<DecisionDefinition>().setSort(sorts).setSize(3).setSearchAfter(searchAfter));

    assertThat(decisionDefinitionResults.getTotal()).isEqualTo(4);
    assertThat(decisionDefinitionResults.getItems()).hasSize(1);

    assertThat(decisionDefinitionResults.getItems().get(0).getDecisionId()).isEqualTo("invoiceAssignApprover");
    assertThat(decisionDefinitionResults.getItems().get(0).getVersion()).isEqualTo(1);
  }

  @Test
  public void shouldFilterDecisionDefinitions() {
    Results<DecisionDefinition> decisionDefinitionResults = dao.search(new Query<DecisionDefinition>()
        .setFilter(new DecisionDefinition().setDecisionId("invoiceAssignApprover")));

    assertThat(decisionDefinitionResults.getItems()).hasSize(2);

    assertThat(decisionDefinitionResults.getItems()).extracting(DECISION_ID)
        .containsExactly("invoiceAssignApprover", "invoiceAssignApprover");
    assertThat(decisionDefinitionResults.getItems()).extracting(VERSION).containsExactlyInAnyOrder(2, 1);
  }

  @Test
  public void shouldFilterByDecisionRequirements() {
    Results<DecisionDefinition> decisionDefinitionResults = dao.search(new Query<DecisionDefinition>()
        .setFilter(new DecisionDefinition().setName("Invoice Classification").setDecisionRequirementsVersion(2)));

    assertThat(decisionDefinitionResults.getItems()).hasSize(1);

    DecisionDefinition result = decisionDefinitionResults.getItems().get(0);
    assertThat(result.getDecisionId()).isEqualTo("invoiceClassification");
    assertThat(result.getVersion()).isEqualTo(2);
    assertThat(result.getDecisionRequirementsVersion()).isEqualTo(2);
    assertThat(result.getDecisionRequirementsName()).isEqualTo("Invoice Business Decisions");
  }

  @Test
  public void shouldReturnWhenByKey() {
    DecisionDefinition decisionDefinition = dao.byKey(2251799813685250L);

    assertThat(decisionDefinition.getKey()).isEqualTo(2251799813685250L);
    assertThat(decisionDefinition.getDecisionId()).isEqualTo("invoiceAssignApprover");
    assertThat(decisionDefinition.getVersion()).isEqualTo(1);
  }

  @Test
  public void shouldThrowWhenKeyNotExists() {
    assertThrows(ResourceNotFoundException.class, () -> dao.byKey(1L));
  }
}
