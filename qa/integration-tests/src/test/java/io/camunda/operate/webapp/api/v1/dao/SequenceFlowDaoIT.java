/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort.Order;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.entities.SequenceFlow;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

public class SequenceFlowDaoIT extends OperateZeebeAbstractIT {

  @Autowired
  private SequenceFlowDao dao;

  private Results<SequenceFlow> sequenceFlowResults;
  private Long demoProcessKey;
  private Long singleTaskKey;
  private Long processInstanceKey;

  @Before
  public void setUp(){
    demoProcessKey = tester
        .deployProcess("sequenceFlowsProcess_v1.bpmn")
        .waitUntil().processIsDeployed()
        .getProcessDefinitionKey();
    singleTaskKey = tester
        .deployProcess("single-task.bpmn")
        .waitUntil().processIsDeployed()
        .getProcessDefinitionKey();
  }

  protected Long createIncidentsAndGetProcessInstanceKey(String bpmnProcessId, String taskId,String errorMessage) {
    return tester
        .startProcessInstance(bpmnProcessId, "{\"a\": \"b\"}").waitUntil().processInstanceIsStarted()
        .and()
        .failTask(taskId, errorMessage).waitUntil().incidentIsActive()
        .and()
        .getProcessInstanceKey();
  }

  @Test
  public void shouldReturnSequenceFlows() throws Exception {
    given(() ->
      processInstanceKey = createIncidentsAndGetProcessInstanceKey(
          "sequenceFlowsProcessV1", "taskA", "Some error")
    );
    when(() ->
        sequenceFlowResults = dao.search(new Query<>())
    );
    then(() -> {
      assertThat(sequenceFlowResults.getItems()).hasSize(4);
      assertThat(sequenceFlowResults.getItems().get(0))
          .extracting("processInstanceKey", "activityId", "tenantId")
          .containsExactly(
              processInstanceKey, "sequenceFlow_01", DEFAULT_TENANT_ID);
      assertThat(sequenceFlowResults.getItems().get(1))
          .extracting("processInstanceKey", "activityId", "tenantId")
          .containsExactly(
              processInstanceKey, "sequenceFlow_02", DEFAULT_TENANT_ID);
      assertThat(sequenceFlowResults.getItems().get(2))
          .extracting("processInstanceKey", "activityId", "tenantId")
          .containsExactly(
              processInstanceKey, "sequenceFlow_03", DEFAULT_TENANT_ID);
      assertThat(sequenceFlowResults.getItems().get(3))
          .extracting("processInstanceKey", "activityId", "tenantId")
          .containsExactly(
              processInstanceKey, "sequenceFlow_04", DEFAULT_TENANT_ID);
    });
  }

  @Test
  public void shouldReturnSequenceFlowsWithEmptyFilter() throws Exception {
    given(() ->
        processInstanceKey = createIncidentsAndGetProcessInstanceKey(
            "sequenceFlowsProcessV1", "taskA", "Some error")
    );
    when(() ->
        sequenceFlowResults = dao.search(new Query<SequenceFlow>().setFilter(new SequenceFlow()))
    );
    then(() -> {
      assertThat(sequenceFlowResults.getItems()).hasSize(4);
      assertThat(sequenceFlowResults.getItems().get(0))
          .extracting("processInstanceKey", "activityId", "tenantId")
          .containsExactly(
              processInstanceKey, "sequenceFlow_01", DEFAULT_TENANT_ID);
      assertThat(sequenceFlowResults.getItems().get(1))
          .extracting("processInstanceKey", "activityId", "tenantId")
          .containsExactly(
              processInstanceKey, "sequenceFlow_02", DEFAULT_TENANT_ID);
      assertThat(sequenceFlowResults.getItems().get(2))
          .extracting("processInstanceKey", "activityId", "tenantId")
          .containsExactly(
              processInstanceKey, "sequenceFlow_03", DEFAULT_TENANT_ID);
      assertThat(sequenceFlowResults.getItems().get(3))
          .extracting("processInstanceKey", "activityId", "tenantId")
          .containsExactly(
              processInstanceKey, "sequenceFlow_04", DEFAULT_TENANT_ID);
    });
  }

  @Test
  public void shouldFilterSequenceFlows() throws Exception {
    given(() -> {
      processInstanceKey = createIncidentsAndGetProcessInstanceKey(
          "sequenceFlowsProcessV1", "taskA", "Some error");
      createIncidentsAndGetProcessInstanceKey(
          "process", "task", "Another error");
    });
    when(() ->
        sequenceFlowResults = dao.search(new Query<SequenceFlow>().setFilter(new SequenceFlow().setProcessInstanceKey(processInstanceKey)))
    );
    then(() -> {
      assertThat(sequenceFlowResults.getItems()).hasSize(4);
      assertThat(sequenceFlowResults.getItems().get(0))
          .extracting("processInstanceKey", "activityId")
          .containsExactly(processInstanceKey, "sequenceFlow_01");
      assertThat(sequenceFlowResults.getItems().get(1))
          .extracting("processInstanceKey", "activityId")
          .containsExactly(processInstanceKey, "sequenceFlow_02");
      assertThat(sequenceFlowResults.getItems().get(2))
          .extracting("processInstanceKey", "activityId")
          .containsExactly(processInstanceKey, "sequenceFlow_03");
      assertThat(sequenceFlowResults.getItems().get(3))
          .extracting("processInstanceKey", "activityId")
          .containsExactly(processInstanceKey, "sequenceFlow_04");
    });
  }

  @Test
  public void shouldSortSequenceFlows() throws Exception {
    given(() -> {
      createIncidentsAndGetProcessInstanceKey(
          "sequenceFlowsProcessV1", "taskA", "Some error");
      processInstanceKey = createIncidentsAndGetProcessInstanceKey(
          "process", "task", "Another error");
    });
    when(() ->
      sequenceFlowResults = dao.search(new Query<SequenceFlow>()
          .setSort(Sort.listOf(
              SequenceFlow.ACTIVITY_ID, Order.DESC)))
    );
    then(() -> {
      assertThat(sequenceFlowResults.getItems()).hasSize(5);
      assertThat(sequenceFlowResults.getItems().get(0).getActivityId()).isEqualTo("sequenceFlow_04");
      assertThat(sequenceFlowResults.getItems().get(1).getActivityId()).isEqualTo("sequenceFlow_03");
      assertThat(sequenceFlowResults.getItems().get(2).getActivityId()).isEqualTo("sequenceFlow_02");
      assertThat(sequenceFlowResults.getItems().get(3).getActivityId()).isEqualTo("sequenceFlow_01");
      assertThat(sequenceFlowResults.getItems().get(4).getActivityId()).isEqualTo("SequenceFlow_04ev4jl");
    });
  }

  @Test
  public void shouldPageSequenceFlows() throws Exception {
    given(() -> {
      for (int i = 0; i < 2; i++) {
        createIncidentsAndGetProcessInstanceKey(
            "sequenceFlowsProcessV1", "taskA", "Some error " + i);
      }
    });
    when(() ->
      sequenceFlowResults = dao.search(new Query<SequenceFlow>().setSize(5))
    );
    then(() -> {
      assertThat(sequenceFlowResults.getTotal()).isEqualTo(8);
      assertThat(sequenceFlowResults.getItems()).hasSize(5);
      Object[] searchAfter = sequenceFlowResults.getSortValues();

      Results<SequenceFlow> nextResults = dao.search(new Query<SequenceFlow>().setSearchAfter(searchAfter).setSize(3));
      assertThat(nextResults.getTotal()).isEqualTo(8);
      assertThat(nextResults.getItems()).hasSize(3);
    });
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
