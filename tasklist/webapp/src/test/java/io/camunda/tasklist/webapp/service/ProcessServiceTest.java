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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.webapp.graphql.entity.ProcessInstanceDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.camunda.tasklist.webapp.permission.TasklistPermissionsService;
import io.camunda.tasklist.webapp.rest.exception.ForbiddenActionException;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import io.camunda.tasklist.webapp.security.tenant.TenantService;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientStatusException;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.impl.ZeebeClientFutureImpl;
import io.camunda.zeebe.client.impl.command.CreateProcessInstanceCommandImpl;
import io.grpc.Status;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ProcessServiceTest {

  @Mock private TenantService tenantService;

  @Mock private ZeebeClient zeebeClient;

  @Spy
  private TasklistPermissionsService tasklistPermissionsService =
      new TasklistPermissionsService(null, null, null);

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

    doReturn(true)
        .when(tasklistPermissionsService)
        .hasPermissionToCreateProcessInstance(processDefinitionKey);
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
    doReturn(true)
        .when(tasklistPermissionsService)
        .hasPermissionToCreateProcessInstance(processDefinitionKey);

    final ProcessInstanceEvent processInstanceEvent =
        mockZeebeCreateProcessInstance(processDefinitionKey);

    final ProcessInstanceDTO response =
        instance.startProcessInstance(processDefinitionKey, variableInputDTOList, tenantId);
    assertThat(response).isInstanceOf(ProcessInstanceDTO.class);
    assertThat(response.getId()).isEqualTo(processInstanceEvent.getProcessInstanceKey());
  }

  private ProcessInstanceEvent mockZeebeCreateProcessInstance(final String processDefinitionKey) {
    final ProcessInstanceEvent processInstanceEvent = mock(ProcessInstanceEvent.class);
    when(processInstanceEvent.getProcessInstanceKey()).thenReturn(123456L);
    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3 step3 =
        mock(CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3.class);
    when(zeebeClient.newCreateInstanceCommand())
        .thenReturn(mock(CreateProcessInstanceCommandImpl.class));
    when(zeebeClient.newCreateInstanceCommand().bpmnProcessId(processDefinitionKey))
        .thenReturn(
            mock(CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep2.class));
    when(zeebeClient.newCreateInstanceCommand().bpmnProcessId(processDefinitionKey).latestVersion())
        .thenReturn(step3);
    when(step3.send()).thenReturn(mock(ZeebeClientFutureImpl.class));
    when(step3.send().join()).thenReturn(processInstanceEvent);
    return processInstanceEvent;
  }

  private ProcessInstanceEvent mockZeebeCreateProcessInstanceNotFound(
      final String processDefinitionKey) {
    final ProcessInstanceEvent processInstanceEvent = mock(ProcessInstanceEvent.class);
    when(processInstanceEvent.getProcessInstanceKey()).thenReturn(123456L);
    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3 step3 =
        mock(CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3.class);
    when(zeebeClient.newCreateInstanceCommand())
        .thenReturn(mock(CreateProcessInstanceCommandImpl.class));
    when(zeebeClient.newCreateInstanceCommand().bpmnProcessId(processDefinitionKey))
        .thenReturn(
            mock(CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep2.class));
    when(zeebeClient.newCreateInstanceCommand().bpmnProcessId(processDefinitionKey).latestVersion())
        .thenReturn(step3);
    when(step3.send()).thenThrow(new ClientStatusException(Status.NOT_FOUND, null));
    return processInstanceEvent;
  }

  @Test
  void startProcessInstanceMissingResourceBasedAuth() {
    final String processDefinitionKey = "processDefinitionKey";
    final List<VariableInputDTO> variableInputDTOList = new ArrayList<VariableInputDTO>();
    doReturn(false)
        .when(tasklistPermissionsService)
        .hasPermissionToCreateProcessInstance(processDefinitionKey);

    assertThatThrownBy(
            () -> instance.startProcessInstance(processDefinitionKey, variableInputDTOList, ""))
        .isInstanceOf(ForbiddenActionException.class);
  }

  @Test
  public void testStartProcessInstanceWhenProcessDefinitionNotFound() {
    // Given
    final String processDefinitionKey = "someKey";

    // When
    doReturn(true)
        .when(tasklistPermissionsService)
        .hasPermissionToCreateProcessInstance(processDefinitionKey);

    mockZeebeCreateProcessInstanceNotFound(processDefinitionKey);

    // Then
    assertThatThrownBy(() -> instance.startProcessInstance(processDefinitionKey, ""))
        .isInstanceOf(NotFoundApiException.class)
        .hasMessage("No process definition found with processDefinitionKey: 'someKey'");
  }

  @Test
  void startProcessInstanceMissingResourceBasedAuthCaseHasNoPermissionOnAnyResource() {
    final String processDefinitionKey = "processDefinitionKey";
    final List<VariableInputDTO> variableInputDTOList = new ArrayList<VariableInputDTO>();
    doReturn(false)
        .when(tasklistPermissionsService)
        .hasPermissionToCreateProcessInstance(processDefinitionKey);

    assertThatThrownBy(
            () -> instance.startProcessInstance(processDefinitionKey, variableInputDTOList, ""))
        .isInstanceOf(ForbiddenActionException.class);
  }

  @Test
  void startProcessInstanceWithResourceBasedAuth() {
    final String processDefinitionKey = "processDefinitionKey";
    final List<VariableInputDTO> variableInputDTOList = new ArrayList<VariableInputDTO>();
    doReturn(true)
        .when(tasklistPermissionsService)
        .hasPermissionToCreateProcessInstance(processDefinitionKey);

    final ProcessInstanceEvent processInstanceEvent =
        mockZeebeCreateProcessInstance(processDefinitionKey);

    final ProcessInstanceDTO response =
        instance.startProcessInstance(processDefinitionKey, variableInputDTOList, "");
    assertThat(response).isInstanceOf(ProcessInstanceDTO.class);
    assertThat(response.getId()).isEqualTo(processInstanceEvent.getProcessInstanceKey());
  }

  @Test
  void startProcessInstanceWithResourceBasedAuthCaseHasAllResourcesAccess() {
    final String processDefinitionKey = "processDefinitionKey";
    final List<VariableInputDTO> variableInputDTOList = new ArrayList<VariableInputDTO>();
    doReturn(true)
        .when(tasklistPermissionsService)
        .hasPermissionToCreateProcessInstance(processDefinitionKey);

    final ProcessInstanceEvent processInstanceEvent =
        mockZeebeCreateProcessInstance(processDefinitionKey);

    final ProcessInstanceDTO response =
        instance.startProcessInstance(processDefinitionKey, variableInputDTOList, "");
    assertThat(response).isInstanceOf(ProcessInstanceDTO.class);
    assertThat(response.getId()).isEqualTo(processInstanceEvent.getProcessInstanceKey());
  }
}
