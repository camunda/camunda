/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.operate.store.SequenceFlowStore;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.reader.FlowNodeStatisticsReader;
import io.camunda.operate.webapp.reader.IncidentReader;
import io.camunda.operate.webapp.reader.ListViewReader;
import io.camunda.operate.webapp.reader.ListenerReader;
import io.camunda.operate.webapp.reader.VariableReader;
import io.camunda.operate.webapp.rest.dto.VariableRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeMetadataRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import io.camunda.operate.webapp.rest.exception.NotAuthorizedException;
import io.camunda.operate.webapp.rest.validation.ModifyProcessInstanceRequestValidator;
import io.camunda.operate.webapp.rest.validation.ProcessInstanceRequestValidator;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.operate.webapp.writer.BatchOperationWriter;
import io.camunda.webapps.schema.entities.operate.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ProcessInstanceRestServiceTest {

  @Mock private PermissionsService permissionsService;
  @Mock private ProcessInstanceRequestValidator processInstanceRequestValidator;
  @Mock private ModifyProcessInstanceRequestValidator modifyProcessInstanceRequestValidator;
  @Mock private BatchOperationWriter batchOperationWriter;
  @Mock private ProcessInstanceReader processInstanceReader;
  @Mock private ListenerReader listenerReader;
  @Mock private ListViewReader listViewReader;
  @Mock private IncidentReader incidentReader;
  @Mock private VariableReader variableReader;
  @Mock private FlowNodeInstanceReader flowNodeInstanceReader;
  @Mock private FlowNodeStatisticsReader flowNodeStatisticsReader;
  @Mock private SequenceFlowStore sequenceFlowStore;

  private ProcessInstanceRestService underTest;

  @BeforeEach
  public void setup() {
    underTest =
        new ProcessInstanceRestService(
            permissionsService,
            processInstanceRequestValidator,
            modifyProcessInstanceRequestValidator,
            batchOperationWriter,
            processInstanceReader,
            listenerReader,
            listViewReader,
            incidentReader,
            variableReader,
            flowNodeInstanceReader,
            flowNodeStatisticsReader,
            sequenceFlowStore);

    when(permissionsService.permissionsEnabled()).thenReturn(true);
    when(permissionsService.hasPermissionForProcess(any(), any(PermissionType.class)))
        .thenReturn(true);
  }

  @Test
  public void testGetInstanceByIdWithValidId() {
    // given
    final String validId = "123";
    // when
    final ListViewProcessInstanceDto expectedDto = new ListViewProcessInstanceDto().setId("one id");
    when(processInstanceReader.getProcessInstanceWithOperationsByKey(Long.valueOf(validId)))
        .thenReturn(expectedDto);
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(validId)))
        .thenReturn(new ProcessInstanceForListViewEntity());
    // then
    final ListViewProcessInstanceDto actualResult = underTest.queryProcessInstanceById(validId);
    assertThat(actualResult).isEqualTo(expectedDto);
  }

  @Test
  public void testProcessInstanceByIdFailsWhenNoPermissions() {
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(
            bpmnProcessId, PermissionType.READ_PROCESS_INSTANCE))
        .thenReturn(false);

    final NotAuthorizedException exception =
        assertThrows(
            NotAuthorizedException.class,
            () -> underTest.queryProcessInstanceById(processInstanceId));

    assertThat(exception.getMessage())
        .contains("No READ_PROCESS_INSTANCE permission for process instance");
  }

  @Test
  public void testProcessInstanceIncidentsFailsWhenNoPermissions() {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(
            bpmnProcessId, PermissionType.READ_PROCESS_INSTANCE))
        .thenReturn(false);

    final NotAuthorizedException exception =
        assertThrows(
            NotAuthorizedException.class,
            () -> underTest.queryIncidentsByProcessInstanceId(processInstanceId));

    assertThat(exception.getMessage())
        .contains("No READ_PROCESS_INSTANCE permission for process instance");
  }

  @Test
  public void testProcessInstanceSequenceFlowsFailsWhenNoPermissions() {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(
            bpmnProcessId, PermissionType.READ_PROCESS_INSTANCE))
        .thenReturn(false);

    final NotAuthorizedException exception =
        assertThrows(
            NotAuthorizedException.class,
            () -> underTest.querySequenceFlowsByProcessInstanceId(processInstanceId));

    assertThat(exception.getMessage())
        .contains("No READ_PROCESS_INSTANCE permission for process instance");
  }

  @Test
  public void testProcessInstanceVariablesFailsWhenNoPermissions() {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(
            bpmnProcessId, PermissionType.READ_PROCESS_INSTANCE))
        .thenReturn(false);

    final NotAuthorizedException exception =
        assertThrows(
            NotAuthorizedException.class,
            () -> underTest.getVariables(processInstanceId, new VariableRequestDto()));

    assertThat(exception.getMessage())
        .contains("No READ_PROCESS_INSTANCE permission for process instance");
  }

  @Test
  public void testProcessInstanceFlowNodeStatesFailsWhenNoPermissions() {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(
            bpmnProcessId, PermissionType.READ_PROCESS_INSTANCE))
        .thenReturn(false);

    final NotAuthorizedException exception =
        assertThrows(
            NotAuthorizedException.class, () -> underTest.getFlowNodeStates(processInstanceId));

    assertThat(exception.getMessage())
        .contains("No READ_PROCESS_INSTANCE permission for process instance");
  }

  @Test
  public void testProcessInstanceStatisticsFailsWhenNoPermissions() {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(
            bpmnProcessId, PermissionType.READ_PROCESS_INSTANCE))
        .thenReturn(false);

    final NotAuthorizedException exception =
        assertThrows(
            NotAuthorizedException.class, () -> underTest.getStatistics(processInstanceId));

    assertThat(exception.getMessage())
        .contains("No READ_PROCESS_INSTANCE permission for process instance");
  }

  @Test
  public void testProcessInstanceFlowNodeMetadataFailsWhenNoPermissions() {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(
            bpmnProcessId, PermissionType.READ_PROCESS_INSTANCE))
        .thenReturn(false);

    final NotAuthorizedException exception =
        assertThrows(
            NotAuthorizedException.class,
            () ->
                underTest.getFlowNodeMetadata(processInstanceId, new FlowNodeMetadataRequestDto()));

    assertThat(exception.getMessage())
        .contains("No READ_PROCESS_INSTANCE permission for process instance");
  }

  @Test
  public void testProcessInstanceDeleteOperationFailsWhenNoPermissions() {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(
            bpmnProcessId, PermissionType.DELETE_PROCESS_INSTANCE))
        .thenReturn(false);

    final NotAuthorizedException exception =
        assertThrows(
            NotAuthorizedException.class,
            () ->
                underTest.operation(
                    processInstanceId,
                    new CreateOperationRequestDto()
                        .setOperationType(OperationType.DELETE_PROCESS_INSTANCE)));

    assertThat(exception.getMessage())
        .contains("No DELETE_PROCESS_INSTANCE permission for process instance");
  }

  @Test
  public void testProcessInstanceUpdateOperationFailsWhenNoPermissions() {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(
            bpmnProcessId, PermissionType.UPDATE_PROCESS_INSTANCE))
        .thenReturn(false);

    final NotAuthorizedException exception =
        assertThrows(
            NotAuthorizedException.class,
            () ->
                underTest.operation(
                    processInstanceId,
                    new CreateOperationRequestDto()
                        .setOperationType(OperationType.CANCEL_PROCESS_INSTANCE)));

    assertThat(exception.getMessage())
        .contains("No UPDATE_PROCESS_INSTANCE permission for process instance");
  }

  @Test
  public void testProcessInstanceModifyOperationFailsWhenNoPermissions() {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(
            bpmnProcessId, PermissionType.UPDATE_PROCESS_INSTANCE))
        .thenReturn(false);

    final NotAuthorizedException exception =
        assertThrows(
            NotAuthorizedException.class,
            () ->
                underTest.operation(
                    processInstanceId,
                    new CreateOperationRequestDto().setOperationType(OperationType.ADD_VARIABLE)));

    assertThat(exception.getMessage())
        .contains("No UPDATE_PROCESS_INSTANCE permission for process instance");
  }

  @Test
  public void testProcessInstanceSingleVariableFailsWhenNoPermissions() {
    // given
    final String processInstanceId = "123";
    final String bpmnProcessId = "processId";
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(
            bpmnProcessId, PermissionType.READ_PROCESS_INSTANCE))
        .thenReturn(false);

    final NotAuthorizedException exception =
        assertThrows(
            NotAuthorizedException.class, () -> underTest.getVariable(processInstanceId, "var1"));

    assertThat(exception.getMessage())
        .contains("No READ_PROCESS_INSTANCE permission for process instance");
  }
}
