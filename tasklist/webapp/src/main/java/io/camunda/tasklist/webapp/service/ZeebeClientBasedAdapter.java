/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.service;

import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import io.camunda.tasklist.webapp.security.tenant.TenantService;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientStatusException;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.gateway.cmd.ClientException;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.grpc.Status;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("standalone")
public class ZeebeClientBasedAdapter implements TasklistServicesAdapter {

  private static final Logger LOGGER = LoggerFactory.getLogger(ZeebeClientBasedAdapter.class);

  private final ZeebeClient zeebeClient;
  private final TenantService tenantService;

  public ZeebeClientBasedAdapter(
      @Qualifier("tasklistZeebeClient") final ZeebeClient zeebeClient,
      final TenantService tenantService) {
    this.zeebeClient = zeebeClient;
    this.tenantService = tenantService;
  }

  @Override
  public ProcessInstanceCreationRecord createProcessInstance(
      final String bpmnProcessId, final Map<String, Object> variables, final String tenantId) {
    return doCreateProcessInstance(bpmnProcessId, variables, tenantId);
  }

  @Override
  public ProcessInstanceCreationRecord createProcessInstanceWithoutAuthentication(
      final String bpmnProcessId, final Map<String, Object> variables, final String tenantId) {
    return doCreateProcessInstance(bpmnProcessId, variables, tenantId);
  }

  @Override
  public void assignUserTask(final TaskEntity task, final String assignee) {
    if (!isJobBasedUserTask(task)) {
      try {
        zeebeClient.newUserTaskAssignCommand(task.getKey()).assignee(assignee).send().join();
      } catch (final ClientException exception) {
        throw new TasklistRuntimeException(exception.getMessage());
      }
    }
  }

  @Override
  public void unassignUserTask(final TaskEntity task) {
    if (!isJobBasedUserTask(task)) {
      try {
        zeebeClient.newUserTaskUnassignCommand(task.getKey()).send().join();
      } catch (final ClientException exception) {
        throw new TasklistRuntimeException(exception.getMessage());
      }
    }
  }

  @Override
  public void completeUserTask(final TaskEntity task, final Map<String, Object> variables) {
    try {
      if (isJobBasedUserTask(task)) {
        zeebeClient.newCompleteCommand(task.getKey()).variables(variables).send().join();
      } else {
        zeebeClient.newUserTaskCompleteCommand(task.getKey()).variables(variables).send().join();
      }
    } catch (final ClientException exception) {
      throw new TasklistRuntimeException(exception.getMessage());
    }
  }

  private ProcessInstanceCreationRecord doCreateProcessInstance(
      final String bpmnProcessId, final Map<String, Object> variables, final String tenantId) {
    final var createProcessInstanceCommandStep3 =
        zeebeClient.newCreateInstanceCommand().bpmnProcessId(bpmnProcessId).latestVersion();

    if (tenantService.isMultiTenancyEnabled()) {
      createProcessInstanceCommandStep3.tenantId(tenantId);
    }

    if (variables != null && !variables.isEmpty()) {
      createProcessInstanceCommandStep3.variables(variables);
    }

    ProcessInstanceEvent processInstanceEvent = null;
    try {
      processInstanceEvent = createProcessInstanceCommandStep3.send().join();
      LOGGER.debug("Process instance created for process [{}]", bpmnProcessId);
    } catch (final ClientStatusException ex) {
      if (Status.Code.NOT_FOUND.equals(ex.getStatusCode())) {
        throw new NotFoundApiException(
            String.format(
                "No process definition found with processDefinitionKey: '%s'", bpmnProcessId),
            ex);
      }
      throw new TasklistRuntimeException(ex.getMessage(), ex);
    } catch (final ClientException ex) {
      throw new TasklistRuntimeException(ex.getMessage(), ex);
    }

    return new ProcessInstanceCreationRecord()
        .setProcessInstanceKey(processInstanceEvent.getProcessInstanceKey());
  }

  @Override
  public boolean supportAuthenticatedRequests() {
    return false;
  }
}
