/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.permission;

import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.reader.ResourceAccessProvider;
import io.camunda.tasklist.util.LazySupplier;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class TasklistPermissionServices {
  public static final String WILDCARD_RESOURCE = "*";
  private static final List<String> WILD_CARD_PERMISSION = List.of(WILDCARD_RESOURCE);
  private static final Authorization<?> READ_PROC_DEF_AUTH_CHECK =
      Authorization.of(a -> a.processDefinition().readProcessDefinition());
  private static final Authorization<?> CREATE_PROC_INST_AUTH_CHECK =
      Authorization.of(a -> a.processDefinition().createProcessInstance());
  private static final Authorization<?> UPDATE_USER_TASK_AUTH_CHECK =
      Authorization.of(a -> a.processDefinition().updateUserTask());
  private static final Authorization<?> READ_USER_TASK_AUTH_CHECK =
      Authorization.of(a -> a.processDefinition().readUserTask());

  private final SecurityConfiguration securityConfiguration;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final ResourceAccessProvider resourceAccessProvider;

  public TasklistPermissionServices(
      final SecurityConfiguration securityConfiguration,
      final CamundaAuthenticationProvider authenticationProvider,
      final ResourceAccessProvider resourceAccessProvider) {
    this.securityConfiguration = securityConfiguration;
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

  public boolean hasPermissionToReadUserTask(final String bpmnProcessId) {
    return isAuthorizedForResource(bpmnProcessId, READ_USER_TASK_AUTH_CHECK);
  }

  public List<String> getProcessDefinitionsWithCreateProcessInstancePermission() {
    final var authenticationSupplier =
        LazySupplier.of(authenticationProvider::getCamundaAuthentication);
    if (isAuthorizationCheckDisabled(authenticationSupplier)) {
      return WILD_CARD_PERMISSION;
    }

    final var resourceAccess =
        resourceAccessProvider.resolveResourceAccess(
            authenticationSupplier.get(), CREATE_PROC_INST_AUTH_CHECK);
    return resourceAccess.allowed() ? resourceAccess.authorization().resourceIds() : List.of();
  }

  public List<String> getProcessDefinitionsWithReadUserTaskPermission() {
    final var authenticationSupplier =
        LazySupplier.of(authenticationProvider::getCamundaAuthentication);
    if (isAuthorizationCheckDisabled(authenticationSupplier)) {
      return WILD_CARD_PERMISSION;
    }

    final var resourceAccess =
        resourceAccessProvider.resolveResourceAccess(
            authenticationSupplier.get(), READ_USER_TASK_AUTH_CHECK);

    return resourceAccess.allowed() ? resourceAccess.authorization().resourceIds() : List.of();
  }

  private boolean isAuthorizedForResource(
      final String resourceId, final Authorization<?> authorization) {

    final var authenticationSupplier =
        LazySupplier.of(authenticationProvider::getCamundaAuthentication);
    if (isAuthorizationCheckDisabled(authenticationSupplier)) {
      return true;
    }

    return resourceAccessProvider
        .hasResourceAccessByResourceId(authenticationSupplier.get(), authorization, resourceId)
        .allowed();
  }

  private boolean isAuthorizationCheckDisabled(
      final Supplier<CamundaAuthentication> authentication) {
    return isAuthorizationDisabled() || isWithoutAuthenticatedUserKey(authentication.get());
  }

  private boolean isAuthorizationDisabled() {
    return !securityConfiguration.getAuthorizations().isEnabled();
  }

  private boolean isWithoutAuthenticatedUserKey(final CamundaAuthentication authentication) {
    // when the provided authentication does not contain a username,
    // then the authorization check cannot be performed.
    // the authorization key is only provided when running with the BASIC authentication method
    // in other cases the authorization check must not happen
    return authentication == null || authentication.authenticatedUsername() == null;
  }
}
