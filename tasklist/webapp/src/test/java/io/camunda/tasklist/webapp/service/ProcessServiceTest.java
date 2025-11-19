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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.webapp.graphql.entity.ProcessInstanceDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.camunda.tasklist.webapp.rest.exception.ForbiddenActionException;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import io.camunda.tasklist.webapp.security.identity.IdentityAuthorizationService;
import io.camunda.tasklist.webapp.security.identity.IdentityAuthorizationServiceImpl;
import io.camunda.tasklist.webapp.security.tenant.TenantService;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
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

  @Mock private TasklistServicesAdapter tasklistServicesAdapter;

  @Spy
  private IdentityAuthorizationService identityAuthorizationService =
      new IdentityAuthorizationServiceImpl();

  @InjectMocks private ProcessService instance;

  @Test
  void startProcessInstanceNoAuthentication() {
    final String processDefinitionKey = "processDefinitionKey";
    final List<VariableInputDTO> variableInputDTOList = new ArrayList<>();
    final String tenantId = "tenantA";

    when(tenantService.isMultiTenancyEnabled()).thenReturn(false);

    final ProcessInstanceCreationRecord processInstanceEvent =
        mockCreateProcessInstance(processDefinitionKey, false);

    final ProcessInstanceDTO response =
        instance.startProcessInstance(processDefinitionKey, variableInputDTOList, tenantId, false);
    assertThat(response).isInstanceOf(ProcessInstanceDTO.class);
    assertThat(response.getId()).isEqualTo(processInstanceEvent.getProcessInstanceKey());
    verifyNoInteractions(identityAuthorizationService);
  }

  @Test
  void startProcessInstanceInvalidTenant() {
    final String processDefinitionKey = "processDefinitionKey";
    final List<VariableInputDTO> variableInputDTOList = new ArrayList<>();

    final List<String> tenantIds = new ArrayList<>();
    tenantIds.add("TenantB");
    tenantIds.add("TenantC");
    final TenantService.AuthenticatedTenants authenticatedTenants =
        TenantService.AuthenticatedTenants.assignedTenants(tenantIds);

    when(tenantService.isMultiTenancyEnabled()).thenReturn(true);
    when(tenantService.getAuthenticatedTenants()).thenReturn(authenticatedTenants);

    doReturn(true).when(identityAuthorizationService).isAllowedToStartProcess(processDefinitionKey);

    assertThatThrownBy(
            () -> instance.startProcessInstance(processDefinitionKey, variableInputDTOList, ""))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void startProcessInstanceInvalidTenantMultiTenancyOff() {
    final String processDefinitionKey = "processDefinitionKey";
    final List<VariableInputDTO> variableInputDTOList = new ArrayList<>();
    final String tenantId = "tenantA";

    when(tenantService.isMultiTenancyEnabled()).thenReturn(false);

    doReturn(true).when(identityAuthorizationService).isAllowedToStartProcess(processDefinitionKey);

    final ProcessInstanceCreationRecord processInstanceEvent =
        mockCreateProcessInstance(processDefinitionKey, true);

    final ProcessInstanceDTO response =
        instance.startProcessInstance(processDefinitionKey, variableInputDTOList, tenantId);
    assertThat(response).isInstanceOf(ProcessInstanceDTO.class);
    assertThat(response.getId()).isEqualTo(processInstanceEvent.getProcessInstanceKey());
    verify(identityAuthorizationService).isAllowedToStartProcess(processDefinitionKey);
  }

  private ProcessInstanceCreationRecord mockCreateProcessInstance(
      final String processDefinitionKey, final boolean usesAuthentication) {
    final var processInstanceEvent = mock(ProcessInstanceCreationRecord.class);
    when(processInstanceEvent.getProcessInstanceKey()).thenReturn(123456L);
    if (usesAuthentication) {
      doReturn(processInstanceEvent)
          .when(tasklistServicesAdapter)
          .createProcessInstance(eq(processDefinitionKey), any(), any());
    } else {
      doReturn(processInstanceEvent)
          .when(tasklistServicesAdapter)
          .createProcessInstanceWithoutAuthentication(eq(processDefinitionKey), any(), any());
    }
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
        .when(identityAuthorizationService)
        .getProcessDefinitionsFromAuthorization();

    mockCreateProcessInstanceNotFound(processDefinitionKey);

    // Then
    assertThatThrownBy(() -> instance.startProcessInstance(processDefinitionKey, ""))
        .isInstanceOf(NotFoundApiException.class)
        .hasMessage("No process definition found with processDefinitionKey: 'someKey'");
  }

  @Test
  void startProcessInstanceMissingResourceBasedAuthCaseHasNoPermissionOnAnyResource() {
    final String processDefinitionKey = "processDefinitionKey";
    final List<VariableInputDTO> variableInputDTOList = new ArrayList<>();

    mockCreateProcessInstanceForbiddenAction(processDefinitionKey);

    doReturn(false)
        .when(identityAuthorizationService)
        .isAllowedToStartProcess(processDefinitionKey);

    assertThatThrownBy(
            () -> instance.startProcessInstance(processDefinitionKey, variableInputDTOList, ""))
        .isInstanceOf(ForbiddenActionException.class);
    verify(identityAuthorizationService).isAllowedToStartProcess(processDefinitionKey);
  }

  @Test
  void startProcessInstanceWithResourceBasedAuth() {
    final String processDefinitionKey = "processDefinitionKey";
    final List<VariableInputDTO> variableInputDTOList = new ArrayList<>();
    doReturn(List.of("processDefinitionKey"))
        .when(identityAuthorizationService)
        .getProcessDefinitionsFromAuthorization();

    final ProcessInstanceCreationRecord processInstanceEvent =
        mockCreateProcessInstance(processDefinitionKey, true);

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
        .when(identityAuthorizationService)
        .getProcessDefinitionsFromAuthorization();

    final ProcessInstanceCreationRecord processInstanceEvent =
        mockCreateProcessInstance(processDefinitionKey, true);

    final ProcessInstanceDTO response =
        instance.startProcessInstance(processDefinitionKey, variableInputDTOList, "");
    assertThat(response).isInstanceOf(ProcessInstanceDTO.class);
    assertThat(response.getId()).isEqualTo(processInstanceEvent.getProcessInstanceKey());
  }
}
