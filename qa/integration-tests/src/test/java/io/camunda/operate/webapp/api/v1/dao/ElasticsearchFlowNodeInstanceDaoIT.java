/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.util.OperateZeebeIntegrationTest;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort.Order;
import io.camunda.operate.webapp.api.v1.entities.Results;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ElasticsearchFlowNodeInstanceDaoIT extends OperateZeebeIntegrationTest {

  @Autowired
  ElasticsearchFlowNodeInstanceDao dao;

  private Results<FlowNodeInstance> flowNodeInstanceResults;
  private FlowNodeInstance flowNodeInstance;
  private Long key;
  private List<Long> flowNodeInstanceKeys;
  private Long demoProcessKey;
  private Long singleTaskKey;
  private Long processInstanceKey;

  @Before
  public void setUp(){
    demoProcessKey = tester
        .deployProcess("demoProcess_v_1.bpmn")
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
  public void shouldReturnEmptyListWhenNoFlowNodeInstanceExist() throws Exception {
    given(() -> { /*"no flownode instances "*/ });
    when(() -> flowNodeInstanceResults = dao.search(new Query<>()));
    then(() -> {
      assertThat(flowNodeInstanceResults.getItems()).isEmpty();
      assertThat(flowNodeInstanceResults.getTotal()).isZero();
    });
  }

  @Test
  public void shouldReturnFlowNodeInstances() throws Exception {
    given(() ->
        processInstanceKey = createIncidentsAndGetProcessInstanceKey(
            "demoProcess", "taskA", "Some error")
    );
    when(() ->
        flowNodeInstanceResults = dao.search(new Query<>())
    );
    then(() -> {
      assertThat(flowNodeInstanceResults.getItems()).hasSize(2);
      assertThat(flowNodeInstanceResults.getItems().get(0))
          .extracting(
              "processInstanceKey",
              "flowNodeId",
              "flowNodeName",
              "type",
              "state")
          .containsExactly(
              processInstanceKey,
              "start",
              "start",
              "START_EVENT",
              "COMPLETED"
          );
      assertThat(flowNodeInstanceResults.getItems().get(0).getProcessDefinitionKey()).isNotNull();
    });
  }

  @Test
  public void shouldFilterFlowNodeInstances() throws Exception {
    given(() -> {
      createIncidentsAndGetProcessInstanceKey(
          "demoProcess", "taskA", "Some error");
      createIncidentsAndGetProcessInstanceKey(
          "process", "task", "Another error");
    });
    when(() ->
        flowNodeInstanceResults = dao.search(new Query<FlowNodeInstance>()
            .setFilter(
                new FlowNodeInstance().setProcessInstanceKey(processInstanceKey)))
    );
    then(() -> {
      assertThat(flowNodeInstanceResults.getItems()).hasSize(4);
      assertThat(flowNodeInstanceResults.getItems().get(0))
          .extracting(
              "flowNodeId",
              "flowNodeName",
              "type",
              "state")
          .containsExactly(
              "start",
              "start",
              "START_EVENT",
              "COMPLETED"
          );
    });
  }

  @Test
  public void shouldSortFlowNodeInstances() throws Exception {
    given(() -> {
      demoProcessKey = createIncidentsAndGetProcessInstanceKey(
          "demoProcess", "taskA", "Some error");
      singleTaskKey = createIncidentsAndGetProcessInstanceKey(
          "process", "task", "Another error");
    });
    when(() ->
        flowNodeInstanceResults = dao.search(new Query<FlowNodeInstance>()
            .setSort(Sort.listOf(
                FlowNodeInstance.PROCESS_INSTANCE_KEY, Order.DESC)))
    );
    then(() -> {
      assertThat(flowNodeInstanceResults.getItems()).hasSize(4);
      assertThat(flowNodeInstanceResults.getItems().get(0).getProcessInstanceKey())
          .isEqualTo(singleTaskKey);
    });
  }

  @Test
  public void shouldPageFlowNodeInstances() throws Exception {
    given(() -> {
      for (int i = 0; i < 7; i++) {
        createIncidentsAndGetProcessInstanceKey(
            "demoProcess", "taskA", "Some error " + i);
      }
    });
    when(() ->
        flowNodeInstanceResults = dao.search(new Query<FlowNodeInstance>().setSize(5))
    );
    then(() -> {
      assertThat(flowNodeInstanceResults.getTotal()).isEqualTo(14);
      assertThat(flowNodeInstanceResults.getItems()).hasSize(5);
      Object[] searchAfter = flowNodeInstanceResults.getSortValues();
      assertThat(flowNodeInstanceResults.getItems().get(4).getKey()).isEqualTo(searchAfter[0]);

      Long firstKey = flowNodeInstanceResults.getItems().get(0).getKey();
      Long secondKey = flowNodeInstanceResults.getItems().get(1).getKey();

      Results<FlowNodeInstance> nextResults = dao.search(new Query<FlowNodeInstance>().setSearchAfter(new Object[]{firstKey}).setSize(2));
      assertThat(nextResults.getTotal()).isEqualTo(14);
      assertThat(nextResults.getItems()).hasSize(2);
      assertThat(nextResults.getItems().get(0).getKey()).isEqualTo(secondKey);
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
