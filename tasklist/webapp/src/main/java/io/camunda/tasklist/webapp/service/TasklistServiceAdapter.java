/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.service;

import static io.camunda.zeebe.protocol.record.RejectionType.NOT_FOUND;
import static io.camunda.zeebe.protocol.record.RejectionType.UNAUTHORIZED;

import io.camunda.security.auth.Authentication;
import io.camunda.service.JobServices;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCreateRequest;
import io.camunda.service.UserTaskServices;
import io.camunda.service.exception.CamundaBrokerException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.webapp.graphql.entity.ProcessInstanceDTO;
import io.camunda.tasklist.webapp.rest.exception.ForbiddenActionException;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import io.camunda.tasklist.webapp.security.tenant.TenantService;
import io.camunda.webapps.schema.entities.tasklist.TaskEntity;
import io.camunda.webapps.schema.entities.tasklist.TaskEntity.TaskImplementation;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import org.springframework.stereotype.Service;

@Service
public class TasklistServiceAdapter {

  private final ProcessInstanceServices processInstanceServices;
  private final UserTaskServices userTaskServices;
  private final JobServices<?> jobServices;
  private final TenantService tenantService;

  public TasklistServiceAdapter(
      final ProcessInstanceServices processInstanceServices,
      final UserTaskServices userTaskServices,
      final JobServices<?> jobServices,
      final TenantService tenantService) {
    this.processInstanceServices = processInstanceServices;
    this.userTaskServices = userTaskServices;
    this.jobServices = jobServices;
    this.tenantService = tenantService;
  }

  public ProcessInstanceDTO createProcessInstance(
      final String bpmnProcessId, final Map<String, Object> variables, final String tenantId) {
    final var event =
        executeCamundaServiceWithAuthentication(
            (authentication) ->
                processInstanceServices
                    .withAuthentication(authentication)
                    .createProcessInstance(
                        toProcessInstanceCreateRequest(bpmnProcessId, variables, tenantId)));
    return new ProcessInstanceDTO().setId(event.getProcessInstanceKey());
  }

  public void assignUserTask(final TaskEntity task, final String assignee) {
    executeCamundaServiceWithAuthentication(
        (authentication) ->
            userTaskServices
                .withAuthentication(authentication)
                .assignUserTask(task.getKey(), assignee, "", true));
  }

  public void unassignUserTask(final TaskEntity task) {
    executeCamundaServiceWithAuthentication(
        (authentication) ->
            userTaskServices
                .withAuthentication(authentication)
                .unassignUserTask(task.getKey(), ""));
  }

  public void completeJobBasedUserTask(final TaskEntity task, final Map<String, Object> variables) {
    executeCamundaServiceWithAuthentication(
        (authentication) ->
            jobServices
                .withAuthentication(authentication)
                .completeJob(task.getKey(), variables, null));
  }

  public void completeCamundaUserTask(final TaskEntity task, final Map<String, Object> variables) {
    executeCamundaServiceWithAuthentication(
        (authentication) ->
            userTaskServices
                .withAuthentication(authentication)
                .completeUserTask(task.getKey(), variables, ""));
  }

  public void completeUserTask(final TaskEntity task, final Map<String, Object> variables) {
    if (isJobBasedUserTask(task)) {
      completeJobBasedUserTask(task, variables);
    } else {
      completeCamundaUserTask(task, variables);
    }
  }

  private boolean isJobBasedUserTask(final TaskEntity task) {
    return task.getImplementation().equals(TaskImplementation.JOB_WORKER);
  }

  private ProcessInstanceCreateRequest toProcessInstanceCreateRequest(
      final String bpmnProcessId, final Map<String, Object> variables, final String tenantId) {
    return new ProcessInstanceCreateRequest(
        -1L, bpmnProcessId, -1, variables, toTenant(tenantId), null, null, null, List.of(), null);
  }

  private String toTenant(final String tenant) {
    return tenant == null || tenant.isEmpty() ? TenantOwned.DEFAULT_TENANT_IDENTIFIER : tenant;
  }

  private <T> T executeCamundaServiceWithAuthentication(
      final Function<Authentication, CompletableFuture<T>> method) {
    try {
      return method.apply(RequestMapper.getAuthentication()).join();
    } catch (final Exception e) {
      throw handleException(e);
    }
  }

  private RuntimeException handleException(final Throwable error) {
    return switch (error) {
      case final CompletionException ce -> handleException(ce.getCause());
      case final CamundaBrokerException cbe -> mapCamundaBrokerException(cbe);
      default ->
          throw new RuntimeException("Failed to execute request: " + error.getMessage(), error);
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
      if (type.equals(NOT_FOUND)) {
        return new NotFoundApiException(message, exception);
      }
      if (type.equals(UNAUTHORIZED)) {
        return new ForbiddenActionException(message, exception);
      }
    }
    return new TasklistRuntimeException(
        String.format("Failed to execute request with %s", exception.getMessage()), exception);
  }
}
