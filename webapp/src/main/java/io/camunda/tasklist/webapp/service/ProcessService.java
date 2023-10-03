/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.webapp.graphql.entity.ProcessInstanceDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import io.camunda.tasklist.webapp.security.tenant.TenantService;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.command.ClientStatusException;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.grpc.Status;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProcessService {
  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessService.class);

  @Autowired private ZeebeClient zeebeClient;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private TenantService tenantService;

  public ProcessInstanceDTO startProcessInstance(
      final String processDefinitionKey, final String tenantId) {
    return startProcessInstance(processDefinitionKey, null, tenantId);
  }

  public ProcessInstanceDTO startProcessInstance(
      final String processDefinitionKey,
      final List<VariableInputDTO> variables,
      final String tenantId) {

    final boolean isMultiTenancyEnabled = tenantService.isMultiTenancyEnabled();

    if (isMultiTenancyEnabled && !tenantService.getAuthenticatedTenants().contains(tenantId)) {
      throw new InvalidRequestException("Invalid tenant.");
    }

    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3
        createProcessInstanceCommandStep3 =
            zeebeClient
                .newCreateInstanceCommand()
                .bpmnProcessId(processDefinitionKey)
                .latestVersion();

    if (isMultiTenancyEnabled) {
      createProcessInstanceCommandStep3.tenantId(tenantId);
    }

    if (CollectionUtils.isNotEmpty(variables)) {
      final Map<String, Object> variablesMap =
          variables.stream()
              .collect(Collectors.toMap(VariableInputDTO::getName, this::extractTypedValue));
      createProcessInstanceCommandStep3.variables(variablesMap);
    }

    ProcessInstanceEvent processInstanceEvent = null;
    try {
      processInstanceEvent = createProcessInstanceCommandStep3.send().join();
      LOGGER.debug("Process instance created for process [{}]", processDefinitionKey);
    } catch (ClientStatusException ex) {
      if (Status.Code.NOT_FOUND.equals(ex.getStatusCode())) {
        throw new NotFoundApiException(
            String.format(
                "No process definition found with processDefinitionKey: '%s'",
                processDefinitionKey),
            ex);
      }
      throw new TasklistRuntimeException(ex.getMessage(), ex);
    } catch (ClientException ex) {
      throw new TasklistRuntimeException(ex.getMessage(), ex);
    }

    return new ProcessInstanceDTO().setId(processInstanceEvent.getProcessInstanceKey());
  }

  private Object extractTypedValue(VariableInputDTO variable) {
    try {
      return objectMapper.readValue(variable.getValue(), Object.class);
    } catch (IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }
}
