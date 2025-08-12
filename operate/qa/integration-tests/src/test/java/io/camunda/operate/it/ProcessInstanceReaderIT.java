/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

import io.camunda.operate.store.NotFoundException;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.rest.dto.ProcessInstanceCoreStatisticsDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.listview.ListViewJoinRelation;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.time.OffsetDateTime;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

public class ProcessInstanceReaderIT extends OperateSearchAbstractIT {

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired private OperationTemplate operationTemplate;

  @Autowired private ProcessInstanceReader processInstanceReader;

  @Autowired private CamundaAuthenticationProvider camundaAuthenticationProvider;

  @MockitoBean private PermissionsService permissionsService;

  private ProcessInstanceForListViewEntity processInstanceData;
  private ProcessInstanceForListViewEntity processInstanceData2;
  private OperationEntity operationData;

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    final Long processInstanceKey1 = 2251799813685251L;
    final Long processInstanceKey2 = 2251799813685252L;
    final String indexName = listViewTemplate.getFullQualifiedName();

    processInstanceData =
        new ProcessInstanceForListViewEntity()
            .setId("2251799813685251")
            .setKey(processInstanceKey1)
            .setPartitionId(1)
            .setProcessDefinitionKey(2251799813685249L)
            .setProcessName("Demo process")
            .setProcessVersion(1)
            .setBpmnProcessId("demoProcess")
            .setStartDate(OffsetDateTime.now())
            .setState(ProcessInstanceState.ACTIVE)
            .setTreePath("PI_2251799813685251")
            .setIncident(true)
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .setProcessInstanceKey(processInstanceKey1)
            .setJoinRelation(new ListViewJoinRelation("processInstance"));
    processInstanceData2 =
        new ProcessInstanceForListViewEntity()
            .setId("2251799813685252")
            .setKey(processInstanceKey2)
            .setPartitionId(1)
            .setProcessDefinitionKey(2251799813685242L)
            .setProcessName("Demo process 2")
            .setProcessVersion(1)
            .setBpmnProcessId("demoProcess2")
            .setStartDate(OffsetDateTime.now())
            .setState(ProcessInstanceState.ACTIVE)
            .setTreePath("PI_2251799813685252")
            .setIncident(false)
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .setProcessInstanceKey(processInstanceKey2)
            .setJoinRelation(new ListViewJoinRelation("processInstance"));

    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName, String.valueOf(processInstanceKey1), processInstanceData);
    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName, String.valueOf(processInstanceKey2), processInstanceData2);

    operationData = new OperationEntity();
    operationData.setProcessInstanceKey(processInstanceData.getProcessInstanceKey());
    operationData.setUsername(
        camundaAuthenticationProvider.getCamundaAuthentication().authenticatedUsername());
    operationData.setState(OperationState.SCHEDULED);

    testSearchRepository.createOrUpdateDocumentFromObject(
        operationTemplate.getFullQualifiedName(), operationData);

    searchContainerManager.refreshIndices("*operate*");
  }

  @Test
  public void testGetProcessInstanceWithOperationsByKeyWithCorrectKey() {
    // When
    final ListViewProcessInstanceDto processInstance =
        processInstanceReader.getProcessInstanceWithOperationsByKey(
            processInstanceData.getProcessInstanceKey());
    assertThat(processInstance.getId())
        .isEqualTo(String.valueOf(processInstanceData.getProcessInstanceKey()));
    assertThat(processInstance.getOperations().size()).isEqualTo(1);
    assertThat(processInstance.getOperations().get(0).getId()).isEqualTo(operationData.getId());
  }

  @Test
  public void testGetProcessInstanceWithCorrectKey() {
    // When
    final ProcessInstanceForListViewEntity processInstance =
        processInstanceReader.getProcessInstanceByKey(processInstanceData.getProcessInstanceKey());
    assertThat(processInstance.getId())
        .isEqualTo(String.valueOf(processInstanceData.getProcessInstanceKey()));
  }

  @Test
  public void testGetProcessInstanceWithInvalidKey() {
    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> processInstanceReader.getProcessInstanceByKey(1L));
  }

  @Test
  public void testGetProcessInstanceWithOperationsWithInvalidKey() {
    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> processInstanceReader.getProcessInstanceWithOperationsByKey(1L));
  }

  @Test
  public void testGetCoreStatisticsWithWildcardPermissions() {
    // given
    when(permissionsService.permissionsEnabled()).thenReturn(true);
    when(permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_INSTANCE))
        .thenReturn(PermissionsService.ResourcesAllowed.wildcard());
    // when
    final ProcessInstanceCoreStatisticsDto result = processInstanceReader.getCoreStatistics();
    assertThat(result.getRunning()).isEqualTo(2);
    assertThat(result.getWithIncidents()).isOne();
    assertThat(result.getActive()).isOne();
  }

  @Test
  public void testGetCoreStatisticsWithSomePermissions() {
    // given
    when(permissionsService.permissionsEnabled()).thenReturn(true);
    when(permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_INSTANCE))
        .thenReturn(
            PermissionsService.ResourcesAllowed.withIds(
                Set.of(processInstanceData.getBpmnProcessId())));
    // when
    final ProcessInstanceCoreStatisticsDto result = processInstanceReader.getCoreStatistics();
    assertThat(result.getRunning()).isOne();
    assertThat(result.getWithIncidents()).isOne();
  }

  @Test
  public void testGetCoreStatisticsWithNoPermission() {
    // given
    when(permissionsService.permissionsEnabled()).thenReturn(true);
    when(permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_INSTANCE))
        .thenReturn(PermissionsService.ResourcesAllowed.withIds(Set.of()));
    // when
    final ProcessInstanceCoreStatisticsDto result = processInstanceReader.getCoreStatistics();
    assertThat(result.getRunning()).isZero();
    assertThat(result.getWithIncidents()).isZero();
  }
}
