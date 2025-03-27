/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.service;

import io.camunda.client.impl.command.StreamUtil;
import io.camunda.security.auth.Authentication;
import io.camunda.service.JobServices;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCreateRequest;
import io.camunda.service.ResourceServices;
import io.camunda.service.ResourceServices.DeployResourcesRequest;
import io.camunda.service.UserTaskServices;
import io.camunda.service.exception.CamundaBrokerException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.util.ConditionalOnTasklistCompatibility;
import io.camunda.tasklist.webapp.permission.TasklistPermissionServices;
import io.camunda.tasklist.webapp.rest.exception.ForbiddenActionException;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import io.camunda.tasklist.webapp.security.tenant.TenantService;
import io.camunda.tasklist.zeebe.TasklistServicesAdapter;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.validator.MultiTenancyValidator;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.RejectionType;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnTasklistCompatibility(enabled = "false", matchIfMissing = true)
public class CamundaServicesBasedAdapter implements TasklistServicesAdapter {

  private final TenantService tenantService;
  private final ProcessInstanceServices processInstanceServices;
  private final ResourceServices resourceServices;
  private final UserTaskServices userTaskServices;
  private final JobServices<?> jobServices;
  private final TasklistPermissionServices permissionServices;

  public CamundaServicesBasedAdapter(
      final TenantService tenantService,
      final ProcessInstanceServices processInstanceServices,
      final ResourceServices resourceServices,
      final UserTaskServices userTaskServices,
      final JobServices<?> jobServices,
      final TasklistPermissionServices permissionServices) {
    this.tenantService = tenantService;
    this.processInstanceServices = processInstanceServices;
    this.resourceServices = resourceServices;
    this.userTaskServices = userTaskServices;
    this.jobServices = jobServices;
    this.permissionServices = permissionServices;
  }

  @Override
  public ProcessInstanceCreationRecord createProcessInstance(
      final String bpmnProcessId, final Map<String, Object> variables, final String tenantId) {
    return executeCamundaServiceAuthenticated(
        (authentication) ->
            processInstanceServices
                .withAuthentication(authentication)
                .createProcessInstance(
                    toProcessInstanceCreateRequest(bpmnProcessId, variables, tenantId)));
  }

  @Override
  public ProcessInstanceCreationRecord createProcessInstanceWithoutAuthentication(
      final String bpmnProcessId, final Map<String, Object> variables, final String tenantId) {
    return executeCamundaServiceAnonymously(
        (authentication) ->
            processInstanceServices
                .withAuthentication(authentication)
                .createProcessInstance(
                    toProcessInstanceCreateRequest(bpmnProcessId, variables, tenantId)));
  }

  @Override
  public void assignUserTask(final TaskEntity task, final String assignee) {
    if (!isJobBasedUserTask(task)) {
      assignCamundaUserTask(task, assignee);
    } else if (isNotAuthorizedToAssignJobBasedUserTask(task)) {
      throw new ForbiddenActionException("Not allowed to assign user task.");
    }
  }

  @Override
  public void unassignUserTask(final TaskEntity task) {
    if (!isJobBasedUserTask(task)) {
      unassignCamundaUserTask(task);
    } else if (isNotAuthorizedToUnassignJobBasedUserTask(task)) {
      throw new ForbiddenActionException("Not allowed to unassign user task.");
    }
  }

  @Override
  public void completeUserTask(final TaskEntity task, final Map<String, Object> variables) {
    if (isJobBasedUserTask(task)) {
      completeJobBasedUserTask(task, variables);
    } else {
      completeCamundaUserTask(task, variables);
    }
  }

  @Override
  public void deployResourceWithoutAuthentication(
      final String classpathResource, final String tenantId) {
    try (final InputStream resourceStream =
        getClass().getClassLoader().getResourceAsStream(classpathResource)) {
      if (resourceStream != null) {
        final byte[] bytes = StreamUtil.readInputStream(resourceStream);
        executeCamundaServiceAnonymously(
            (authentication) ->
                resourceServices
                    .withAuthentication(authentication)
                    .deployResources(toDeployResourcesRequest(classpathResource, bytes, tenantId)));
      } else {
        throw new FileNotFoundException(classpathResource);
      }
    } catch (final IOException e) {
      final String exceptionMsg =
          String.format("Cannot deploy resource from classpath. %s", e.getMessage());
      throw new TasklistRuntimeException(exceptionMsg, e);
    }
  }

  private boolean isNotAuthorizedToAssignJobBasedUserTask(final TaskEntity task) {
    return !permissionServices.hasPermissionToUpdateUserTask(task);
  }

  private void assignCamundaUserTask(final TaskEntity task, final String assignee) {
    executeCamundaServiceAuthenticated(
        (authentication) ->
            userTaskServices
                .withAuthentication(authentication)
                .assignUserTask(task.getKey(), assignee, "", true));
  }

  private boolean isNotAuthorizedToUnassignJobBasedUserTask(final TaskEntity task) {
    return !permissionServices.hasPermissionToUpdateUserTask(task);
  }

  private void unassignCamundaUserTask(final TaskEntity task) {
    executeCamundaServiceAuthenticated(
        (authentication) ->
            userTaskServices
                .withAuthentication(authentication)
                .unassignUserTask(task.getKey(), ""));
  }

  private boolean isNotAuthorizedToCompleteJobBasedUserTask(final TaskEntity task) {
    return !permissionServices.hasPermissionToUpdateUserTask(task);
  }

  private void completeJobBasedUserTask(
      final TaskEntity task, final Map<String, Object> variables) {
    if (isNotAuthorizedToCompleteJobBasedUserTask(task)) {
      throw new ForbiddenActionException("Not allowed to complete user task.");
    }

    executeCamundaServiceAnonymously(
        (authentication) ->
            jobServices
                .withAuthentication(authentication)
                .completeJob(task.getKey(), variables, null));
  }

  private void completeCamundaUserTask(final TaskEntity task, final Map<String, Object> variables) {
    executeCamundaServiceAuthenticated(
        (authentication) ->
            userTaskServices
                .withAuthentication(authentication)
                .completeUserTask(task.getKey(), variables, ""));
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

  private DeployResourcesRequest toDeployResourcesRequest(
      final String classpathResource, final byte[] bytes, final String tenantId) {
    final var tenantValidationResult =
        MultiTenancyValidator.validateTenantId(
            tenantId, tenantService.isMultiTenancyEnabled(), "Deploy Resources");

    if (tenantValidationResult.isLeft()) {
      throw new InvalidRequestException(tenantValidationResult.getLeft().getDetail());
    }

    final var tenant = tenantValidationResult.get();
    return new DeployResourcesRequest(Map.of(classpathResource, bytes), tenant);
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
      if (type.equals(RejectionType.UNAUTHORIZED) || type.equals(RejectionType.FORBIDDEN)) {
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
