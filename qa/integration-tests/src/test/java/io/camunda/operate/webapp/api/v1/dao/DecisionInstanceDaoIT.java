/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import io.camunda.operate.entities.dmn.DecisionType;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.util.searchrepository.TestSearchRepository;
import io.camunda.operate.webapp.api.v1.entities.DecisionInstance;
import io.camunda.operate.webapp.api.v1.entities.DecisionInstanceState;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.List;

import static io.camunda.operate.schema.templates.DecisionInstanceTemplate.DECISION_ID;
import static io.camunda.operate.schema.templates.DecisionInstanceTemplate.DECISION_NAME;
import static io.camunda.operate.schema.templates.DecisionInstanceTemplate.DECISION_TYPE;
import static io.camunda.operate.schema.templates.DecisionInstanceTemplate.DEFAULT_TENANT_ID;
import static io.camunda.operate.schema.templates.DecisionInstanceTemplate.PROCESS_DEFINITION_KEY;
import static io.camunda.operate.schema.templates.DecisionInstanceTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.operate.schema.templates.DecisionInstanceTemplate.STATE;
import static org.assertj.core.api.Assertions.assertThat;

public class DecisionInstanceDaoIT extends OperateZeebeAbstractIT {

  protected static final Logger logger = LoggerFactory.getLogger(DecisionInstanceDaoIT.class);

  @Autowired
  DecisionInstanceDao dao;

  @Autowired
  private DecisionInstanceTemplate decisionInstanceTemplate;

  @Autowired
  private TestSearchRepository searchRepository;

  private DecisionInstance decisionInstance;
  private String id;
  private Long processDefinitionKey, processInstanceKey;
  private Results<DecisionInstance> decisionInstanceResults, decisionInstanceResultsPage1, decisionInstanceResultsPage2;

