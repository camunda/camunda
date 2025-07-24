/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.permission;

import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.AuthorizationScope;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.impl.AuthorizationChecker;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.tasklist.property.IdentityProperties;
import io.camunda.tasklist.util.LazySupplier;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class TasklistPermissionServices {

  private static final List<String> WILD_CARD_PERMISSION =
      List.of(IdentityProperties.ALL_RESOURCES);
  private static final Authorization READ_PROC_DEF_AUTH_CHECK =
      Authorization.of(a -> a.processDefinition().readProcessDefinition());
  private static final Authorization CREATE_PROC_INST_AUTH_CHECK =
      Authorization.of(a -> a.processDefinition().createProcessInstance());
  private static final Authorization UPDATE_USER_TASK_AUTH_CHECK =
      Authorization.of(a -> a.processDefinition().updateUserTask());

  private final SecurityConfiguration securityConfiguration;
  private final SecurityContextProvider securityContextProvider;
  private final AuthorizationChecker authorizationChecker;
  private final CamundaAuthenticationProvider authenticationProvider;

  public TasklistPermissionServices(
      final SecurityConfiguration securityConfiguration,
      final SecurityContextProvider securityContextProvider,
      final AuthorizationChecker authorizationChecker,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.securityConfiguration = securityConfiguration;
    this.securityContextProvider = securityContextProvider;
    this.authorizationChecker = authorizationChecker;
    this.authenticationProvider = authenticationProvider;
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

  public List<String> getProcessDefinitionsWithCreateProcessInstancePermission() {
    final var authenticationSupplier =
        LazySupplier.of(authenticationProvider::getCamundaAuthentication);
    if (isAuthorizationCheckDisabled(authenticationSupplier)) {
      return WILD_CARD_PERMISSION;
    }

    final var securityContext =
        securityContextProvider.provideSecurityContext(
            authenticationSupplier.get(), CREATE_PROC_INST_AUTH_CHECK);
    return authorizationChecker.retrieveAuthorizedResourceKeys(securityContext).stream()
        .map(AuthorizationScope::resourceId)
        .collect(Collectors.toList());
  }

  private boolean isAuthorizedForResource(
      final String resourceId, final Authorization authorization) {

    final var authenticationSupplier =
        LazySupplier.of(authenticationProvider::getCamundaAuthentication);
    if (isAuthorizationCheckDisabled(authenticationSupplier)) {
      return true;
    }

    return securityContextProvider.isAuthorized(
        resourceId, authenticationSupplier.get(), authorization);
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
