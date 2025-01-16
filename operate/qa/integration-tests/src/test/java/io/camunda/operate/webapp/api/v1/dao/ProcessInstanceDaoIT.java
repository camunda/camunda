/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao;

import static io.camunda.operate.webapp.api.v1.entities.ProcessInstance.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.entities.AbstractExporterEntity.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.ProcessInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.webapps.schema.descriptors.operate.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.OperationTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.SequenceFlowTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.VariableTemplate;
import io.camunda.webapps.schema.entities.listview.ListViewJoinRelation;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import io.camunda.webapps.schema.entities.operate.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.operate.SequenceFlowEntity;
import io.camunda.webapps.schema.entities.operate.VariableEntity;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ProcessInstanceDaoIT extends OperateSearchAbstractIT {
  private final String firstInstanceStartDate = "2024-02-15T22:40:10.834+0000";
  private final String secondInstanceStartDate = "2024-02-15T22:41:10.834+0000";
  private final String endDate = "2024-02-15T22:42:10.834+0000";

  @Autowired private ProcessInstanceDao dao;
  @Autowired private ListViewTemplate processInstanceIndex;

  @Autowired private OperateDateTimeFormatter dateTimeFormatter;
  @Autowired private List<ProcessInstanceDependant> processInstanceDependants;

  @Override
  public void runAdditionalBeforeAllSetup() throws Exception {
    ProcessInstanceForListViewEntity processInstance =
        new ProcessInstanceForListViewEntity()
            .setId("2251799813685251")
            .setKey(2251799813685251L)
            .setPartitionId(1)
            .setProcessDefinitionKey(2251799813685249L)
            .setProcessName("Demo process")
            .setProcessVersion(1)
            .setProcessVersionTag("tag-v1")
            .setBpmnProcessId("demoProcess-1")
            .setStartDate(dateTimeFormatter.parseGeneralDateTime(firstInstanceStartDate))
            .setEndDate(dateTimeFormatter.parseGeneralDateTime(endDate))
            .setState(ProcessInstanceState.ACTIVE)
            .setTreePath("PI_2251799813685251")
            .setIncident(true)
            .setTenantId(DEFAULT_TENANT_ID)
            .setProcessInstanceKey(2251799813685251L)
            .setJoinRelation(new ListViewJoinRelation("processInstance"));

    testSearchRepository.createOrUpdateDocumentFromObject(
        processInstanceIndex.getFullQualifiedName(), processInstance.getId(), processInstance);

    processInstance =
        new ProcessInstanceForListViewEntity()
            .setId("2251799813685252")
            .setKey(2251799813685252L)
            .setPartitionId(1)
            .setProcessDefinitionKey(2251799813685249L)
            .setProcessName("Demo process")
            .setProcessVersion(1)
            .setBpmnProcessId("demoProcess-2")
            .setStartDate(dateTimeFormatter.parseGeneralDateTime(secondInstanceStartDate))
            .setEndDate(null)
            .setState(ProcessInstanceState.ACTIVE)
            .setTreePath("PI_2251799813685252")
            .setIncident(false)
            .setParentProcessInstanceKey(2251799813685251L)
            .setParentFlowNodeInstanceKey(3251799813685251L)
            .setTenantId(DEFAULT_TENANT_ID)
            .setProcessInstanceKey(2251799813685252L)
            .setJoinRelation(new ListViewJoinRelation("processInstance"));

    testSearchRepository.createOrUpdateDocumentFromObject(
        processInstanceIndex.getFullQualifiedName(), processInstance.getId(), processInstance);

    searchContainerManager.refreshIndices("*operate*");
  }

  @Test
  public void shouldReturnProcessInstancesOnSearch() {
    final Results<ProcessInstance> processInstanceResults = dao.search(new Query<>());

    assertThat(processInstanceResults.getTotal()).isEqualTo(2);
    assertThat(processInstanceResults.getItems())
        .extracting(BPMN_PROCESS_ID)
        .containsExactlyInAnyOrder("demoProcess-1", "demoProcess-2");
  }

  @Test
  public void searchShouldReturnParentKeyWhenExists() {
    // Find the child process
    final Results<ProcessInstance> processInstanceResults =
        dao.search(
            new Query<ProcessInstance>()
                .setFilter(new ProcessInstance().setBpmnProcessId("demoProcess-2")));
    assertThat(processInstanceResults.getItems().size()).isEqualTo(1);
    final ProcessInstance instance = processInstanceResults.getItems().get(0);

    assertThat(instance.getParentKey()).isEqualTo(2251799813685251L);
    assertThat(instance.getParentFlowNodeInstanceKey()).isEqualTo(3251799813685251L);
  }

  @Test
  public void shouldThrowForDeleteWhenKeyNotExists() {
    assertThrows(ResourceNotFoundException.class, () -> dao.delete(1L));
  }

  @Test
  public void shouldReturnProcessInstanceByKey() {
    final ProcessInstance result = dao.byKey(2251799813685251L);
    assertThat(result.getKey()).isEqualTo(2251799813685251L);
    assertThat(result.getBpmnProcessId()).isEqualTo("demoProcess-1");
    assertThat(result.getProcessVersionTag()).isEqualTo("tag-v1");
    assertThat(result.getStartDate()).isEqualTo(firstInstanceStartDate);
    assertThat(result.getEndDate()).isEqualTo(endDate);
  }

  @Test
  public void shouldThrowWhenByKeyNotExists() {
    assertThrows(ResourceNotFoundException.class, () -> dao.byKey(1L));
  }

  @Test
  public void shouldSortInstancesAsc() {
    final Results<ProcessInstance> results =
        dao.search(
            new Query<ProcessInstance>()
                .setSort(Query.Sort.listOf(BPMN_PROCESS_ID, Query.Sort.Order.ASC)));

    assertThat(results.getItems()).hasSize(2);
    assertThat(results.getItems())
        .extracting(BPMN_PROCESS_ID)
        .containsExactly("demoProcess-1", "demoProcess-2");
  }

  @Test
  public void shouldSortInstancesDesc() {
    final Results<ProcessInstance> results =
        dao.search(
            new Query<ProcessInstance>()
                .setSort(Query.Sort.listOf(BPMN_PROCESS_ID, Query.Sort.Order.DESC)));

    assertThat(results.getItems()).hasSize(2);
    assertThat(results.getItems())
        .extracting(BPMN_PROCESS_ID)
        .containsExactly("demoProcess-2", "demoProcess-1");
  }

  @Test
  public void shouldPageInstances() {
    // First page
    Results<ProcessInstance> results =
        dao.search(
            new Query<ProcessInstance>()
                .setSize(1)
                .setSort(Query.Sort.listOf(BPMN_PROCESS_ID, Query.Sort.Order.ASC)));

    assertThat(results.getTotal()).isEqualTo(2);
    assertThat(results.getItems().size()).isEqualTo(1);
    assertThat(results.getItems().get(0).getBpmnProcessId()).isEqualTo("demoProcess-1");

    // Second page
    results =
        dao.search(
            new Query<ProcessInstance>()
                .setSize(1)
                .setSort(Query.Sort.listOf(BPMN_PROCESS_ID, Query.Sort.Order.ASC))
                .setSearchAfter(results.getSortValues()));

    assertThat(results.getTotal()).isEqualTo(2);
    assertThat(results.getItems().size()).isEqualTo(1);
    assertThat(results.getItems().get(0).getBpmnProcessId()).isEqualTo("demoProcess-2");
  }

  @Test
  public void shouldFilterByParentFlowNodeInstanceKey() {
    // Should return the same process when searching by the parent flow node instance key
    final var results =
        dao.search(
            new Query<ProcessInstance>()
                .setFilter(new ProcessInstance().setParentFlowNodeInstanceKey(3251799813685251L)));

    assertThat(results.getItems().size()).isEqualTo(1);
    assertThat(results.getItems().get(0).getBpmnProcessId()).isEqualTo("demoProcess-2");
  }

  @Test
  public void shouldFilterByIncident() {

    final Results<ProcessInstance> resultsWithIncident =
        dao.search(new Query<ProcessInstance>().setFilter(new ProcessInstance().setIncident(true)));

    final Results<ProcessInstance> resultsWithoutIncident =
        dao.search(
            new Query<ProcessInstance>().setFilter(new ProcessInstance().setIncident(false)));

    assertThat(resultsWithIncident.getItems()).hasSize(1);
    assertThat(resultsWithIncident.getItems().get(0).getBpmnProcessId()).isEqualTo("demoProcess-1");
    assertThat(resultsWithoutIncident.getItems()).hasSize(1);
    assertThat(resultsWithoutIncident.getItems().get(0).getBpmnProcessId())
        .isEqualTo("demoProcess-2");
  }

  @Test
  public void shouldFilterByStartDate() {
    final Results<ProcessInstance> processInstanceResults =
        dao.search(
            new Query<ProcessInstance>()
                .setFilter(new ProcessInstance().setStartDate(firstInstanceStartDate)));

    assertThat(processInstanceResults.getTotal()).isEqualTo(1L);
    assertThat(processInstanceResults.getItems().get(0).getStartDate())
        .isEqualTo(firstInstanceStartDate);
    assertThat(processInstanceResults.getItems().get(0).getEndDate()).isEqualTo(endDate);
    assertThat(processInstanceResults.getItems().get(0).getBpmnProcessId())
        .isEqualTo("demoProcess-1");
  }

  @Test
  public void shouldFilterByStartDateWithDateMath() {
    final Results<ProcessInstance> processInstanceResults =
        dao.search(
            new Query<ProcessInstance>()
                .setFilter(new ProcessInstance().setStartDate(firstInstanceStartDate + "||/d")));

    assertThat(processInstanceResults.getTotal()).isEqualTo(2L);

    ProcessInstance checkInstance =
        processInstanceResults.getItems().stream()
            .filter(item -> "demoProcess-1".equals(item.getBpmnProcessId()))
            .findFirst()
            .orElse(null);

    assertThat(checkInstance.getBpmnProcessId()).isEqualTo("demoProcess-1");
    assertThat(checkInstance.getStartDate()).isEqualTo(firstInstanceStartDate);
    assertThat(checkInstance.getEndDate()).isEqualTo(endDate);

    checkInstance =
        processInstanceResults.getItems().stream()
            .filter(item -> "demoProcess-2".equals(item.getBpmnProcessId()))
            .findFirst()
            .orElse(null);

    assertThat(checkInstance.getBpmnProcessId()).isEqualTo("demoProcess-2");
    assertThat(checkInstance.getStartDate()).isEqualTo(secondInstanceStartDate);
    assertThat(checkInstance.getEndDate()).isNull();
  }

  @Test
  public void shouldDeleteByKey() throws Exception {
    final Long processInstanceKey = 4503599627370497L;
    final ProcessInstanceForListViewEntity processInstance =
        new ProcessInstanceForListViewEntity()
            .setId(String.valueOf(processInstanceKey))
            .setKey(processInstanceKey)
            .setProcessDefinitionKey(2251799813685248L)
            .setProcessInstanceKey(4503599627370497L)
            .setProcessName("Demo process")
            .setBpmnProcessId("demoProcess")
            .setState(ProcessInstanceState.COMPLETED)
            .setStartDate(OffsetDateTime.now())
            .setEndDate(OffsetDateTime.now())
            .setTreePath("PI_4503599627370497")
            .setTenantId(DEFAULT_TENANT_ID)
            .setJoinRelation(new ListViewJoinRelation("processInstance"));
    testSearchRepository.createOrUpdateDocumentFromObject(
        processInstanceIndex.getFullQualifiedName(), processInstance.getId(), processInstance);

    testSearchRepository.createOrUpdateDocumentFromObject(
        getFullIndexNameForDependant(FlowNodeInstanceTemplate.INDEX_NAME),
        new FlowNodeInstanceEntity().setProcessInstanceKey(processInstanceKey));
    testSearchRepository.createOrUpdateDocumentFromObject(
        getFullIndexNameForDependant(SequenceFlowTemplate.INDEX_NAME),
        new SequenceFlowEntity().setProcessInstanceKey(processInstanceKey));
    testSearchRepository.createOrUpdateDocumentFromObject(
        getFullIndexNameForDependant(VariableTemplate.INDEX_NAME),
        new VariableEntity().setProcessInstanceKey(processInstanceKey));

    searchContainerManager.refreshIndices("*operate*");

    dao.delete(processInstance.getProcessInstanceKey());

    searchContainerManager.refreshIndices("*operate*");

    Assertions.assertThrows(
        ResourceNotFoundException.class, () -> dao.byKey(processInstance.getProcessInstanceKey()));
    assertThatDependantsAreAlsoDeleted(processInstanceKey);
  }

  private String getFullIndexNameForDependant(final String indexName) {
    final ProcessInstanceDependant dependant =
        processInstanceDependants.stream()
            .filter(template -> template.getFullQualifiedName().contains(indexName))
            .findAny()
            .orElse(null);

    return dependant.getFullQualifiedName();
  }

  private void assertThatDependantsAreAlsoDeleted(final long finishedProcessInstanceKey)
      throws IOException {
    for (final ProcessInstanceDependant t : processInstanceDependants) {
      if (!(t instanceof OperationTemplate)) {
        final var index = t.getFullQualifiedName() + "*";
        final var field = ProcessInstanceDependant.PROCESS_INSTANCE_KEY;
        final var response =
            testSearchRepository.searchTerm(
                index, field, finishedProcessInstanceKey, Object.class, 100);
        AssertionsForClassTypes.assertThat(response.size()).isZero();
      }
    }
  }
}
