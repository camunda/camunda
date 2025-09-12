/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.permission;

import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.reader.ResourceAccessProvider;
import io.camunda.tasklist.util.LazySupplier;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TasklistPermissionServices {
  public static final String WILDCARD_RESOURCE = "*";
  private static final Authorization<?> READ_PROC_DEF_AUTH_CHECK =
      Authorization.of(a -> a.processDefinition().readProcessDefinition());
  private static final Authorization<?> CREATE_PROC_INST_AUTH_CHECK =
      Authorization.of(a -> a.processDefinition().createProcessInstance());
  private static final Authorization<?> UPDATE_USER_TASK_AUTH_CHECK =
      Authorization.of(a -> a.processDefinition().updateUserTask());
  private static final Authorization<?> READ_USER_TASK_AUTH_CHECK =
      Authorization.of(a -> a.processDefinition().readUserTask());
  private static final Authorization<?> READ_PROCESS_INSTANCE_AUTH_CHECK =
      Authorization.of(a -> a.processDefinition().readProcessInstance());

  private final CamundaAuthenticationProvider authenticationProvider;
  private final ResourceAccessProvider resourceAccessProvider;

  public TasklistPermissionServices(
      final CamundaAuthenticationProvider authenticationProvider,
      final ResourceAccessProvider resourceAccessProvider) {
    this.authenticationProvider = authenticationProvider;
    this.resourceAccessProvider = resourceAccessProvider;
  }

  public boolean hasPermissionToCreateProcessInstance(final String bpmnProcessId) {
    return isAuthorizedForResource(bpmnProcessId, CREATE_PROC_INST_AUTH_CHECK);
  }

  public boolean hasPermissionToReadProcessDefinition(final String bpmnProcessId) {
    return isAuthorizedForResource(bpmnProcessId, READ_PROC_DEF_AUTH_CHECK);
  }

  public boolean hasPermissionToUpdateUserTask(final TaskEntity task) {
    return isAuthorizedForResource(task.getBpmnProcessId(), UPDATE_USER_TASK_AUTH_CHECK);
  }

  public boolean hasWildcardPermissionToReadUserTask() {
    return isAuthorizedForResource(WILDCARD_RESOURCE, READ_USER_TASK_AUTH_CHECK);
  }

  public boolean hasWildcardPermissionToReadProcessInstance() {
    return isAuthorizedForResource(WILDCARD_RESOURCE, READ_PROCESS_INSTANCE_AUTH_CHECK);
  }

  public List<String> getProcessDefinitionsWithCreateProcessInstancePermission() {
    final var authenticationSupplier =
        LazySupplier.of(authenticationProvider::getCamundaAuthentication);

    final var resourceAccess =
        resourceAccessProvider.resolveResourceAccess(
            authenticationSupplier.get(), CREATE_PROC_INST_AUTH_CHECK);
    return resourceAccess.allowed() ? resourceAccess.authorization().resourceIds() : List.of();
  }

  public List<String> getProcessDefinitionsWithReadUserTaskPermission() {
    final var authenticationSupplier =
        LazySupplier.of(authenticationProvider::getCamundaAuthentication);

    final var resourceAccess =
        resourceAccessProvider.resolveResourceAccess(
            authenticationSupplier.get(), READ_USER_TASK_AUTH_CHECK);

    return resourceAccess.allowed() ? resourceAccess.authorization().resourceIds() : List.of();
  }

  private boolean isAuthorizedForResource(
      final String resourceId, final Authorization<?> authorization) {

    final var authenticationSupplier =
        LazySupplier.of(authenticationProvider::getCamundaAuthentication);

    return resourceAccessProvider
        .hasResourceAccessByResourceId(authenticationSupplier.get(), authorization, resourceId)
        .allowed();
  }
}
