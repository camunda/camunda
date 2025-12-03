/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.security;

import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.SecurityContext;
import io.camunda.security.auth.SecurityContext.Builder;
import io.camunda.security.auth.condition.AuthorizationCondition;

public class SecurityContextProvider {

  public SecurityContext provideSecurityContext(
      final CamundaAuthentication authentication, final Authorization<?> authorization) {
    return new Builder()
        .withAuthentication(authentication)
        .withAuthorization(authorization)
        .build();
  }

  public SecurityContext provideSecurityContext(
      final CamundaAuthentication authentication,
      final AuthorizationCondition authorizationCondition) {
    return new Builder()
        .withAuthentication(authentication)
        .withAuthorizationCondition(authorizationCondition)
        .build();
  }

  public SecurityContext provideSecurityContext(final CamundaAuthentication authentication) {
    return provideSecurityContext(authentication, (AuthorizationCondition) null);
  }
}
