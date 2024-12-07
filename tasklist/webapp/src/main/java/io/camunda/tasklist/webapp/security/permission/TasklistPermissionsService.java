/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.permission;

import io.camunda.authentication.entity.CamundaUser;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authorization;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.impl.AuthorizationChecker;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.tasklist.property.IdentityProperties;
import io.camunda.webapps.schema.entities.tasklist.TaskEntity;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class TasklistPermissionsService {

  private static final Authorization UPDATE_PROC_INST_AUTH_CHECK =
      Authorization.of(a -> a.processDefinition().updateProcessInstance());
  private static final Authorization UPDATE_USER_TASK_AUTH_CHECK =
      Authorization.of(a -> a.processDefinition().updateUserTask());

  private final SecurityConfiguration securityConfiguration;
  private final AuthorizationChecker authorizationChecker;
  private final SecurityContextProvider securityContextProvider;

  public TasklistPermissionsService(
      final SecurityConfiguration securityConfiguration,
      final AuthorizationChecker authorizationChecker,
      final SecurityContextProvider securityContextProvider) {
    this.securityConfiguration = securityConfiguration;
    this.authorizationChecker = authorizationChecker;
    this.securityContextProvider = securityContextProvider;
  }

  public List<String> getProcessDefinitionIdsWithReadPermission() {
    return getResourceIdsWithPermission(PermissionType.READ);
  }

  public List<String> getProcessDefinitionIdsWithCreateInstancePermission() {
    return getResourceIdsWithPermission(PermissionType.CREATE_PROCESS_INSTANCE);
  }

  private List<String> getResourceIdsWithPermission(final PermissionType permission) {
    final var authentication = getAuthentication();
    if (isAuthorizationCheckDisabled(authentication)) {
      return List.of(IdentityProperties.ALL_RESOURCES);
    }

    final var authorization =
        Authorization.of(
            a ->
                a.resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                    .permissionType(permission));
    final var securityContext =
        securityContextProvider.provideSecurityContext(authentication, authorization);
    return authorizationChecker.retrieveAuthorizedResourceKeys(securityContext);
  }

  public boolean hasPermissionToUpdateJobBasedUserTask(final TaskEntity task) {
    final var authentication = getAuthentication();
    return hasPermissionToUpdateUserTask(task, authentication)
        || hasPermissionToUpdateProcessInstance(task, authentication);
  }

  private boolean hasPermissionToUpdateUserTask(
      final TaskEntity task, final Authentication authentication) {
    return isAuthorizedForResource(
        task.getBpmnProcessId(), authentication, UPDATE_USER_TASK_AUTH_CHECK);
  }

  private boolean hasPermissionToUpdateProcessInstance(
      final TaskEntity task, final Authentication authentication) {
    return isAuthorizedForResource(
        task.getBpmnProcessId(), authentication, UPDATE_PROC_INST_AUTH_CHECK);
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

  private Authentication getAuthentication() {
    final Long authenticatedUserKey = getAuthenticatedUserKey();
    // groups and roles will come later
    return new Authentication.Builder().user(authenticatedUserKey).build();
  }

  private Long getAuthenticatedUserKey() {
    final var requestAuthentication = SecurityContextHolder.getContext().getAuthentication();
    if (requestAuthentication != null) {
      final Object principal = requestAuthentication.getPrincipal();
      if (principal instanceof final CamundaUser authenticatedPrincipal) {
        return authenticatedPrincipal.getUserKey();
      }
    }
    return null;
  }
}
