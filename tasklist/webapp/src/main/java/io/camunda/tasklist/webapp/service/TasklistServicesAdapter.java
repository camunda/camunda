/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.service;

import io.camunda.security.auth.Authentication;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCreateRequest;
import io.camunda.service.UserTaskServices;
import io.camunda.service.exception.CamundaBrokerException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.webapp.rest.exception.ForbiddenActionException;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import io.camunda.tasklist.webapp.security.permission.TasklistPermissionServices;
import io.camunda.tasklist.webapp.security.tenant.TenantService;
import io.camunda.webapps.schema.entities.tasklist.TaskEntity;
import io.camunda.webapps.schema.entities.tasklist.TaskEntity.TaskImplementation;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.validator.MultiTenancyValidator;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.RejectionType;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class TasklistServicesAdapter {

  private final TenantService tenantService;
  private final ProcessInstanceServices processInstanceServices;
  private final UserTaskServices userTaskServices;
  private final TasklistPermissionServices permissionServices;

  public TasklistServicesAdapter(
      final TenantService tenantService,
      final ProcessInstanceServices processInstanceServices,
      final UserTaskServices userTaskServices,
      final TasklistPermissionServices permissionServices) {
    this.tenantService = tenantService;
    this.processInstanceServices = processInstanceServices;
    this.userTaskServices = userTaskServices;
    this.permissionServices = permissionServices;
  }

  public ProcessInstanceCreationRecord createProcessInstance(
      final String bpmnProcessId, final Map<String, Object> variables, final String tenantId) {
    return executeCamundaServiceAuthenticated(
        (authentication) ->
            processInstanceServices
                .withAuthentication(authentication)
                .createProcessInstance(
                    toProcessInstanceCreateRequest(bpmnProcessId, variables, tenantId)));
  }

  public ProcessInstanceCreationRecord createProcessInstanceWithoutAuthentication(
      final String bpmnProcessId, final Map<String, Object> variables, final String tenantId) {
    return executeCamundaServiceAnonymously(
        (authentication) ->
            processInstanceServices
                .withAuthentication(authentication)
                .createProcessInstance(
                    toProcessInstanceCreateRequest(bpmnProcessId, variables, tenantId)));
  }

  public void assignUserTask(final TaskEntity task, final String assignee) {
    if (!isJobBasedUserTask(task)) {
      assignCamundaUserTask(task, assignee);
    } else if (isNotAuthorizedToAssignJobBasedUserTask(task)) {
      throw new ForbiddenActionException("Not allowed to assign user task.");
    }
  }

  private boolean isNotAuthorizedToAssignJobBasedUserTask(final TaskEntity task) {
    return !permissionServices.hasPermissionToUpdateJobBasedUserTask(task);
  }

  private void assignCamundaUserTask(final TaskEntity task, final String assignee) {
    executeCamundaServiceAuthenticated(
        (authentication) ->
            userTaskServices
                .withAuthentication(authentication)
                .assignUserTask(task.getKey(), assignee, "", true));
  }

  private ProcessInstanceCreateRequest toProcessInstanceCreateRequest(
      final String bpmnProcessId, final Map<String, Object> variables, final String tenantId) {
    final var tenantValidationResult =
        MultiTenancyValidator.validateTenantId(
            tenantId, tenantService.isMultiTenancyEnabled(), "Create Process Instance");

    if (tenantValidationResult.isLeft()) {
      throw new InvalidRequestException(tenantValidationResult.getLeft().getDetail());
    }

    final var tenant = tenantValidationResult.get();
    return new ProcessInstanceCreateRequest(
        -1L, bpmnProcessId, -1, variables, tenant, null, null, null, List.of(), null);
  }

  private boolean isJobBasedUserTask(final TaskEntity task) {
    return task.getImplementation().equals(TaskImplementation.JOB_WORKER);
  }

  private <T> T executeCamundaServiceAuthenticated(
      final Function<Authentication, CompletableFuture<T>> method) {
    return executeCamundaService(method, RequestMapper.getAuthentication());
  }

  private <T> T executeCamundaServiceAnonymously(
      final Function<Authentication, CompletableFuture<T>> method) {
    return executeCamundaService(method, RequestMapper.getAnonymousAuthentication());
  }

  private <T> T executeCamundaService(
      final Function<Authentication, CompletableFuture<T>> method,
      final Authentication authentication) {
    try {
      return method.apply(authentication).join();
    } catch (final Exception e) {
      throw handleException(e);
    }
  }

  private RuntimeException handleException(final Throwable error) {
    return switch (error) {
      case final CompletionException ce -> handleException(ce.getCause());
      case final CamundaBrokerException cbe -> mapCamundaBrokerException(cbe);
      default -> new RuntimeException("Failed to execute request: " + error.getMessage(), error);
    };
  }

  private RuntimeException mapCamundaBrokerException(final CamundaBrokerException exception) {
    if (exception.getCause() instanceof final BrokerRejectionException brokerRejection) {
      final var rejection = brokerRejection.getRejection();
      final String message =
          String.format(
              "Request '%s' rejected with code '%s': %s",
              rejection.intent(), rejection.type(), rejection.reason());
      final var type = rejection.type();
      if (type.equals(RejectionType.NOT_FOUND)) {
        return new NotFoundApiException(message, exception);
      }
      if (type.equals(RejectionType.UNAUTHORIZED)) {
        return new ForbiddenActionException(message, exception);
      }
    }
    if (exception.getCause() instanceof final BrokerErrorException brokerError) {
      final var errorCode = brokerError.getError().getCode();
      if (errorCode.equals(ErrorCode.PROCESS_NOT_FOUND)) {
        return new ForbiddenActionException("Process not found", exception);
      }
    }
    return new TasklistRuntimeException(
        String.format("Failed to execute request with %s", exception.getMessage()), exception);
  }
}
