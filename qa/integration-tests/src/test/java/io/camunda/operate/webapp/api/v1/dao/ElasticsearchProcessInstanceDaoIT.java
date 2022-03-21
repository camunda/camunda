/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import static io.camunda.operate.webapp.api.v1.entities.ProcessInstance.BPMN_PROCESS_ID;
import static io.camunda.operate.webapp.api.v1.entities.ProcessInstance.VERSION;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.OperateZeebeIntegrationTest;
import io.camunda.operate.util.ThreadUtil;
import io.camunda.operate.webapp.api.v1.entities.ChangeStatus;
import io.camunda.operate.util.OperateZeebeIntegrationTest;
import io.camunda.operate.webapp.api.v1.entities.ProcessInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort;
import io.camunda.operate.webapp.api.v1.entities.Query.Sort.Order;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ElasticsearchProcessInstanceDaoIT extends OperateZeebeIntegrationTest {

  @Autowired
  private ElasticsearchProcessInstanceDao dao;

  private Results<ProcessInstance> processInstanceResults;
  private ProcessInstance processInstance;
  private Long key;
  private List<Long> processInstanceKeys;

  private ChangeStatus changeStatus;

  @Test
  public void shouldReturnEmptyListWhenNoProcessDefinitionsExist() throws Exception {
    given(() -> { /*"no process definitions"*/ });
    when(() -> processInstanceResults = dao.search(new Query<>()));
    then(() -> {
      assertThat(processInstanceResults.getItems()).isEmpty();
      assertThat(processInstanceResults.getTotal()).isZero();
    });

    given(() -> {
      deployProcesses(
          "demoProcess_v_1.bpmn", "errorProcess.bpmn", "complexProcess_v_3.bpmn");
      startProcesses("demoProcess","errorProcess","complexProcess");
    });
    when(() -> processInstanceResults = dao.search(new Query<>()));
    then(() -> {
      assertThat(processInstanceResults.getTotal()).isEqualTo(3);
      assertThat(processInstanceResults.getItems()).extracting(BPMN_PROCESS_ID)
          .contains("demoProcess", "errorProcess", "complexProcess");
    });
  }

  @Test
  public void shouldReturnByKey() throws Exception {
    given(() -> {
      deployProcesses("complexProcess_v_3.bpmn", "demoProcess_v_2.bpmn");
      processInstanceKeys = startProcesses("complexProcess", "demoProcess");
      processInstanceResults = dao.search(new Query<ProcessInstance>()
          .setSort(Sort.listOf(BPMN_PROCESS_ID)));
      key = processInstanceResults.getItems().get(0).getKey();
    });
    when(() -> processInstance = dao.byKey(key));
    then(() -> {
      assertThat(processInstance.getKey()).isEqualTo(key);
      assertThat(processInstance.getBpmnProcessId()).isEqualTo("complexProcess");
    });
  }

  @Test
  public void shouldDeleteByKey() throws Exception {
    given(() -> {
      deployProcesses("single-task.bpmn", "complexProcess_v_3.bpmn", "demoProcess_v_2.bpmn");
      key  = tester.startProcessInstance("process", null)
              .and().completeTask("task", null)
              .waitUntil()
              .processInstanceIsFinished()
              .getProcessInstanceKey();
      startProcesses("complexProcess", "demoProcess");
      processInstanceResults = dao.search(new Query<>());
    });
    when(() -> changeStatus = dao.delete(key));
    then(() -> {
      assertThat(changeStatus.getDeleted()).isEqualTo(1);
      assertThat(changeStatus.getMessage()).contains(""+key);
      elasticsearchTestRule.refreshIndexesInElasticsearch();
      processInstanceResults = dao.search(new Query<>());
      assertThat(processInstanceResults.getItems().stream()
          .noneMatch(pi -> pi.getKey().equals(key))).isTrue();
    });
  }

  @Test(expected = ResourceNotFoundException.class)
  public void shouldThrowForDeleteWhenKeyNotExists() throws Exception {
    given(() -> {
      deployProcesses("single-task.bpmn", "complexProcess_v_3.bpmn", "demoProcess_v_2.bpmn");
      startProcesses("process", "complexProcess", "demoProcess");
    });
    when(() -> dao.delete(123L));
  }

  @Test(expected = ResourceNotFoundException.class)
  public void showThrowWhenByKeyNotExists() throws Exception {
    given(() -> {});
    when(() -> dao.byKey(-27L));
  }

  @Test(expected = ServerException.class)
  public void shouldThrowWhenByKeyFails() throws Exception {
    given(() -> {});
    when(() -> dao.byKey(null));
  }

  @Test
  public void shouldPagedWithSearchAfterSizeAndSorted() throws Exception {
    given(() -> {
      deployProcesses(
          "demoProcess_v_1.bpmn", "errorProcess.bpmn", "complexProcess_v_3.bpmn",
          "error-end-event.bpmn","intermediate-throw-event.bpmn","message-end-event.bpmn");
      processInstanceKeys = startProcesses("demoProcess", "errorProcess", "complexProcess",
          "error-end-process", "intermediate-throw-event-process", "message-end-event-process");
    });

    when(() ->
        processInstanceResults = dao.search(new Query<ProcessInstance>()
            .setSize(3).setSearchAfter(new Object[]{"errorProcess", processInstanceKeys.get(1)})
            .setSort(Sort.listOf(BPMN_PROCESS_ID, Order.DESC)))
    );
    then(() -> {
      assertThat(processInstanceResults.getTotal()).isEqualTo(6);
      List<ProcessInstance> processDefinitions = processInstanceResults.getItems();
      assertThat(processDefinitions).hasSize(3);
      assertThat(processDefinitions).extracting(BPMN_PROCESS_ID)
          .containsExactly("error-end-process", "demoProcess", "complexProcess");
    });
  }

  @Test
  public void shouldPagedWithSearchAfterSizeAndSortedAsc() throws Exception {
    given(() -> {
      deployProcesses(
          "demoProcess_v_1.bpmn", "errorProcess.bpmn", "complexProcess_v_3.bpmn",
          "error-end-event.bpmn", "intermediate-throw-event.bpmn", "message-end-event.bpmn");
      processInstanceKeys = startProcesses("demoProcess", "errorProcess", "complexProcess",
          "error-end-process", "intermediate-throw-event-process", "message-end-event-process");
    });

    when(() ->
        processInstanceResults = dao.search(new Query<ProcessInstance>()
            .setSize(3).setSearchAfter(new Object[]{"errorProcess", processInstanceKeys.get(1)})
            .setSort(Sort.listOf(BPMN_PROCESS_ID, Order.ASC))));
    then(() -> {
      assertThat(processInstanceResults.getTotal()).isEqualTo(6);
      List<ProcessInstance> processDefinitions = processInstanceResults.getItems();
      assertThat(processDefinitions).hasSize(2);
      assertThat(processDefinitions).extracting(BPMN_PROCESS_ID)
          .containsExactly("intermediate-throw-event-process","message-end-event-process");
    });
  }

  @Test
  public void shouldFilteredByFieldsAndSortedDesc() throws Exception {
    given(() -> {
      deployProcesses(
          "demoProcess_v_1.bpmn", "demoProcess_v_2.bpmn", "complexProcess_v_3.bpmn",
          "error-end-event.bpmn","intermediate-throw-event.bpmn","message-end-event.bpmn");
      startProcesses("demoProcess", "demoProcess", "complexProcess",
          "error-end-process", "intermediate-throw-event-process", "message-end-event-process");
    });

    when(() -> {
      final ProcessInstance processDefinitionExample = new ProcessInstance()
          .setBpmnProcessId("demoProcess");
      processInstanceResults = dao.search(new Query<ProcessInstance>()
          .setFilter(processDefinitionExample)
          .setSort(Sort.listOf(VERSION, Order.DESC)));
    });
    then(() -> {
      assertThat(processInstanceResults.getTotal()).isEqualTo(2);
      List<ProcessInstance> processDefinitions = processInstanceResults.getItems();
      assertThat(processDefinitions).hasSize(2);
      assertThat(processDefinitions).extracting(BPMN_PROCESS_ID)
          .contains("demoProcess", "demoProcess");
    });
  }

  @Test
  public void shouldFilteredAndPagedAndSorted() throws Exception {
    given(() -> {
      deployProcesses(
          "demoProcess_v_1.bpmn", "errorProcess.bpmn", "complexProcess_v_3.bpmn",
          "error-end-event.bpmn","intermediate-throw-event.bpmn","message-end-event.bpmn");
      startProcesses("demoProcess", "errorProcess", "complexProcess",
          "error-end-process", "intermediate-throw-event-process", "message-end-event-process");
    });

    when(() -> {
      final ProcessInstance processDefinitionExample = new ProcessInstance()
          .setProcessVersion(1);
      processInstanceResults = dao.search(new Query<ProcessInstance>()
          .setFilter(processDefinitionExample)
          .setSize(2)
          .setSort(Sort.listOf(BPMN_PROCESS_ID, Order.DESC)));
    });
    then(() -> {
      assertThat(processInstanceResults.getTotal()).isEqualTo(6);
      List<ProcessInstance> processDefinitions = processInstanceResults.getItems();
      assertThat(processDefinitions).hasSize(2);
      assertThat(processDefinitions).extracting(BPMN_PROCESS_ID)
          .containsExactly("message-end-event-process", "intermediate-throw-event-process");
      assertThat(processDefinitions).extracting(VERSION)
          .contains(1,1);
    });
  }

  @Test
  public void shouldFilteredByDate() throws Exception {
    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(
        operateProperties.getElasticsearch().getDateFormat());
    // See https://www.elastic.co/guide/en/elasticsearch/reference/current/common-options.html#date-math
    String dateThatMatchesWithinDay = dateTimeFormatter.format(OffsetDateTime.now()) + "||/d";
    given(() -> {
      deployProcesses(
          "demoProcess_v_1.bpmn", "errorProcess.bpmn", "complexProcess_v_3.bpmn");
      startProcesses("demoProcess","errorProcess", "complexProcess");
    });
    when(() -> {
      final ProcessInstance processDefinitionExample = new ProcessInstance()
          .setStartDate(dateThatMatchesWithinDay);
      processInstanceResults = dao.search(new Query<ProcessInstance>()
          .setFilter(processDefinitionExample)
          .setSort(Sort.listOf(BPMN_PROCESS_ID, Order.DESC)));
    });
    then(() -> {
      assertThat(processInstanceResults.getTotal()).isEqualTo(3);
      List<ProcessInstance> processDefinitions = processInstanceResults.getItems();
      assertThat(processDefinitions).hasSize(3);
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
