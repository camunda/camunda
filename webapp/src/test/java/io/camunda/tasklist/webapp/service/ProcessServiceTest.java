/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.webapp.graphql.entity.ProcessInstanceDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.security.tenant.TenantService;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.impl.ZeebeClientFutureImpl;
import io.camunda.zeebe.client.impl.command.CreateProcessInstanceCommandImpl;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ProcessServiceTest {

  @Mock private TenantService tenantService;

  @Mock private ZeebeClient zeebeClient;

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

    Assertions.assertThatThrownBy(
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
    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3 step3 =
        mock(CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3.class);
    final ProcessInstanceEvent processInstanceEvent = mock(ProcessInstanceEvent.class);

    when(tenantService.isMultiTenancyEnabled()).thenReturn(false);
    when(zeebeClient.newCreateInstanceCommand())
        .thenReturn(mock(CreateProcessInstanceCommandImpl.class));
    when(zeebeClient.newCreateInstanceCommand().bpmnProcessId(processDefinitionKey))
        .thenReturn(
            mock(CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep2.class));
    when(zeebeClient.newCreateInstanceCommand().bpmnProcessId(processDefinitionKey).latestVersion())
        .thenReturn(step3);
    when(step3.send()).thenReturn(mock(ZeebeClientFutureImpl.class));
    when(step3.send().join()).thenReturn(processInstanceEvent);

    final ProcessInstanceDTO response =
        instance.startProcessInstance(processDefinitionKey, variableInputDTOList, tenantId);
    Assertions.assertThat(response).isInstanceOf(ProcessInstanceDTO.class);
    Assertions.assertThat(response.getId()).isEqualTo(processInstanceEvent.getProcessInstanceKey());
  }
}
