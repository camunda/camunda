/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.elasticsearch;

import static io.camunda.operate.qa.util.RestAPITestUtil.createGetAllProcessInstancesQuery;
import static io.camunda.operate.util.TestUtil.createProcessInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.util.SearchTestRule;
import io.camunda.operate.webapp.rest.ProcessInstanceRestService;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.MigrationPlanDto;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.operate.webapp.writer.BatchOperationWriter;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.apache.http.HttpStatus;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;

/** Tests Elasticsearch queries for operations. */
public class BatchOperationWriterZeebeIT extends OperateZeebeAbstractIT {

  private static final String QUERY_CREATE_BATCH_OPERATIONS_URL =
      ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/batch-operation";
  @Rule public SearchTestRule searchTestRule = new SearchTestRule();
  private final String bpmnProcessId1 = "bpmnProcessId1";
  private final String bpmnProcessId2 = "bpmnProcessId2";
  private final String bpmnProcessId3 = "bpmnProcessId3";
  @Autowired private BatchOperationWriter batchOperationWriter;
  @Autowired private OperationTemplate operationTemplate;
  @MockBean private PermissionsService permissionsService;

  @Test
  public void testBatchUpdateWithPermisssionWhenAllowed() throws Exception {
    // given
    createData();
    final ListViewQueryDto query = createGetAllProcessInstancesQuery();
    final CreateBatchOperationRequestDto request =
        new CreateBatchOperationRequestDto()
            .setOperationType(OperationType.CANCEL_PROCESS_INSTANCE)
            .setQuery(query);

    // when
    when(permissionsService.permissionsEnabled()).thenReturn(true);
    when(permissionsService.getProcessesWithPermission(PermissionType.UPDATE_PROCESS_INSTANCE))
        .thenReturn(PermissionsService.ResourcesAllowed.all());
    final MvcResult mvcResult = postRequest(QUERY_CREATE_BATCH_OPERATIONS_URL, request);

    // then
    final BatchOperationEntity response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response.getInstancesCount()).isEqualTo(3);
    assertThat(response.getOperationsTotalCount()).isEqualTo(3);
  }

  @Test
  public void testBatchUpdateWithPermissionWhenNotAllowed() throws Exception {
    // given
    createData();
    final ListViewQueryDto query = createGetAllProcessInstancesQuery();
    final CreateBatchOperationRequestDto request =
        new CreateBatchOperationRequestDto()
            .setOperationType(OperationType.CANCEL_PROCESS_INSTANCE)
            .setQuery(query);

    // when
    when(permissionsService.permissionsEnabled()).thenReturn(true);
    when(permissionsService.getProcessesWithPermission(PermissionType.UPDATE_PROCESS_INSTANCE))
        .thenReturn(PermissionsService.ResourcesAllowed.withIds(Set.of()));
    final MvcResult mvcResult = postRequest(QUERY_CREATE_BATCH_OPERATIONS_URL, request);

    // then
    final BatchOperationEntity response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response.getInstancesCount()).isEqualTo(0);
    assertThat(response.getOperationsTotalCount()).isEqualTo(0);
  }

  @Test
  public void testBatchUpdateWithPermissionWhenSomeAllowed() throws Exception {
    // given
    createData();
    final ListViewQueryDto query = createGetAllProcessInstancesQuery();
    final CreateBatchOperationRequestDto request =
        new CreateBatchOperationRequestDto()
            .setOperationType(OperationType.CANCEL_PROCESS_INSTANCE)
            .setQuery(query);

    // when
    when(permissionsService.permissionsEnabled()).thenReturn(true);
    when(permissionsService.getProcessesWithPermission(PermissionType.UPDATE_PROCESS_INSTANCE))
        .thenReturn(PermissionsService.ResourcesAllowed.withIds(Set.of(bpmnProcessId1)));

    final MvcResult mvcResult = postRequest(QUERY_CREATE_BATCH_OPERATIONS_URL, request);

    // then
    final BatchOperationEntity response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response.getInstancesCount()).isEqualTo(1);
    assertThat(response.getOperationsTotalCount()).isEqualTo(1);
  }

  @Test
  public void testBatchDeleteWithPermissionWhenAllowed() throws Exception {
    // given
    createData();
    final ListViewQueryDto query = createGetAllProcessInstancesQuery();
    final CreateBatchOperationRequestDto request =
        new CreateBatchOperationRequestDto()
            .setOperationType(OperationType.DELETE_PROCESS_INSTANCE)
            .setQuery(query);

    // when
    when(permissionsService.permissionsEnabled()).thenReturn(true);
    when(permissionsService.getProcessesWithPermission(PermissionType.DELETE_PROCESS_INSTANCE))
        .thenReturn(PermissionsService.ResourcesAllowed.all());

    final MvcResult mvcResult = postRequest(QUERY_CREATE_BATCH_OPERATIONS_URL, request);

    // then
    final BatchOperationEntity response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response.getInstancesCount()).isEqualTo(3);
    assertThat(response.getOperationsTotalCount()).isEqualTo(3);
  }

  @Test
  public void testBatchDeleteWithPermissionWhenNotAllowed() throws Exception {
    // given
    createData();
    final ListViewQueryDto query = createGetAllProcessInstancesQuery();
    final CreateBatchOperationRequestDto request =
        new CreateBatchOperationRequestDto()
            .setOperationType(OperationType.DELETE_PROCESS_INSTANCE)
            .setQuery(query);

    // when
    when(permissionsService.permissionsEnabled()).thenReturn(true);
    when(permissionsService.getProcessesWithPermission(PermissionType.DELETE_PROCESS_INSTANCE))
        .thenReturn(PermissionsService.ResourcesAllowed.withIds(Set.of()));
    final MvcResult mvcResult = postRequest(QUERY_CREATE_BATCH_OPERATIONS_URL, request);

    // then
    final BatchOperationEntity response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response.getInstancesCount()).isEqualTo(0);
    assertThat(response.getOperationsTotalCount()).isEqualTo(0);
  }

  @Test
  public void testBatchDeleteWithPermissionWhenSomeAllowed() throws Exception {
    // given
    createData();
    final ListViewQueryDto query = createGetAllProcessInstancesQuery();
    final CreateBatchOperationRequestDto request =
        new CreateBatchOperationRequestDto()
            .setOperationType(OperationType.DELETE_PROCESS_INSTANCE)
            .setQuery(query);

    // when
    when(permissionsService.permissionsEnabled()).thenReturn(true);
    when(permissionsService.getProcessesWithPermission(PermissionType.DELETE_PROCESS_INSTANCE))
        .thenReturn(PermissionsService.ResourcesAllowed.withIds(Set.of(bpmnProcessId1)));
    final MvcResult mvcResult = postRequest(QUERY_CREATE_BATCH_OPERATIONS_URL, request);

    // then
    final BatchOperationEntity response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response.getInstancesCount()).isEqualTo(1);
    assertThat(response.getOperationsTotalCount()).isEqualTo(1);
  }

  private void createData() {
    final ProcessInstanceForListViewEntity processInstance1 =
        createProcessInstance(ProcessInstanceState.ACTIVE).setBpmnProcessId(bpmnProcessId1);
    processInstance1.setProcessDefinitionKey(processInstance1.getProcessInstanceKey() + 1);
    final ProcessInstanceForListViewEntity processInstance2 =
        createProcessInstance(ProcessInstanceState.ACTIVE).setBpmnProcessId(bpmnProcessId2);
    processInstance2.setProcessDefinitionKey(processInstance2.getProcessInstanceKey() + 1);
    final ProcessInstanceForListViewEntity processInstance3 =
        createProcessInstance(ProcessInstanceState.ACTIVE).setBpmnProcessId(bpmnProcessId3);
    processInstance3.setProcessDefinitionKey(processInstance3.getProcessInstanceKey() + 1);
    searchTestRule.persistNew(processInstance1, processInstance2, processInstance3);
  }

  @Test
  public void shouldValidateMigrationPlanForMigrateProcessInstance1() throws Exception {

    // given
    final MigrationPlanDto invalidPlan =
        new MigrationPlanDto().setTargetProcessDefinitionKey("123");

    // when
    final ListViewQueryDto query = createGetAllProcessInstancesQuery();
    final CreateBatchOperationRequestDto request =
        new CreateBatchOperationRequestDto()
            .setName("batch-1")
            .setOperationType(OperationType.MIGRATE_PROCESS_INSTANCE)
            .setQuery(query)
            .setMigrationPlan(invalidPlan);

    final MvcResult mvcResult = postBatchOperation(request, HttpStatus.SC_BAD_REQUEST);

    // then
    assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  public void shouldValidateMigrationPlanForMigrateProcessInstance2() throws Exception {

    // given
    final MigrationPlanDto invalidPlan =
        new MigrationPlanDto()
            .setMappingInstructions(
                List.of(
                    new MigrationPlanDto.MappingInstruction()
                        .setSourceElementId("source")
                        .setTargetElementId("target")));

    // when
    final ListViewQueryDto query = createGetAllProcessInstancesQuery();
    final CreateBatchOperationRequestDto request =
        new CreateBatchOperationRequestDto()
            .setName("batch-1")
            .setOperationType(OperationType.MIGRATE_PROCESS_INSTANCE)
            .setQuery(query)
            .setMigrationPlan(invalidPlan);

    final MvcResult mvcResult = postBatchOperation(request, HttpStatus.SC_BAD_REQUEST);

    // then
    assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  public void shouldValidateMigrationPlanForMigrateProcessInstance3() throws Exception {

    // given
    final MigrationPlanDto invalidPlan =
        new MigrationPlanDto()
            .setTargetProcessDefinitionKey("123")
            .setMappingInstructions(List.of());

    // when
    final ListViewQueryDto query = createGetAllProcessInstancesQuery();
    final CreateBatchOperationRequestDto request =
        new CreateBatchOperationRequestDto()
            .setName("batch-1")
            .setOperationType(OperationType.MIGRATE_PROCESS_INSTANCE)
            .setQuery(query)
            .setMigrationPlan(invalidPlan);

    final MvcResult mvcResult = postBatchOperation(request, HttpStatus.SC_BAD_REQUEST);

    // then
    assertThat(mvcResult.getResponse().getStatus()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  public void shouldCreateMigrateProcessInstanceOperations() throws Exception {

    // given
    // process instances that are running
    final Long processDefinitionKey =
        tester
            .deployProcess("single-task.bpmn")
            .waitUntil()
            .processIsDeployed()
            .getProcessDefinitionKey();
    final Long processInstanceKey1 =
        tester
            .startProcessInstance("process", null)
            .and()
            .waitUntil()
            .processInstanceIsStarted()
            .getProcessInstanceKey();
    final Long processInstanceKey2 =
        tester
            .startProcessInstance("process", null)
            .and()
            .waitUntil()
            .processInstanceIsStarted()
            .getProcessInstanceKey();
    final ListViewQueryDto processInstanceQuery =
        createGetAllProcessInstancesQuery()
            .setIds(
                Arrays.asList(
                    String.valueOf(processInstanceKey1), String.valueOf(processInstanceKey2)));

    // when
    when(permissionsService.permissionsEnabled()).thenReturn(true);
    when(permissionsService.getProcessesWithPermission(any(PermissionType.class)))
        .thenReturn(PermissionsService.ResourcesAllowed.all());
    final ListViewQueryDto query = createGetAllProcessInstancesQuery();
    final CreateBatchOperationRequestDto request =
        new CreateBatchOperationRequestDto()
            .setName("batch-1")
            .setOperationType(OperationType.MIGRATE_PROCESS_INSTANCE)
            .setQuery(query)
            .setMigrationPlan(
                new MigrationPlanDto()
                    .setTargetProcessDefinitionKey("123")
                    .setMappingInstructions(
                        List.of(
                            new MigrationPlanDto.MappingInstruction()
                                .setSourceElementId("source")
                                .setTargetElementId("target"))));

    final MvcResult mvcResult = postBatchOperation(request, HttpStatus.SC_OK);

    // then
    final BatchOperationEntity batchOperationEntity =
        objectMapper.readValue(
            mvcResult.getResponse().getContentAsString(), BatchOperationEntity.class);
    final List<OperationEntity> operations =
        searchAllDocuments(operationTemplate.getAlias(), OperationEntity.class);
    final String migrationPlanJson = objectMapper.writeValueAsString(request.getMigrationPlan());

    assertThat(operations.size()).isEqualTo(2);
    assertThat(operations)
        .extracting("type")
        .containsExactly(request.getOperationType(), request.getOperationType());
    assertThat(operations)
        .extracting("batchOperationId")
        .containsExactly(batchOperationEntity.getId(), batchOperationEntity.getId());
    assertThat(operations)
        .extracting("migrationPlan")
        .containsExactly(migrationPlanJson, migrationPlanJson);
    assertThat(operations)
        .extracting("processInstanceKey")
        .containsExactlyInAnyOrder(processInstanceKey1, processInstanceKey2);
  }
}
