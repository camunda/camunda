/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.permission;

import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authorization;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.impl.AuthorizationChecker;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.webapps.schema.entities.tasklist.TaskEntity;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import org.springframework.stereotype.Component;

@Component
public class TasklistPermissionServices {

  private static final Authorization CREATE_PROC_INST_AUTH_CHECK =
      Authorization.of(a -> a.processDefinition().createProcessInstance());
  private static final Authorization UPDATE_USER_TASK_AUTH_CHECK =
      Authorization.of(a -> a.processDefinition().updateUserTask());

  private final SecurityConfiguration securityConfiguration;
  private final AuthorizationChecker authorizationChecker;
  private final SecurityContextProvider securityContextProvider;

  public TasklistPermissionServices(
      final SecurityConfiguration securityConfiguration,
      final AuthorizationChecker authorizationChecker,
      final SecurityContextProvider securityContextProvider) {
    this.securityConfiguration = securityConfiguration;
    this.authorizationChecker = authorizationChecker;
    this.securityContextProvider = securityContextProvider;
  }

  public boolean hasPermissionToCreateProcessInstance(final String bpmnProcessId) {
    final var authentication = RequestMapper.getAuthentication();
    return isAuthorizedForResource(bpmnProcessId, authentication, CREATE_PROC_INST_AUTH_CHECK);
  }

  public boolean hasPermissionToUpdateUserTask(final TaskEntity task) {
    final var authentication = RequestMapper.getAuthentication();
    return isAuthorizedForResource(
        task.getBpmnProcessId(), authentication, UPDATE_USER_TASK_AUTH_CHECK);
  }

  private boolean isAuthorizedForResource(
      final String resourceId,
      final Authentication authentication,
      final Authorization authorization) {

    if (isAuthorizationCheckDisabled(authentication)) {
      return true;
    }

    final var securityContext =
        securityContextProvider.provideSecurityContext(authentication, authorization);
    return authorizationChecker.isAuthorized(resourceId, securityContext);
  }

  private boolean isAuthorizationCheckDisabled(final Authentication authentication) {
    return isAuthorizationDisabled() || isWithoutAuthenticatedUserKey(authentication);
  }

  private boolean isAuthorizationDisabled() {
    return !securityConfiguration.getAuthorizations().isEnabled();
  }

  private boolean isWithoutAuthenticatedUserKey(final Authentication authentication) {
    // when the provided authentication does not contain a user key,
    // then the authorization check cannot be performed.
    // the authorization key is only provided when running with profile "auth-basic",
    // in other cases the authorization check must not happen
    return authentication == null || authentication.authenticatedUserKey() == null;
  }
}
