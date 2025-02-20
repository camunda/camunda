/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.IncidentState;
import io.camunda.operate.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.entities.listview.VariableForListViewEntity;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.reader.ListViewReader;
import io.camunda.operate.webapp.rest.dto.SortingDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.VariablesQueryDto;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class ListViewReaderIT extends OperateSearchAbstractIT {
  @Autowired private ListViewReader listViewReader;
  @Autowired private ListViewTemplate listViewTemplate;
  @Autowired private IncidentTemplate incidentTemplate;
  @MockBean private PermissionsService permissionsService;

  private ProcessInstanceForListViewEntity activeProcess;
  private ProcessInstanceForListViewEntity completedProcess;
  private ProcessInstanceForListViewEntity incidentProcess;
  private IncidentEntity incident;

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    activeProcess =
        new ProcessInstanceForListViewEntity()
            .setProcessName("process1")
            .setBpmnProcessId("p1")
            .setState(ProcessInstanceState.ACTIVE)
            .setProcessDefinitionKey(300L)
            .setStartDate(
                OffsetDateTime.of(2018, 2, 1, 12, 0, 30, 457, OffsetDateTime.now().getOffset()))
            .setProcessInstanceKey(100L)
            .setBatchOperationIds(List.of("b1, b2"))
            .setTenantId("tenant1")
            .setId("100");

    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(), activeProcess.getId(), activeProcess);

    final VariableForListViewEntity variableEntity =
        new VariableForListViewEntity()
            .setProcessInstanceKey(activeProcess.getProcessInstanceKey())
            .setScopeKey(activeProcess.getProcessInstanceKey())
            .setVarName("p1v1")
            .setVarValue("Y")
            .setTenantId("tenant1")
            .setId(
                VariableForListViewEntity.getIdBy(activeProcess.getProcessInstanceKey(), "p1v1"));

    variableEntity.getJoinRelation().setParent(activeProcess.getProcessInstanceKey());
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        variableEntity.getId(),
        variableEntity,
        variableEntity.getProcessInstanceKey().toString());

    final FlowNodeInstanceForListViewEntity flowNodeEntity =
        new FlowNodeInstanceForListViewEntity()
            .setProcessInstanceKey(activeProcess.getProcessInstanceKey())
            .setActivityState(FlowNodeState.ACTIVE)
            .setActivityId("start")
            .setId("1000")
            .setKey(1000L)
            .setTenantId("tenant1")
            .setErrorMessage("Some error message")
            .setJobFailedWithRetriesLeft(true);
    flowNodeEntity.getJoinRelation().setParent(activeProcess.getProcessInstanceKey());

    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        flowNodeEntity.getId(),
        flowNodeEntity,
        variableEntity.getProcessInstanceKey().toString());

    completedProcess =
        new ProcessInstanceForListViewEntity()
            .setProcessName("process2")
            .setState(ProcessInstanceState.COMPLETED)
            .setBpmnProcessId("p2")
            .setProcessDefinitionKey(400L)
            .setStartDate(
                OffsetDateTime.of(2018, 3, 1, 17, 15, 14, 235, OffsetDateTime.now().getOffset()))
            .setEndDate(
                OffsetDateTime.of(2018, 3, 1, 20, 15, 14, 235, OffsetDateTime.now().getOffset()))
            .setProcessInstanceKey(101L)
            .setBatchOperationIds(List.of("b3"))
            .setParentProcessInstanceKey(50L)
            .setTenantId("tenant2")
            .setId("101");

    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(), completedProcess.getId(), completedProcess);

    incidentProcess =
        new ProcessInstanceForListViewEntity()
            .setProcessName("process3")
            .setBpmnProcessId("p3")
            .setState(ProcessInstanceState.ACTIVE)
            .setProcessDefinitionKey(123L)
            .setStartDate(
                OffsetDateTime.of(2025, 2, 1, 12, 0, 30, 457, OffsetDateTime.now().getOffset()))
            .setProcessInstanceKey(333L)
            .setTenantId("tenant3")
            .setIncident(true)
            .setKey(333L)
            .setId("333");

    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(), incidentProcess.getId(), incidentProcess);

    incident =
        new IncidentEntity()
            .setErrorMessage("Another error")
            .setErrorMessageHash("Another error".hashCode())
            .setProcessInstanceKey(incidentProcess.getProcessInstanceKey())
            .setProcessDefinitionKey(incidentProcess.getProcessDefinitionKey())
            .setBpmnProcessId(incidentProcess.getBpmnProcessId())
            .setState(IncidentState.ACTIVE)
            .setTenantId("tenant3")
            .setId("555");

    testSearchRepository.createOrUpdateDocumentFromObject(
        incidentTemplate.getFullQualifiedName(), incident.getId(), incident);

    final FlowNodeInstanceForListViewEntity incidentFlowNodeEntity =
        new FlowNodeInstanceForListViewEntity()
            .setProcessInstanceKey(incidentProcess.getProcessInstanceKey())
            .setActivityState(FlowNodeState.ACTIVE)
            .setActivityId("serviceTask")
            .setId("2000")
            .setKey(2000L)
            .setTenantId("tenant3")
            .setErrorMessage(incident.getErrorMessage())
            .setJobFailedWithRetriesLeft(false);
    incidentFlowNodeEntity.getJoinRelation().setParent(incidentProcess.getProcessInstanceKey());

    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        incidentFlowNodeEntity.getId(),
        incidentFlowNodeEntity,
        incidentProcess.getProcessInstanceKey().toString());

    searchContainerManager.refreshIndices("*incident*");

    searchContainerManager.refreshIndices("*list-view*");
  }

  @Override
  protected void runAdditionalBeforeEachSetup() throws Exception {
    Mockito.reset(permissionsService);
  }

  @Test
  public void testQueryProcessInstancesAllRunning() {
    final ListViewRequestDto requestDto = createSimpleProcessInstanceQuery();
    requestDto.getQuery().setCanceled(false).setCompleted(false).setFinished(false);

    final var response = listViewReader.queryProcessInstances(requestDto);
    assertThat(response.getTotalCount()).isEqualTo(2);
    assertThat(response.getProcessInstances()).hasSize(2);
    validateResultAgainstProcess(response.getProcessInstances().getFirst(), activeProcess);
  }

  @Test
  public void testQueryProcessInstancesAll() {
    final ListViewRequestDto requestDto = createSimpleProcessInstanceQuery();

    final var response = listViewReader.queryProcessInstances(requestDto);
    assertThat(response.getTotalCount()).isEqualTo(3);
    assertThat(response.getProcessInstances()).hasSize(3);
  }

  @Test
  public void testQueryProcessInstancesNone() {
    // Setting running and finished to false creates a matchNone query regardless of other values
    final ListViewRequestDto requestDto = createSimpleProcessInstanceQuery();
    requestDto.getQuery().setRunning(false).setFinished(false);

    final var response = listViewReader.queryProcessInstances(requestDto);
    assertThat(response.getTotalCount()).isEqualTo(0);
    assertThat(response.getProcessInstances()).isEmpty();
  }

  @Test
  public void testQueryProcessInstancesNoEndDate() {
    // Setting running + active (but not incidents) should return only processes without an
    // end date. Even though completed is set to true, the completed process should not be returned
    // since it has an end date value
    final ListViewRequestDto requestDto = createSimpleProcessInstanceQuery();
    requestDto.getQuery().setIncidents(false).setFinished(false).setCanceled(false);

    final var response = listViewReader.queryProcessInstances(requestDto);
    assertThat(response.getTotalCount()).isEqualTo(1);
    assertThat(response.getProcessInstances()).hasSize(1);
    validateResultAgainstProcess(response.getProcessInstances().getFirst(), activeProcess);
  }

  @Test
  public void testQueryProcessInstancesAllFinished() {
    final ListViewRequestDto requestDto = createSimpleProcessInstanceQuery();
    requestDto.getQuery().setRunning(false).setActive(false).setIncidents(false);

    final var response = listViewReader.queryProcessInstances(requestDto);
    assertThat(response.getTotalCount()).isEqualTo(1);
    assertThat(response.getProcessInstances()).hasSize(1);
    validateResultAgainstProcess(response.getProcessInstances().getFirst(), completedProcess);
  }

  @Test
  public void testQueryProcessInstancesByRetriesLeft() {
    final ListViewRequestDto requestDto = createSimpleProcessInstanceQuery();
    requestDto.getQuery().setRetriesLeft(true);

    final var response = listViewReader.queryProcessInstances(requestDto);
    assertThat(response.getTotalCount()).isEqualTo(1);
    assertThat(response.getProcessInstances()).hasSize(1);
    validateResultAgainstProcess(response.getProcessInstances().getFirst(), activeProcess);
  }

  @Test
  public void testQueryProcessInstancesByActivityId() {
    final ListViewRequestDto requestDto = createSimpleProcessInstanceQuery();
    requestDto.getQuery().setActivityId("start");

    final var response = listViewReader.queryProcessInstances(requestDto);
    assertThat(response.getTotalCount()).isEqualTo(1);
    assertThat(response.getProcessInstances()).hasSize(1);
    validateResultAgainstProcess(response.getProcessInstances().getFirst(), activeProcess);
  }

  @Test
  public void testQueryProcessInstancesById() {
    final ListViewRequestDto requestDto = createSimpleProcessInstanceQuery();
    requestDto.getQuery().setIds(List.of("101", "notexist"));

    final var response = listViewReader.queryProcessInstances(requestDto);
    assertThat(response.getTotalCount()).isEqualTo(1);
    assertThat(response.getProcessInstances()).hasSize(1);
    validateResultAgainstProcess(response.getProcessInstances().getFirst(), completedProcess);
  }

  @Test
  public void testQueryProcessInstancesByErrorMessage() {
    final ListViewRequestDto requestDto = createSimpleProcessInstanceQuery();
    requestDto.getQuery().setErrorMessage("Some error message");

    final var response = listViewReader.queryProcessInstances(requestDto);
    assertThat(response.getTotalCount()).isEqualTo(1);
    assertThat(response.getProcessInstances()).hasSize(1);
    validateResultAgainstProcess(response.getProcessInstances().getFirst(), activeProcess);
  }

  @Test
  public void testQueryProcessInstancesByIncidentErrorHashCode() {
    final ListViewRequestDto requestDto = createSimpleProcessInstanceQuery();
    requestDto.getQuery().setIncidentErrorHashCode(incident.getErrorMessageHash());

    final var response = listViewReader.queryProcessInstances(requestDto);
    assertThat(response.getTotalCount()).isEqualTo(1);
    assertThat(response.getProcessInstances()).hasSize(1);
    validateResultAgainstProcess(response.getProcessInstances().getFirst(), incidentProcess);
  }

  @Test
  public void testQueryProcessInstancesByBeforeStartDate() {
    final ListViewRequestDto requestDto = createSimpleProcessInstanceQuery();
    requestDto
        .getQuery()
        .setStartDateBefore(
            OffsetDateTime.of(2018, 2, 15, 12, 0, 30, 457, OffsetDateTime.now().getOffset()));

    final var response = listViewReader.queryProcessInstances(requestDto);
    assertThat(response.getTotalCount()).isEqualTo(1);
    assertThat(response.getProcessInstances()).hasSize(1);
    validateResultAgainstProcess(response.getProcessInstances().getFirst(), activeProcess);
  }

  @Test
  public void testQueryProcessInstancesByAfterStartDate() {
    final ListViewRequestDto requestDto = createSimpleProcessInstanceQuery();
    requestDto
        .getQuery()
        .setStartDateAfter(
            OffsetDateTime.of(2018, 2, 15, 12, 0, 30, 457, OffsetDateTime.now().getOffset()));

    final var response = listViewReader.queryProcessInstances(requestDto);
    assertThat(response.getTotalCount()).isEqualTo(2);
    assertThat(response.getProcessInstances()).hasSize(2);
    validateResultAgainstProcess(response.getProcessInstances().getFirst(), completedProcess);
  }

  @Test
  public void testQueryProcessInstancesByBeforeEndDate() {
    final ListViewRequestDto requestDto = createSimpleProcessInstanceQuery();
    requestDto
        .getQuery()
        .setEndDateBefore(
            OffsetDateTime.of(2018, 3, 2, 20, 15, 14, 235, OffsetDateTime.now().getOffset()));

    final var response = listViewReader.queryProcessInstances(requestDto);
    assertThat(response.getTotalCount()).isEqualTo(1);
    assertThat(response.getProcessInstances()).hasSize(1);
    validateResultAgainstProcess(response.getProcessInstances().getFirst(), completedProcess);
  }

  @Test
  public void testQueryProcessInstancesByAfterEndDate() {
    final ListViewRequestDto requestDto = createSimpleProcessInstanceQuery();
    requestDto
        .getQuery()
        .setEndDateAfter(
            OffsetDateTime.of(2018, 2, 2, 20, 15, 14, 235, OffsetDateTime.now().getOffset()));

    final var response = listViewReader.queryProcessInstances(requestDto);
    assertThat(response.getTotalCount()).isEqualTo(1);
    assertThat(response.getProcessInstances()).hasSize(1);
    validateResultAgainstProcess(response.getProcessInstances().getFirst(), completedProcess);
  }

  @Test
  public void testQueryProcessInstancesByProcessDefinition() {
    final ListViewRequestDto requestDto = createSimpleProcessInstanceQuery();
    requestDto.getQuery().setProcessIds(List.of("300", "301"));

    final var response = listViewReader.queryProcessInstances(requestDto);
    assertThat(response.getTotalCount()).isEqualTo(1);
    assertThat(response.getProcessInstances()).hasSize(1);
    validateResultAgainstProcess(response.getProcessInstances().getFirst(), activeProcess);
  }

  @Test
  public void testQueryProcessInstancesByBpmnId() {
    final ListViewRequestDto requestDto = createSimpleProcessInstanceQuery();
    requestDto.getQuery().setBpmnProcessId("p2");

    final var response = listViewReader.queryProcessInstances(requestDto);
    assertThat(response.getTotalCount()).isEqualTo(1);
    assertThat(response.getProcessInstances()).hasSize(1);
    validateResultAgainstProcess(response.getProcessInstances().getFirst(), completedProcess);
  }

  @Test
  public void testQueryProcessInstancesByExcludeIds() {
    final ListViewRequestDto requestDto = createSimpleProcessInstanceQuery();
    requestDto.getQuery().setExcludeIds(List.of("101"));

    final var response = listViewReader.queryProcessInstances(requestDto);
    assertThat(response.getTotalCount()).isEqualTo(2);
    assertThat(response.getProcessInstances()).hasSize(2);
    validateResultAgainstProcess(response.getProcessInstances().getFirst(), activeProcess);
  }

  @Test
  public void testQueryProcessInstancesByVariable() {
    final ListViewRequestDto requestDto = createSimpleProcessInstanceQuery();
    requestDto.getQuery().setVariable(new VariablesQueryDto().setName("p1v1").setValue("Y"));

    final var response = listViewReader.queryProcessInstances(requestDto);
    assertThat(response.getTotalCount()).isEqualTo(1);
    assertThat(response.getProcessInstances()).hasSize(1);
    validateResultAgainstProcess(response.getProcessInstances().getFirst(), activeProcess);
  }

  @Test
  public void testQueryProcessInstancesByVariableValues() {
    final ListViewRequestDto requestDto = createSimpleProcessInstanceQuery();
    requestDto
        .getQuery()
        .setVariable(
            new VariablesQueryDto().setName("p1v1").setValues(new String[] {"Y", "Z", "A"}));

    final var response = listViewReader.queryProcessInstances(requestDto);
    assertThat(response.getTotalCount()).isEqualTo(1);
    assertThat(response.getProcessInstances()).hasSize(1);
    validateResultAgainstProcess(response.getProcessInstances().getFirst(), activeProcess);
  }

  @Test
  public void testQueryProcessInstancesByBatchOperationId() {
    final ListViewRequestDto requestDto = createSimpleProcessInstanceQuery();
    requestDto.getQuery().setBatchOperationId("b3");

    final var response = listViewReader.queryProcessInstances(requestDto);
    assertThat(response.getTotalCount()).isEqualTo(1);
    assertThat(response.getProcessInstances()).hasSize(1);
    validateResultAgainstProcess(response.getProcessInstances().getFirst(), completedProcess);
  }

  @Test
  public void testQueryProcessInstancesByParentId() {
    final ListViewRequestDto requestDto = createSimpleProcessInstanceQuery();
    requestDto.getQuery().setParentInstanceId(50L);

    final var response = listViewReader.queryProcessInstances(requestDto);
    assertThat(response.getTotalCount()).isEqualTo(1);
    assertThat(response.getProcessInstances()).hasSize(1);
    validateResultAgainstProcess(response.getProcessInstances().getFirst(), completedProcess);
  }

  @Test
  public void testQueryProcessInstancesByTenantId() {
    final ListViewRequestDto requestDto = createSimpleProcessInstanceQuery();
    requestDto.getQuery().setTenantId("tenant1");

    final var response = listViewReader.queryProcessInstances(requestDto);
    assertThat(response.getTotalCount()).isEqualTo(1);
    assertThat(response.getProcessInstances()).hasSize(1);
    validateResultAgainstProcess(response.getProcessInstances().getFirst(), activeProcess);
  }

  @Test
  public void testQueryProcessInstancesWithPermissions() {
    when(permissionsService.getProcessesWithPermission(IdentityPermission.READ))
        .thenReturn(
            PermissionsService.ResourcesAllowed.withIds(Set.of(activeProcess.getBpmnProcessId())));

    final ListViewRequestDto requestDto = createSimpleProcessInstanceQuery();

    final var response = listViewReader.queryProcessInstances(requestDto);
    assertThat(response.getTotalCount()).isEqualTo(1);
    assertThat(response.getProcessInstances()).hasSize(1);
    validateResultAgainstProcess(response.getProcessInstances().getFirst(), activeProcess);
  }

  @Test
  public void testQueryProcessInstancesWithSorting() {
    final ListViewRequestDto requestDto = createSimpleProcessInstanceQuery();
    requestDto.setSorting(
        new SortingDto()
            .setSortBy(ListViewRequestDto.SORT_BY_PROCESS_NAME)
            .setSortOrder(SortingDto.SORT_ORDER_DESC_VALUE));

    final var response = listViewReader.queryProcessInstances(requestDto);
    assertThat(response.getTotalCount()).isEqualTo(3);
    assertThat(response.getProcessInstances()).hasSize(3);
    validateResultAgainstProcess(response.getProcessInstances().get(0), incidentProcess);
    validateResultAgainstProcess(response.getProcessInstances().get(1), completedProcess);
    validateResultAgainstProcess(response.getProcessInstances().get(2), activeProcess);
  }

  private ListViewRequestDto createSimpleProcessInstanceQuery() {
    return new ListViewRequestDto()
        .setQuery(
            new ListViewQueryDto()
                .setActive(true)
                .setCanceled(true)
                .setIncidents(true)
                .setFinished(true)
                .setCompleted(true)
                .setRunning(true));
  }

  private void validateResultAgainstProcess(
      final ListViewProcessInstanceDto actual, final ProcessInstanceForListViewEntity expected) {
    assertThat(actual.getProcessName()).isEqualTo(expected.getProcessName());
    assertThat(actual.getBpmnProcessId()).isEqualTo(expected.getBpmnProcessId());
    assertThat(actual.getTenantId()).isEqualTo(expected.getTenantId());
    assertThat(actual.getId()).isEqualTo(expected.getId());
    assertThat(actual.getProcessId()).isEqualTo(String.valueOf(expected.getProcessDefinitionKey()));
  }
}
