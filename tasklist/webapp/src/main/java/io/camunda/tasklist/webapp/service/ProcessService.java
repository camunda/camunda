/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.IdentityProperties;
import io.camunda.tasklist.store.ProcessStore;
import io.camunda.tasklist.webapp.graphql.entity.ProcessInstanceDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.camunda.tasklist.webapp.permission.TasklistPermissionsService;
import io.camunda.tasklist.webapp.rest.exception.ForbiddenActionException;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import io.camunda.tasklist.webapp.security.UserReader;
import io.camunda.tasklist.webapp.security.tenant.TenantService;
import io.camunda.webapps.schema.entities.operate.ProcessEntity;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class ProcessService {
  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessService.class);

  @Autowired
  @Qualifier("tasklistZeebeClient")
  private ZeebeClient zeebeClient;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired private TenantService tenantService;

  @Autowired private UserReader userReader;

  @Autowired private ProcessStore processStore;

  @Autowired private TasklistPermissionsService permissionsService;

  public ProcessEntity getProcessByProcessDefinitionKeyAndAccessRestriction(
      final String processDefinitionKey) {

    final ProcessEntity processEntity =
        processStore.getProcessByProcessDefinitionKey(processDefinitionKey);

    final List<String> processReadAuthorizations =
        permissionsService.getProcessDefinitionIdsWithReadPermission();

    if (processReadAuthorizations.contains(processEntity.getBpmnProcessId())
        || processReadAuthorizations.contains(IdentityProperties.ALL_RESOURCES)) {
      return processEntity;
    } else {
      throw new ForbiddenActionException("Resource cannot be accessed");
    }
  }

  public ProcessInstanceDTO startProcessInstance(
      final String processDefinitionKey, final String tenantId) {
    return startProcessInstance(processDefinitionKey, null, tenantId);
  }

  public ProcessInstanceDTO startProcessInstance(
      final String processDefinitionKey,
      final List<VariableInputDTO> variables,
      final String tenantId) {
    return startProcessInstance(processDefinitionKey, null, tenantId, true);
  }

  public ProcessInstanceDTO startProcessInstance(
      final String processDefinitionKey,
      final List<VariableInputDTO> variables,
      final String tenantId,
      final boolean doAuthorizationCheck) {

    if (doAuthorizationCheck
        && !permissionsService.hasPermissionToCreateProcessInstance(processDefinitionKey)) {
      throw new ForbiddenActionException(
          "User does not have the permission to start this process.");
    }

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
    } catch (final ClientStatusException ex) {
      if (Status.Code.NOT_FOUND.equals(ex.getStatusCode())) {
        throw new NotFoundApiException(
            String.format(
                "No process definition found with processDefinitionKey: '%s'",
                processDefinitionKey),
            ex);
      }
      throw new TasklistRuntimeException(ex.getMessage(), ex);
    } catch (final ClientException ex) {
      throw new TasklistRuntimeException(ex.getMessage(), ex);
    }

    return new ProcessInstanceDTO().setId(processInstanceEvent.getProcessInstanceKey());
  }

  private Object extractTypedValue(final VariableInputDTO variable) {
    if (variable.getValue().equals("null")) {
      return objectMapper
          .nullNode(); // JSON Object null must be instanced like "null", also should not send to
      // objectMapper null values
    }

    try {
      return objectMapper.readValue(variable.getValue(), Object.class);
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }
}
