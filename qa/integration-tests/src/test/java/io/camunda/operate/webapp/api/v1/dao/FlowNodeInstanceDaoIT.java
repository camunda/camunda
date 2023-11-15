/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort.Order;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

public class FlowNodeInstanceDaoIT extends OperateZeebeAbstractIT {

  @Autowired
  private FlowNodeInstanceDao dao;

  private Results<FlowNodeInstance> flowNodeInstanceResults;
  private Long demoProcessDefinitionKey;
  private Long singleTaskDefinitionKey;
  private Long processInstanceKey;

  private Long flowNodeInstanceKey;
  private FlowNodeInstance flowNodeInstance;

  @Before
  public void setUp(){
    demoProcessDefinitionKey = tester
        .deployProcess("demoProcess_v_1.bpmn")
        .waitUntil().processIsDeployed()
        .getProcessDefinitionKey();
    singleTaskDefinitionKey = tester
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
              "processInstanceKey", "processDefinitionKey", "flowNodeId", "flowNodeName",
              "type", "state", "tenantId")
          .containsExactly(
              processInstanceKey, demoProcessDefinitionKey, "start", "start",
              "START_EVENT", "COMPLETED", DEFAULT_TENANT_ID
          );
      assertThat(flowNodeInstanceResults.getItems().get(1))
          .extracting(
              "processInstanceKey", "processDefinitionKey", "flowNodeId", "flowNodeName",
              "type", "state", "tenantId")
          .containsExactly(
              processInstanceKey, demoProcessDefinitionKey, "taskA", "task A",
              "SERVICE_TASK", "ACTIVE", DEFAULT_TENANT_ID
          );
    });
  }

  @Test
  public void shouldFilterFlowNodeInstances() throws Exception {
    given(() -> {
      processInstanceKey = createIncidentsAndGetProcessInstanceKey(
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
      assertThat(flowNodeInstanceResults.getItems()).hasSize(2);
      assertThat(flowNodeInstanceResults.getItems().get(0))
          .extracting(
              "flowNodeId", "flowNodeName", "type", "state")
          .containsExactly(
              "start", "start", "START_EVENT", "COMPLETED"
          );
      assertThat(flowNodeInstanceResults.getItems().get(1))
          .extracting(
              "processInstanceKey", "processDefinitionKey", "flowNodeId", "flowNodeName",
              "type", "state", "tenantId")
          .containsExactly(
              processInstanceKey, demoProcessDefinitionKey, "taskA", "task A",
              "SERVICE_TASK", "ACTIVE", DEFAULT_TENANT_ID
          );
    });
  }

  @Test
  public void shouldSortFlowNodeInstances() throws Exception {
    given(() -> {
      createIncidentsAndGetProcessInstanceKey(
          "demoProcess", "taskA", "Some error");
      processInstanceKey = createIncidentsAndGetProcessInstanceKey(
          "process", "task", "Another error");
    });
    when(() ->
        flowNodeInstanceResults = dao.search(new Query<FlowNodeInstance>()
            .setSort(Sort.listOf(
                FlowNodeInstance.FLOW_NODE_ID, Order.DESC)))
    );
    then(() -> {
      assertThat(flowNodeInstanceResults.getItems()).hasSize(4);
      assertThat(flowNodeInstanceResults.getItems().get(0).getFlowNodeId())
          .isEqualTo("taskA");
      assertThat(flowNodeInstanceResults.getItems().get(1).getFlowNodeId())
          .isEqualTo("task");
      assertThat(flowNodeInstanceResults.getItems().get(2).getFlowNodeId())
          .isEqualTo("start");
      assertThat(flowNodeInstanceResults.getItems().get(3).getFlowNodeId())
          .isEqualTo("StartEvent_1");
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
      assertThat(String.valueOf(flowNodeInstanceResults.getItems().get(4).getKey())).isEqualTo(String.valueOf(searchAfter[0]));

      Long firstKey = flowNodeInstanceResults.getItems().get(0).getKey();
      Long secondKey = flowNodeInstanceResults.getItems().get(1).getKey();

      Results<FlowNodeInstance> nextResults = dao.search(new Query<FlowNodeInstance>().setSearchAfter(new Object[]{firstKey}).setSize(2));
      assertThat(nextResults.getTotal()).isEqualTo(14);
      assertThat(nextResults.getItems()).hasSize(2);
      assertThat(nextResults.getItems().get(0).getKey()).isEqualTo(secondKey);
    });
  }

  @Test
  public void shouldReturnByKey() throws Exception {
    given(() -> {
        processInstanceKey = createIncidentsAndGetProcessInstanceKey(
            "demoProcess", "taskA", "Some error");
        createIncidentsAndGetProcessInstanceKey(
        "process", "task", "Another error");
        flowNodeInstanceResults = dao.search(new Query<FlowNodeInstance>()
        .setFilter(
            new FlowNodeInstance().setProcessInstanceKey(processInstanceKey)));

        flowNodeInstanceKey = flowNodeInstanceResults.getItems().get(0).getKey();
    });

    when(() -> flowNodeInstance = dao.byKey(flowNodeInstanceKey));

    then(() -> {
      assertThat(flowNodeInstance)
          .extracting(
              "flowNodeId", "flowNodeName", "type", "state")
          .containsExactly(
              "start", "start", "START_EVENT", "COMPLETED"
          );
    });
  }

  @Test(expected= ResourceNotFoundException.class)
  public void shouldThrowExceptionWhenNoKeyFound() {
    processInstanceKey = createIncidentsAndGetProcessInstanceKey(
        "demoProcess", "taskA", "Some error");

    dao.byKey(1L);
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
