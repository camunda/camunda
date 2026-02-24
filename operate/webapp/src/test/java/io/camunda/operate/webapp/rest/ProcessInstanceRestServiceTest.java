/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.reader.ListViewReader;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.rest.exception.NotAuthorizedException;
import io.camunda.operate.webapp.rest.validation.ModifyProcessInstanceRequestValidator;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.operate.webapp.writer.BatchOperationWriter;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ProcessInstanceRestServiceTest {

  @Mock private PermissionsService permissionsService;
  @Mock private ModifyProcessInstanceRequestValidator modifyProcessInstanceRequestValidator;
  @Mock private BatchOperationWriter batchOperationWriter;
  @Mock private ProcessInstanceReader processInstanceReader;
  @Mock private ListViewReader listViewReader;
  @Mock private FlowNodeInstanceReader flowNodeInstanceReader;

  private ProcessInstanceRestService underTest;

  @BeforeEach
  public void setup() {
    underTest =
        new ProcessInstanceRestService(
            permissionsService,
            modifyProcessInstanceRequestValidator,
            batchOperationWriter,
            processInstanceReader,
            listViewReader,
            flowNodeInstanceReader);

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
        assertThatExceptionOfType(NotAuthorizedException.class)
            .isThrownBy(() -> underTest.queryProcessInstanceById(processInstanceId))
            .actual();

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
        assertThatExceptionOfType(NotAuthorizedException.class)
            .isThrownBy(() -> underTest.getFlowNodeStates(processInstanceId))
            .actual();

    assertThat(exception.getMessage())
        .contains("No READ_PROCESS_INSTANCE permission for process instance");
  }
}
