/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import io.camunda.operate.entities.dmn.DecisionType;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.DecisionInstance;
import io.camunda.operate.webapp.api.v1.entities.DecisionInstanceState;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;
import static io.camunda.operate.schema.templates.DecisionInstanceTemplate.DECISION_ID;
import static io.camunda.operate.schema.templates.DecisionInstanceTemplate.DECISION_NAME;
import static io.camunda.operate.schema.templates.DecisionInstanceTemplate.DECISION_TYPE;
import static io.camunda.operate.schema.templates.DecisionInstanceTemplate.PROCESS_DEFINITION_KEY;
import static io.camunda.operate.schema.templates.DecisionInstanceTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.operate.schema.templates.DecisionInstanceTemplate.STATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DecisionInstanceDaoIT extends OperateSearchAbstractIT {
  @Autowired
  private DecisionInstanceDao dao;

  @Autowired
  private DecisionInstanceTemplate decisionInstanceIndex;

  private static final Long FAKE_PROCESS_DEFINITION_KEY = 2251799813685253L;
  private static final Long FAKE_PROCESS_INSTANCE_KEY = 2251799813685255L;

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    String indexName = decisionInstanceIndex.getFullQualifiedName();
    testSearchRepository.createOrUpdateDocumentFromObject(indexName, new DecisionInstance().setId("2251799813685262-1")
        .setKey(2251799813685262L).setState(DecisionInstanceState.EVALUATED).setEvaluationDate("2024-01-22T16:41:36.316+0000")
        .setProcessDefinitionKey(FAKE_PROCESS_DEFINITION_KEY).setProcessInstanceKey(FAKE_PROCESS_INSTANCE_KEY).setDecisionId("invoiceClassification")
        .setDecisionDefinitionId("2251799813685251").setDecisionName("Invoice Classification").setDecisionVersion(1)
        .setDecisionType(DecisionType.DECISION_TABLE).setResult("\"day-to-day expense\"").setTenantId(DEFAULT_TENANT_ID));

    testSearchRepository.createOrUpdateDocumentFromObject(indexName, new DecisionInstance().setId("2251799813685262-2")
        .setKey(2251799813685262L).setState(DecisionInstanceState.EVALUATED).setEvaluationDate("2024-01-22T16:41:36.316+0000")
        .setProcessDefinitionKey(FAKE_PROCESS_DEFINITION_KEY).setProcessInstanceKey(FAKE_PROCESS_INSTANCE_KEY).setDecisionId("invoiceAssignApprover")
        .setDecisionDefinitionId("2251799813685250").setDecisionName("Assign Approver Group").setDecisionVersion(1)
        .setDecisionType(DecisionType.DECISION_TABLE).setResult("\"day-to-day expense\"").setTenantId(DEFAULT_TENANT_ID));

    searchContainerManager.refreshIndices("*operate-decision*");
  }
  @Test
  public void shouldReturnDecisionInstances() {
    Results<DecisionInstance> decisionInstanceResults = dao.search(new Query<>());

    assertThat(decisionInstanceResults.getTotal()).isEqualTo(2);

    DecisionInstance checkDecisionInstance = decisionInstanceResults.getItems().stream()
        .filter(item -> "invoiceClassification".equals(item.getDecisionId())).findFirst().orElse(null);
    assertThat(checkDecisionInstance).isNotNull();
    assertThat(checkDecisionInstance).extracting(DECISION_ID, DECISION_NAME, DECISION_TYPE, STATE,
            PROCESS_DEFINITION_KEY, PROCESS_INSTANCE_KEY)
        .containsExactly("invoiceClassification", "Invoice Classification", DecisionType.DECISION_TABLE,
            DecisionInstanceState.EVALUATED, FAKE_PROCESS_DEFINITION_KEY, FAKE_PROCESS_INSTANCE_KEY);

    checkDecisionInstance = decisionInstanceResults.getItems().stream()
        .filter(item -> "invoiceAssignApprover".equals(item.getDecisionId())).findFirst().orElse(null);
    assertThat(checkDecisionInstance).isNotNull();
    assertThat(checkDecisionInstance).extracting(DECISION_ID, DECISION_NAME, DECISION_TYPE, STATE,
            PROCESS_DEFINITION_KEY, PROCESS_INSTANCE_KEY)
        .containsExactly("invoiceAssignApprover", "Assign Approver Group", DecisionType.DECISION_TABLE,
            DecisionInstanceState.EVALUATED, FAKE_PROCESS_DEFINITION_KEY, FAKE_PROCESS_INSTANCE_KEY);
  }

  @Test
  public void shouldSortDecisionInstancesDesc() {
    Results<DecisionInstance> decisionInstanceResults = dao.search(new Query<DecisionInstance>()
        .setSort(Query.Sort.listOf(DECISION_ID, Query.Sort.Order.DESC)));

    assertThat(decisionInstanceResults.getTotal()).isEqualTo(2);
    assertThat(decisionInstanceResults.getItems()).extracting(DECISION_ID)
        .containsExactly("invoiceClassification", "invoiceAssignApprover");
  }

  @Test
  public void shouldSortDecisionInstancesAsc() {
    Results<DecisionInstance> decisionInstanceResults = dao.search(new Query<DecisionInstance>()
        .setSort(Query.Sort.listOf(DECISION_ID, Query.Sort.Order.ASC)));

    assertThat(decisionInstanceResults.getTotal()).isEqualTo(2);
    assertThat(decisionInstanceResults.getItems()).extracting(DECISION_ID)
        .containsExactly("invoiceAssignApprover", "invoiceClassification");
  }

  @Test
  public void shouldPageDecisionInstances() {
    Results<DecisionInstance> decisionInstanceResults = dao.search(new Query<DecisionInstance>()
        .setSort(Query.Sort.listOf(DECISION_ID, Query.Sort.Order.DESC)).setSize(1));

    assertThat(decisionInstanceResults.getTotal()).isEqualTo(2);
    assertThat(decisionInstanceResults.getItems()).hasSize(1);

    assertThat(decisionInstanceResults.getItems().get(0).getDecisionId()).isEqualTo("invoiceClassification");

    Object[] searchAfter = decisionInstanceResults.getSortValues();
    decisionInstanceResults = dao.search(new Query<DecisionInstance>()
        .setSort(Query.Sort.listOf(DECISION_ID, Query.Sort.Order.DESC)).setSize(1).setSearchAfter(searchAfter));

    assertThat(decisionInstanceResults.getTotal()).isEqualTo(2);
    assertThat(decisionInstanceResults.getItems()).hasSize(1);

    assertThat(decisionInstanceResults.getItems().get(0).getDecisionId()).isEqualTo("invoiceAssignApprover");
  }

  @Test
  public void shouldFilterDecisionInstances() {
    Results<DecisionInstance> decisionInstanceResults = dao.search(new Query<DecisionInstance>()
        .setSort(Query.Sort.listOf(DECISION_ID, Query.Sort.Order.DESC))
        .setFilter(new DecisionInstance().setDecisionId("invoiceAssignApprover")));

    assertThat(decisionInstanceResults.getTotal()).isEqualTo(1);

    assertThat(decisionInstanceResults.getItems().get(0).getDecisionId()).isEqualTo("invoiceAssignApprover");
  }

  @Test
  public void shouldReturnById() {
    String decisionInstanceId = "2251799813685262-2";
    DecisionInstance decisionInstance = dao.byId(decisionInstanceId);

    assertThat(decisionInstance).isNotNull();
    assertThat(decisionInstance.getDecisionId()).isEqualTo("invoiceAssignApprover");
    assertThat(decisionInstance.getId()).isEqualTo(decisionInstanceId);
  }

  @Test
  public void shouldThrowWhenIdNotExists() {
    assertThrows(ResourceNotFoundException.class, () -> dao.byId("test"));
  }
}
