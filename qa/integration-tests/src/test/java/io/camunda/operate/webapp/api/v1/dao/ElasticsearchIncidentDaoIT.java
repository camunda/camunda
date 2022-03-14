/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.util.OperateZeebeIntegrationTest;
import io.camunda.operate.webapp.api.v1.entities.Incident;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort.Order;
import io.camunda.operate.webapp.api.v1.entities.Results;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ElasticsearchIncidentDaoIT extends OperateZeebeIntegrationTest {

  @Autowired
  ElasticsearchIncidentDao dao;

  private Results<Incident> incidentResults;
  private Incident incident;
  private Long key;
  private List<Long> incidentKeys;
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
  public void shouldReturnEmptyListWhenNoIncidentExist() throws Exception {
    given(() -> { /*"no incidents "*/ });
    when(() -> incidentResults = dao.search(new Query<>()));
    then(() -> {
      assertThat(incidentResults.getItems()).isEmpty();
      assertThat(incidentResults.getTotal()).isZero();
    });
  }

  @Test
  public void shouldReturnIncidents() throws Exception {
    given(() ->
      processInstanceKey = createIncidentsAndGetProcessInstanceKey(
          "demoProcess", "taskA", "Some error")
    );
    when(() ->
        incidentResults = dao.search(new Query<>())
    );
    then(() -> {
      assertThat(incidentResults.getItems()).hasSize(1);
      assertThat(incidentResults.getItems().get(0))
          .extracting(
              "processDefinitionKey",
              "processInstanceKey",
              "type",
              "state",
              "message")
          .containsExactly(
              demoProcessKey,
              processInstanceKey,
              "JOB_NO_RETRIES",
              "ACTIVE",
              "Some error"
          );
    });
  }

  @Test
  public void shouldFilterIncidents() throws Exception {
    given(() -> {
      createIncidentsAndGetProcessInstanceKey(
          "demoProcess", "taskA", "Some error");
      createIncidentsAndGetProcessInstanceKey(
          "process", "task", "Another error");
    });
    when(() ->
        incidentResults = dao.search(new Query<Incident>()
          .setFilter(
              new Incident().setProcessDefinitionKey(demoProcessKey)))
    );
    then(() -> {
      assertThat(incidentResults.getItems()).hasSize(1);
      assertThat(incidentResults.getItems().get(0))
          .extracting(
              "processDefinitionKey",
              "type",
              "state",
              "message")
          .containsExactly(
              demoProcessKey,
              "JOB_NO_RETRIES",
              "ACTIVE",
              "Some error"
          );
    });
  }

  @Test
  public void shouldSortIncidents() throws Exception {
    given(() -> {
      createIncidentsAndGetProcessInstanceKey(
          "demoProcess", "taskA", "Some error");
      createIncidentsAndGetProcessInstanceKey(
          "process", "task", "Another error");
    });
    when(() ->
      incidentResults = dao.search(new Query<Incident>()
          .setSort(Sort.listOf(
              Incident.PROCESS_DEFINITION_KEY, Order.DESC)))
    );
    then(() -> {
      assertThat(incidentResults.getItems()).hasSize(2);
      assertThat(incidentResults.getItems().get(0).getProcessDefinitionKey())
          .isEqualTo(singleTaskKey);
    });
  }

  @Test
  public void shouldPageIncidents() throws Exception {
    given(() -> {
      for (int i = 0; i < 7; i++) {
        createIncidentsAndGetProcessInstanceKey(
            "demoProcess", "taskA", "Some error " + i);
      }
    });
    when(() ->
      incidentResults = dao.search(new Query<Incident>().setSize(5))
    );
    then(() -> {
      assertThat(incidentResults.getTotal()).isEqualTo(7);
      assertThat(incidentResults.getItems()).hasSize(5);
      Object[] searchAfter = incidentResults.getSortValues();
      assertThat(incidentResults.getItems().get(4).getKey()).isEqualTo(searchAfter[0]);

      Long firstKey = incidentResults.getItems().get(0).getKey();
      Long secondKey = incidentResults.getItems().get(1).getKey();

      Results<Incident> nextResults = dao.search(new Query<Incident>().setSearchAfter(new Object[]{firstKey}).setSize(2));
      assertThat(nextResults.getTotal()).isEqualTo(7);
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