  @Test
  public void shouldReturnWhenById() throws Exception {
    given(() -> {
      processDefinitionKey = deployDecisionAndProcess();
      processInstanceKey = startProcessWithDecision(null);
      List<DecisionInstance> hits = waitForDecisionInstances(1);
      id = hits.get(0).getId();
    });
    when(() -> decisionInstance = dao.byId(id));
    then(() -> {
      assertThat(decisionInstance.getId()).isEqualTo(id);
      assertThat(decisionInstance.getState()).isEqualTo(DecisionInstanceState.FAILED);
      assertThat(decisionInstance.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
      assertThat(decisionInstance.getProcessInstanceKey()).isEqualTo(processInstanceKey);
      assertThat(decisionInstance.getDecisionId()).isEqualTo("invoiceClassification");
      assertThat(decisionInstance.getDecisionName()).isEqualTo("Invoice Classification");
      assertThat(decisionInstance.getDecisionVersion()).isEqualTo(1);
      assertThat(decisionInstance.getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
      assertThat(decisionInstance.getEvaluatedInputs()).isEmpty();
      assertThat(decisionInstance.getEvaluatedOutputs()).isEmpty();
    });
  }

  @Test
  public void shouldReturnEvaluatedWhenById() throws Exception {
    given(() -> {
      String payload = "{\"amount\": 1200, \"invoiceCategory\": \"Travel Expenses\"}";
      processDefinitionKey = deployDecisionAndProcess();
      processInstanceKey = startProcessWithDecision(payload);
      List<DecisionInstance> hits = waitForDecisionInstances(2);
      id = hits.get(0).getId();
    });
    when(() -> decisionInstance = dao.byId(id));
    then(() -> {
      assertThat(decisionInstance.getId()).isEqualTo(id);
      assertThat(decisionInstance.getState()).isEqualTo(DecisionInstanceState.EVALUATED);
      assertThat(decisionInstance.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
      assertThat(decisionInstance.getProcessInstanceKey()).isEqualTo(processInstanceKey);
      assertThat(decisionInstance.getDecisionId()).isEqualTo("invoiceClassification");
      assertThat(decisionInstance.getDecisionName()).isEqualTo("Invoice Classification");
      assertThat(decisionInstance.getDecisionVersion()).isEqualTo(1);
      assertThat(decisionInstance.getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
      assertThat(decisionInstance.getEvaluatedInputs()).hasSize(2);
      assertThat(decisionInstance.getEvaluatedInputs()).extracting("value").containsExactly("1200", "\"Travel Expenses\"");
      assertThat(decisionInstance.getEvaluatedOutputs()).hasSize(1);
      assertThat(decisionInstance.getEvaluatedOutputs()).extracting("value").containsExactly("\"day-to-day expense\"");
    });
  }

  @Test(expected = ResourceNotFoundException.class)
  public void shouldThrowWhenByIdNotExists() throws Exception {
    given(() -> {
    });
    when(() -> dao.byId("-27"));
  }

  @Test(expected = ServerException.class)
  public void shouldThrowWhenByIdFails() throws Exception {
    given(() -> {
    });
    when(() -> dao.byId(null));
  }

  @Test
  public void shouldReturnEmptyListWhenNoDecisionInstanceExist() throws Exception {
    given(() -> { /*"no decision instance"*/ });
    when(() -> decisionInstanceResults = dao.search(new Query<>()));
    then(() -> {
      assertThat(decisionInstanceResults.getItems()).isEmpty();
      assertThat(decisionInstanceResults.getTotal()).isZero();
    });
  }

  @Test
  public void shouldReturnNonEmptyListWhenDecisionInstanceExist() throws Exception {
    given(() -> {
      String payload = "{\"amount\": 1200, \"invoiceCategory\": \"Travel Expenses\"}";
      processDefinitionKey = deployDecisionAndProcess();
      processInstanceKey = startProcessWithDecision(payload);
      waitForDecisionInstances(2);
    });
    when(() -> decisionInstanceResults = dao.search(new Query<>()));
    then(() -> {
      assertThat(decisionInstanceResults.getTotal()).isEqualTo(2);
      assertThat(decisionInstanceResults.getItems()).extracting(DECISION_ID)
              .containsExactlyInAnyOrder("invoiceClassification", "invoiceAssignApprover");
      assertThat(decisionInstanceResults.getItems()).extracting(DECISION_NAME)
              .containsExactlyInAnyOrder("Invoice Classification", "Assign Approver Group");
      assertThat(decisionInstanceResults.getItems()).extracting(DECISION_TYPE)
              .containsExactly(DecisionType.DECISION_TABLE, DecisionType.DECISION_TABLE);
      assertThat(decisionInstanceResults.getItems()).extracting(STATE)
              .containsExactly(DecisionInstanceState.EVALUATED, DecisionInstanceState.EVALUATED);
      assertThat(decisionInstanceResults.getItems()).extracting(PROCESS_DEFINITION_KEY)
              .containsExactly(processDefinitionKey, processDefinitionKey);
      assertThat(decisionInstanceResults.getItems()).extracting(PROCESS_INSTANCE_KEY)
              .containsExactly(processInstanceKey, processInstanceKey);
    });
  }

  @Test
  public void shouldPageWithSearchAfterSizeAndSortedDesc() throws Exception {
    given(() -> {
      String payload = "{\"amount\": 1200, \"invoiceCategory\": \"Travel Expenses\"}";
      processDefinitionKey = deployDecisionAndProcess();
      processInstanceKey = startProcessWithDecision(payload);
      processInstanceKey = startProcessWithDecision(null);
      waitForDecisionInstances(3);
    });
    when(() -> {
      decisionInstanceResultsPage1 = dao.search(new Query<DecisionInstance>().setSize(2)
              .setSort(Query.Sort.listOf(DECISION_ID, Query.Sort.Order.DESC)));
      decisionInstanceResultsPage2 = dao.search(new Query<DecisionInstance>().setSize(1)
              .setSort(Query.Sort.listOf(DECISION_ID, Query.Sort.Order.DESC))
              .setSearchAfter(new Object[]{decisionInstanceResultsPage1.getItems().get(1).getDecisionId(),
                      decisionInstanceResultsPage1.getItems().get(1).getId()}));
    });
    then(() -> {
      assertThat(decisionInstanceResultsPage1.getTotal()).isEqualTo(3);
      assertThat(decisionInstanceResultsPage1.getItems()).hasSize(2);
      assertThat(decisionInstanceResultsPage1.getItems()).extracting(DECISION_ID)
              .containsExactly("invoiceClassification", "invoiceClassification");
      assertThat(decisionInstanceResultsPage1.getItems()).extracting(STATE)
              .containsExactlyInAnyOrder(DecisionInstanceState.EVALUATED, DecisionInstanceState.FAILED);
      assertThat(decisionInstanceResultsPage2.getTotal()).isEqualTo(3);
      assertThat(decisionInstanceResultsPage2.getItems()).hasSize(1);
      assertThat(decisionInstanceResultsPage2.getItems()).extracting(DECISION_ID).containsExactly("invoiceAssignApprover");
      assertThat(decisionInstanceResultsPage2.getItems()).extracting(STATE).containsExactly(DecisionInstanceState.EVALUATED);
    });
  }

  @Test
  public void shouldFilterByFieldAndSortAsc() throws Exception {
    given(() -> {
      String payload = "{\"amount\": 1200, \"invoiceCategory\": \"Travel Expenses\"}";
      processDefinitionKey = deployDecisionAndProcess();
      startProcessWithDecision(payload);
      startProcessWithDecision(null);
      waitForDecisionInstances(3);
    });
    when(() -> {
      final DecisionInstance decisionInstanceFilter = new DecisionInstance().setState(DecisionInstanceState.EVALUATED);
      decisionInstanceResults = dao.search(new Query<DecisionInstance>()
              .setFilter(decisionInstanceFilter)
              .setSort(Query.Sort.listOf(DECISION_ID, Query.Sort.Order.ASC)));
    });
    then(() -> {
      assertThat(decisionInstanceResults.getTotal()).isEqualTo(2);
      assertThat(decisionInstanceResults.getItems()).hasSize(2);
      assertThat(decisionInstanceResults.getItems()).extracting(DECISION_ID)
              .containsExactly("invoiceAssignApprover", "invoiceClassification");
      assertThat(decisionInstanceResults.getItems()).extracting(STATE)
              .containsExactly(DecisionInstanceState.EVALUATED, DecisionInstanceState.EVALUATED);
    });
  }

  @Test
  public void shouldFilterByMultipleFields() throws Exception {
    given(() -> {
      String payload = "{\"amount\": 1200, \"invoiceCategory\": \"Travel Expenses\"}";
      processDefinitionKey = deployDecisionAndProcess();
      startProcessWithDecision(payload);
      startProcessWithDecision(null);
      waitForDecisionInstances(3);
    });
    when(() -> {
      final DecisionInstance decisionInstanceFilter = new DecisionInstance().setState(DecisionInstanceState.EVALUATED)
              .setDecisionId("invoiceAssignApprover");
      decisionInstanceResults = dao.search(new Query<DecisionInstance>()
              .setFilter(decisionInstanceFilter));
    });
    then(() -> {
      assertThat(decisionInstanceResults.getTotal()).isEqualTo(1);
      assertThat(decisionInstanceResults.getItems()).hasSize(1);
      assertThat(decisionInstanceResults.getItems()).extracting(DECISION_ID)
              .containsExactly("invoiceAssignApprover");
      assertThat(decisionInstanceResults.getItems()).extracting(STATE)
              .containsExactly(DecisionInstanceState.EVALUATED);
    });
  }

  @Test
  public void shouldFilterAndPageAndSort() throws Exception {
    given(() -> {
      String payload = "{\"amount\": 1200, \"invoiceCategory\": \"Travel Expenses\"}";
      processDefinitionKey = deployDecisionAndProcess();
      startProcessWithDecision(payload);
      startProcessWithDecision(null);
      waitForDecisionInstances(3);
    });
    when(() -> {
      final DecisionInstance decisionInstanceFilter = new DecisionInstance().setDecisionName("Invoice Classification");
      decisionInstanceResults = dao.search(new Query<DecisionInstance>()
              .setFilter(decisionInstanceFilter)
              .setSort(Query.Sort.listOf(STATE, Query.Sort.Order.DESC))
              .setSize(1));
    });
    then(() -> {
      assertThat(decisionInstanceResults.getTotal()).isEqualTo(2);
      List<DecisionInstance> decisionInstances = decisionInstanceResults.getItems();
      assertThat(decisionInstances).hasSize(1);
      assertThat(decisionInstances).extracting(DECISION_NAME).containsExactly("Invoice Classification");
      assertThat(decisionInstances).extracting(STATE).containsExactly(DecisionInstanceState.FAILED);
    });
  }

  protected List<DecisionInstance> getAllDecisionInstances() {
    String index = decisionInstanceTemplate.getAlias();
    try {
      return searchRepository.searchAll(index, DecisionInstance.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected List<DecisionInstance> waitForDecisionInstances(int count) {
    if (count <= 0) {
      throw new IllegalArgumentException("Expected number of decision instances must be positive.");
    }
    tester.waitUntil().decisionInstancesAreCreated(count);
    return getAllDecisionInstances();
  }

  protected Long deployDecisionAndProcess() {
    tester.deployDecision("invoiceBusinessDecisions_v_1.dmn").waitUntil().decisionsAreDeployed(2);
    return tester.deployProcess("invoice.bpmn").waitUntil().processIsDeployed()
            .getProcessDefinitionKey();
  }

  protected Long startProcessWithDecision(String payload) {
    return tester.startProcessInstance("invoice", payload)
            .waitUntil().processInstanceIsStarted().getProcessInstanceKey();
  }

  protected void given(Runnable conditions) throws Exception {
    conditions.run();
  }

  protected void when(Runnable actions) throws Exception {
    actions.run();
  }

  protected void then(Runnable asserts) throws Exception {
    asserts.run();
  }
}
