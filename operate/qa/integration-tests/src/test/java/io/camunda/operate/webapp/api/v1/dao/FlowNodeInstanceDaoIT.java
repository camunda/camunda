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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import io.camunda.webapps.schema.entities.flownode.FlowNodeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.mock.mockito.MockBean;

public class FlowNodeInstanceDaoIT extends OperateSearchAbstractIT {

  private final String firstNodeStartDate = "2024-02-15T22:40:10.834+0000";
  private final String secondNodeStartDate = "2024-02-15T22:41:10.834+0000";
  private final String endDate = "2024-02-15T22:41:10.834+0000";
  @Autowired private FlowNodeInstanceDao dao;

  @Autowired
  @Qualifier("operateFlowNodeInstanceTemplate")
  private FlowNodeInstanceTemplate flowNodeInstanceIndex;

  @MockBean private ProcessCache processCache;
  @Autowired private OperateDateTimeFormatter dateTimeFormatter;

  @Override
  public void runAdditionalBeforeAllSetup() throws Exception {

    final String indexName = flowNodeInstanceIndex.getFullQualifiedName();
    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new FlowNodeInstanceEntity()
            .setKey(2251799813685256L)
            .setProcessInstanceKey(2251799813685253L)
            .setProcessDefinitionKey(2251799813685249L)
            .setStartDate(dateTimeFormatter.parseGeneralDateTime(firstNodeStartDate))
            .setEndDate(dateTimeFormatter.parseGeneralDateTime(endDate))
            .setFlowNodeId("start")
            .setType(FlowNodeType.START_EVENT)
            .setState(FlowNodeState.COMPLETED)
            .setIncident(false)
            .setTenantId(DEFAULT_TENANT_ID));

    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new FlowNodeInstanceEntity()
            .setKey(2251799813685258L)
            .setProcessInstanceKey(2251799813685253L)
            .setProcessDefinitionKey(2251799813685249L)
            .setStartDate(dateTimeFormatter.parseGeneralDateTime(secondNodeStartDate))
            .setEndDate(null)
            .setFlowNodeId("taskA")
            .setType(FlowNodeType.SERVICE_TASK)
            .setIncidentKey(2251799813685264L)
            .setState(FlowNodeState.ACTIVE)
            .setIncident(true)
            .setTenantId(DEFAULT_TENANT_ID));
    searchContainerManager.refreshIndices("*operate-flow*");
  }

  @Override
  public void runAdditionalBeforeEachSetup() {
    when(processCache.getFlowNodeNameOrDefaultValue(any(), eq("start"), eq(null)))
        .thenReturn("start");
    when(processCache.getFlowNodeNameOrDefaultValue(any(), eq("taskA"), eq(null)))
        .thenReturn("task A");
  }

  @Test
  public void shouldReturnFlowNodeInstances() {
    final Results<FlowNodeInstance> flowNodeInstanceResults = dao.search(new Query<>());

    assertThat(flowNodeInstanceResults.getItems()).hasSize(2);

    FlowNodeInstance checkFlowNode =
        flowNodeInstanceResults.getItems().stream()
            .filter(item -> "START_EVENT".equals(item.getType()))
            .findFirst()
            .orElse(null);
    assertThat(checkFlowNode)
        .extracting("flowNodeId", "flowNodeName")
        .containsExactly("start", "start");

    checkFlowNode =
        flowNodeInstanceResults.getItems().stream()
            .filter(
                item ->
                    "SERVICE_TASK".equals(item.getType()) && "taskA".equals(item.getFlowNodeId()))
            .findFirst()
            .orElse(null);
    assertThat(checkFlowNode)
        .extracting("flowNodeId", "flowNodeName")
        .containsExactly("taskA", "task A");
  }

  @Test
  public void shouldFilterFlowNodeInstances() {
    final Results<FlowNodeInstance> flowNodeInstanceResults =
        dao.search(
            new Query<FlowNodeInstance>().setFilter(new FlowNodeInstance().setType("START_EVENT")));

    assertThat(flowNodeInstanceResults.getItems()).hasSize(1);

    assertThat(flowNodeInstanceResults.getItems().get(0))
        .extracting("flowNodeId", "flowNodeName")
        .containsExactly("start", "start");
  }

  @Test
  public void shouldSortFlowNodeInstancesAsc() {
    final Results<FlowNodeInstance> flowNodeInstanceResults =
        dao.search(
            new Query<FlowNodeInstance>()
                .setSort(Query.Sort.listOf(FlowNodeInstance.FLOW_NODE_ID, Query.Sort.Order.ASC)));

    assertThat(flowNodeInstanceResults.getItems()).hasSize(2);

    assertThat(flowNodeInstanceResults.getItems().get(0))
        .extracting("flowNodeId", "flowNodeName")
        .containsExactly("start", "start");

    assertThat(flowNodeInstanceResults.getItems().get(1))
        .extracting("flowNodeId", "flowNodeName")
        .containsExactly("taskA", "task A");
  }

  @Test
  public void shouldSortFlowNodeInstancesDesc() {
    final Results<FlowNodeInstance> flowNodeInstanceResults =
        dao.search(
            new Query<FlowNodeInstance>()
                .setSort(Query.Sort.listOf(FlowNodeInstance.FLOW_NODE_ID, Query.Sort.Order.DESC)));

    assertThat(flowNodeInstanceResults.getItems()).hasSize(2);
    assertThat(flowNodeInstanceResults.getItems().get(0))
        .extracting("flowNodeId", "flowNodeName")
        .containsExactly("taskA", "task A");

    assertThat(flowNodeInstanceResults.getItems().get(1))
        .extracting("flowNodeId", "flowNodeName")
        .containsExactly("start", "start");
  }

  @Test
  public void shouldPageFlowNodeInstances() {
    Results<FlowNodeInstance> flowNodeInstanceResults =
        dao.search(
            new Query<FlowNodeInstance>()
                .setSize(1)
                .setSort(Query.Sort.listOf(FlowNodeInstance.FLOW_NODE_ID, Query.Sort.Order.ASC)));

    assertThat(flowNodeInstanceResults.getTotal()).isEqualTo(2);
    assertThat(flowNodeInstanceResults.getItems()).hasSize(1);

    assertThat(flowNodeInstanceResults.getItems().get(0))
        .extracting("flowNodeId", "flowNodeName")
        .containsExactly("start", "start");

    final Object[] searchAfter = flowNodeInstanceResults.getSortValues();

    flowNodeInstanceResults =
        dao.search(
            new Query<FlowNodeInstance>()
                .setSize(3)
                .setSort(Query.Sort.listOf(FlowNodeInstance.FLOW_NODE_ID, Query.Sort.Order.ASC))
                .setSearchAfter(searchAfter));
    assertThat(flowNodeInstanceResults.getTotal()).isEqualTo(2);
    assertThat(flowNodeInstanceResults.getItems()).hasSize(1);

    assertThat(flowNodeInstanceResults.getItems().get(0))
        .extracting("flowNodeId", "flowNodeName")
        .containsExactly("taskA", "task A");
  }

  @Test
  public void shouldReturnByKey() {
    final FlowNodeInstance flowNodeInstance = dao.byKey(2251799813685256L);

    assertThat(flowNodeInstance)
        .extracting("flowNodeId", "flowNodeName", "startDate", "endDate")
        .containsExactly("start", "start", firstNodeStartDate, endDate);
  }

  @Test
  public void shouldThrowWhenKeyNotExists() {
    assertThrows(ResourceNotFoundException.class, () -> dao.byKey(1L));
  }

  @Test
  public void shouldFilterByStartDate() {
    final Results<FlowNodeInstance> flowNodeInstanceResults =
        dao.search(
            new Query<FlowNodeInstance>()
                .setFilter(new FlowNodeInstance().setStartDate(firstNodeStartDate)));

    assertThat(flowNodeInstanceResults.getTotal()).isEqualTo(1L);
    assertThat(flowNodeInstanceResults.getItems().get(0).getFlowNodeId()).isEqualTo("start");
    assertThat(flowNodeInstanceResults.getItems().get(0).getStartDate())
        .isEqualTo(firstNodeStartDate);
    assertThat(flowNodeInstanceResults.getItems().get(0).getEndDate()).isEqualTo(endDate);
  }

  @Test
  public void shouldFilterByStartDateWithDateMath() {
    final Results<FlowNodeInstance> flowNodeInstanceResults =
        dao.search(
            new Query<FlowNodeInstance>()
                .setFilter(new FlowNodeInstance().setStartDate(firstNodeStartDate + "||/d")));

    assertThat(flowNodeInstanceResults.getTotal()).isEqualTo(2L);

    FlowNodeInstance checkFlowNode =
        flowNodeInstanceResults.getItems().stream()
            .filter(item -> "START_EVENT".equals(item.getType()))
            .findFirst()
            .orElse(null);
    assertThat(checkFlowNode)
        .extracting("flowNodeId", "flowNodeName", "startDate", "endDate")
        .containsExactly("start", "start", firstNodeStartDate, endDate);

    checkFlowNode =
        flowNodeInstanceResults.getItems().stream()
            .filter(item -> "SERVICE_TASK".equals(item.getType()))
            .findFirst()
            .orElse(null);
    assertThat(checkFlowNode)
        .extracting("flowNodeId", "flowNodeName", "startDate", "endDate")
        .containsExactly("taskA", "task A", secondNodeStartDate, null);
  }

  @Test
  public void shouldFilterByEndDate() {
    final Results<FlowNodeInstance> flowNodeInstanceResults =
        dao.search(
            new Query<FlowNodeInstance>().setFilter(new FlowNodeInstance().setEndDate(endDate)));

    assertThat(flowNodeInstanceResults.getTotal()).isEqualTo(1L);
    assertThat(flowNodeInstanceResults.getItems().get(0).getFlowNodeId()).isEqualTo("start");
    assertThat(flowNodeInstanceResults.getItems().get(0).getStartDate())
        .isEqualTo(firstNodeStartDate);
    assertThat(flowNodeInstanceResults.getItems().get(0).getEndDate()).isEqualTo(endDate);
  }

  @Test
  public void shouldFilterByEndDateWithDateMath() {
    final Results<FlowNodeInstance> flowNodeInstanceResults =
        dao.search(
            new Query<FlowNodeInstance>()
                .setFilter(new FlowNodeInstance().setEndDate(endDate + "||/d")));

    assertThat(flowNodeInstanceResults.getTotal()).isEqualTo(1L);
    assertThat(flowNodeInstanceResults.getItems().get(0).getFlowNodeId()).isEqualTo("start");
    assertThat(flowNodeInstanceResults.getItems().get(0).getStartDate())
        .isEqualTo(firstNodeStartDate);
    assertThat(flowNodeInstanceResults.getItems().get(0).getEndDate()).isEqualTo(endDate);
  }
}
