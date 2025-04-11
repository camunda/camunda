/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.service;

import static io.camunda.tasklist.util.ErrorHandlingUtils.getErrorMessage;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.client.api.command.DeployResourceCommandStep1.DeployResourceCommandStep2;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.util.ConditionalOnTasklistCompatibility;
import io.camunda.tasklist.webapp.permission.TasklistPermissionServices;
import io.camunda.tasklist.webapp.rest.exception.ForbiddenActionException;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import io.camunda.tasklist.webapp.tenant.TenantService;
import io.camunda.tasklist.zeebe.TasklistServicesAdapter;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.grpc.Status;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnTasklistCompatibility(enabled = "true")
public class CamundaClientBasedAdapter implements TasklistServicesAdapter {

  private static final Logger LOGGER = LoggerFactory.getLogger(CamundaClientBasedAdapter.class);

  private final CamundaClient camundaClient;
  private final TasklistPermissionServices permissionServices;
  private final TenantService tenantService;

  public CamundaClientBasedAdapter(
      @Qualifier("tasklistCamundaClient") final CamundaClient camundaClient,
      final TasklistPermissionServices permissionServices,
      final TenantService tenantService) {
    this.camundaClient = camundaClient;
    this.permissionServices = permissionServices;
    this.tenantService = tenantService;
  }

  @Override
  public ProcessInstanceCreationRecord createProcessInstance(
      final String bpmnProcessId, final Map<String, Object> variables, final String tenantId) {
    if (isNotAuthorizedToCreateProcessInstance(bpmnProcessId)) {
      throw new ForbiddenActionException(
          "Not allowed to create process of process definition %s.".formatted(bpmnProcessId));
    }
    return doCreateProcessInstance(bpmnProcessId, variables, tenantId);
  }

  @Override
  public ProcessInstanceCreationRecord createProcessInstanceWithoutAuthentication(
      final String bpmnProcessId, final Map<String, Object> variables, final String tenantId) {
    return doCreateProcessInstance(bpmnProcessId, variables, tenantId);
  }

  @Override
  public void assignUserTask(final TaskEntity task, final String assignee) {
    if (isNotAuthorizedToAssignUserTask(task)) {
      throw new ForbiddenActionException(
          "Not allowed to assign user task %s.".formatted(task.getKey()));
    }

    if (!isJobBasedUserTask(task)) {
      try {
        camundaClient.newUserTaskAssignCommand(task.getKey()).assignee(assignee).send().join();
      } catch (final ClientException exception) {
        throw new TasklistRuntimeException(getErrorMessage(exception));
      }
    }
  }

  @Override
  public void unassignUserTask(final TaskEntity task) {
    if (isNotAuthorizedToAssignUserTask(task)) {
      throw new ForbiddenActionException(
          "Not allowed to unassign user task %s.".formatted(task.getKey()));
    }

    if (!isJobBasedUserTask(task)) {
      try {
        camundaClient.newUserTaskUnassignCommand(task.getKey()).send().join();
      } catch (final ClientException exception) {
        throw new TasklistRuntimeException(getErrorMessage(exception));
      }
    }
  }

  @Override
  public void completeUserTask(final TaskEntity task, final Map<String, Object> variables) {
    if (isNotAuthorizedToAssignUserTask(task)) {
      throw new ForbiddenActionException(
          "Not allowed to complete user task %s.".formatted(task.getKey()));
    }

    try {
      if (isJobBasedUserTask(task)) {
        camundaClient.newCompleteCommand(task.getKey()).variables(variables).send().join();
      } else {
        camundaClient.newUserTaskCompleteCommand(task.getKey()).variables(variables).send().join();
      }
    } catch (final ClientException exception) {
      throw new TasklistRuntimeException(getErrorMessage(exception));
    }
  }

  @Override
  public void deployResourceWithoutAuthentication(
      final String classpathResource, final String tenantId) {
    final DeployResourceCommandStep2 deployResourceCommandStep2 =
        camundaClient.newDeployResourceCommand().addResourceFromClasspath(classpathResource);
    if (tenantService.isMultiTenancyEnabled()) {
      deployResourceCommandStep2.tenantId(tenantId);
    }
    deployResourceCommandStep2.send();
  }

  private ProcessInstanceCreationRecord doCreateProcessInstance(
      final String bpmnProcessId, final Map<String, Object> variables, final String tenantId) {
    final var createProcessInstanceCommandStep3 =
        camundaClient.newCreateInstanceCommand().bpmnProcessId(bpmnProcessId).latestVersion();

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

  private boolean isNotAuthorizedToAssignUserTask(final TaskEntity task) {
    return !permissionServices.hasPermissionToUpdateUserTask(task);
  }

  private boolean isNotAuthorizedToCreateProcessInstance(final String bpmnProcessId) {
    return !permissionServices.hasPermissionToCreateProcessInstance(bpmnProcessId);
  }
}
