/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.webapp.dto.ProcessInstanceDTO;
import io.camunda.tasklist.webapp.dto.VariableInputDTO;
import io.camunda.tasklist.webapp.permission.TasklistPermissionServices;
import io.camunda.tasklist.webapp.rest.exception.ForbiddenActionException;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import io.camunda.tasklist.webapp.tenant.TenantService;
import io.camunda.tasklist.zeebe.TasklistServicesAdapter;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ProcessServiceTest {

  @Mock private TenantService tenantService;

  @Mock private TasklistServicesAdapter tasklistServicesAdapter;
  @Mock private TasklistPermissionServices permissionServices;
  @InjectMocks private ProcessService instance;

  @Test
  void startProcessInstanceInvalidTenant() {
    final String processDefinitionKey = "processDefinitionKey";
    final List<VariableInputDTO> variableInputDTOList = new ArrayList<VariableInputDTO>();
    final String tenantId = "tenantA";

    final List<String> tenantIds = new ArrayList<String>();
    tenantIds.add("TenantB");
    tenantIds.add("TenantC");
    final TenantService.AuthenticatedTenants authenticatedTenants =
        TenantService.AuthenticatedTenants.assignedTenants(tenantIds);

    when(tenantService.isMultiTenancyEnabled()).thenReturn(true);
    when(tenantService.getAuthenticatedTenants()).thenReturn(authenticatedTenants);

    assertThatThrownBy(
            () -> instance.startProcessInstance(processDefinitionKey, variableInputDTOList, ""))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void startProcessInstanceInvalidTenantMultiTenancyOff() {
    final String processDefinitionKey = "processDefinitionKey";
    final List<VariableInputDTO> variableInputDTOList = new ArrayList<VariableInputDTO>();
    final String tenantId = "tenantA";

    final List<String> tenantIds = new ArrayList<String>();
    tenantIds.add("TenantB");
    tenantIds.add("TenantC");
    when(tenantService.isMultiTenancyEnabled()).thenReturn(false);

    final ProcessInstanceCreationRecord processInstanceEvent =
        mockCreateProcessInstance(processDefinitionKey);

    final ProcessInstanceDTO response =
        instance.startProcessInstance(processDefinitionKey, variableInputDTOList, tenantId);
    assertThat(response).isInstanceOf(ProcessInstanceDTO.class);
    assertThat(response.getId()).isEqualTo(processInstanceEvent.getProcessInstanceKey());
  }

  private ProcessInstanceCreationRecord mockCreateProcessInstance(
      final String processDefinitionKey) {
    final var processInstanceEvent = mock(ProcessInstanceCreationRecord.class);
    when(processInstanceEvent.getProcessInstanceKey()).thenReturn(123456L);
    doReturn(processInstanceEvent)
        .when(tasklistServicesAdapter)
        .createProcessInstance(eq(processDefinitionKey), any(), any());
    return processInstanceEvent;
  }

  private void mockCreateProcessInstanceNotFound(final String processDefinitionKey) {
    doThrow(
            new NotFoundApiException(
                "No process definition found with processDefinitionKey: 'someKey'"))
        .when(tasklistServicesAdapter)
        .createProcessInstance(eq(processDefinitionKey), any(), any());
  }

  private void mockCreateProcessInstanceForbiddenAction(final String processDefinitionKey) {
    doThrow(new ForbiddenActionException("Not allowed"))
        .when(tasklistServicesAdapter)
        .createProcessInstance(eq(processDefinitionKey), any(), any());
  }

  @Test
  public void testStartProcessInstanceWhenProcessDefinitionNotFound() {
    // Given
    final String processDefinitionKey = "someKey";

    // When
    doReturn(List.of(processDefinitionKey))
        .when(permissionServices)
        .getProcessDefinitionsWithCreateProcessInstancePermission();

    mockCreateProcessInstanceNotFound(processDefinitionKey);

    // Then
    assertThatThrownBy(() -> instance.startProcessInstance(processDefinitionKey, ""))
        .isInstanceOf(NotFoundApiException.class)
        .hasMessage("No process definition found with processDefinitionKey: 'someKey'");
  }

  @Test
  void startProcessInstanceMissingResourceBasedAuthCaseHasNoPermissionOnAnyResource() {
    final String processDefinitionKey = "processDefinitionKey";
    final List<VariableInputDTO> variableInputDTOList = new ArrayList<VariableInputDTO>();

    mockCreateProcessInstanceForbiddenAction(processDefinitionKey);

    assertThatThrownBy(
            () -> instance.startProcessInstance(processDefinitionKey, variableInputDTOList, ""))
        .isInstanceOf(ForbiddenActionException.class);
  }

  @Test
  void startProcessInstanceWithResourceBasedAuth() {
    final String processDefinitionKey = "processDefinitionKey";
    final List<VariableInputDTO> variableInputDTOList = new ArrayList<VariableInputDTO>();
    doReturn(List.of("processDefinitionKey"))
        .when(permissionServices)
        .getProcessDefinitionsWithCreateProcessInstancePermission();

    final ProcessInstanceCreationRecord processInstanceEvent =
        mockCreateProcessInstance(processDefinitionKey);

    final ProcessInstanceDTO response =
        instance.startProcessInstance(processDefinitionKey, variableInputDTOList, "");
    assertThat(response).isInstanceOf(ProcessInstanceDTO.class);
    assertThat(response.getId()).isEqualTo(processInstanceEvent.getProcessInstanceKey());
  }

  @Test
  void startProcessInstanceWithResourceBasedAuthCaseHasAllResourcesAccess() {
    final String processDefinitionKey = "processDefinitionKey";
    final List<VariableInputDTO> variableInputDTOList = new ArrayList<VariableInputDTO>();
    doReturn(List.of("otherProcessDefinitionKey", "*"))
        .when(permissionServices)
        .getProcessDefinitionsWithCreateProcessInstancePermission();

    final ProcessInstanceCreationRecord processInstanceEvent =
        mockCreateProcessInstance(processDefinitionKey);

    final ProcessInstanceDTO response =
        instance.startProcessInstance(processDefinitionKey, variableInputDTOList, "");
    assertThat(response).isInstanceOf(ProcessInstanceDTO.class);
    assertThat(response.getId()).isEqualTo(processInstanceEvent.getProcessInstanceKey());
  }
}
