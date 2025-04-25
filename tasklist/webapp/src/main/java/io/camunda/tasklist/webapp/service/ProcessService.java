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
import io.camunda.tasklist.store.ProcessStore;
import io.camunda.tasklist.webapp.dto.ProcessInstanceDTO;
import io.camunda.tasklist.webapp.dto.VariableInputDTO;
import io.camunda.tasklist.webapp.permission.TasklistPermissionServices;
import io.camunda.tasklist.webapp.rest.exception.ForbiddenActionException;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.security.UserReader;
import io.camunda.tasklist.webapp.tenant.TenantService;
import io.camunda.tasklist.zeebe.TasklistServicesAdapter;
import io.camunda.webapps.schema.entities.ProcessEntity;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired private TenantService tenantService;

  @Autowired private UserReader userReader;

  @Autowired private ProcessStore processStore;

  @Autowired private TasklistServicesAdapter tasklistServicesAdapter;

  @Autowired private TasklistPermissionServices permissionServices;

  public ProcessEntity getProcessByProcessDefinitionKeyAndAccessRestriction(
      final String processDefinitionKey) {
    final var processEntity = processStore.getProcessByProcessDefinitionKey(processDefinitionKey);
    final var bpmnProcessId = processEntity.getBpmnProcessId();
    if (permissionServices.hasPermissionToReadProcessDefinition(bpmnProcessId)) {
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
    return startProcessInstance(processDefinitionKey, variables, tenantId, true);
  }

  public ProcessInstanceDTO startProcessInstance(
      final String processDefinitionKey,
      final List<VariableInputDTO> variables,
      final String tenantId,
      final boolean executeWithAuthentication) {

    final boolean isMultiTenancyEnabled = tenantService.isMultiTenancyEnabled();

    if (isMultiTenancyEnabled && !tenantService.getAuthenticatedTenants().contains(tenantId)) {
      throw new InvalidRequestException("Invalid tenant.");
    }

    final Map<String, Object> variablesMap;
    if (CollectionUtils.isNotEmpty(variables)) {
      variablesMap =
          variables.stream()
              .collect(Collectors.toMap(VariableInputDTO::getName, this::extractTypedValue));
    } else {
      variablesMap = null;
    }

    final var processInstanceEvent =
        Optional.of(executeWithAuthentication)
            .filter(Boolean::booleanValue)
            .map(
                i ->
                    tasklistServicesAdapter.createProcessInstance(
                        processDefinitionKey, variablesMap, tenantId))
            .orElseGet(
                () ->
                    tasklistServicesAdapter.createProcessInstanceWithoutAuthentication(
                        processDefinitionKey, variablesMap, tenantId));
    LOGGER.debug("Process instance created for process [{}]", processDefinitionKey);
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
