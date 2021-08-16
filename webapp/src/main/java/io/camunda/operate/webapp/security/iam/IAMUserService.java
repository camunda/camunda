/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.security.iam;

import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.security.AbstractUserService;
import io.camunda.operate.webapp.security.OperateURIs;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@Profile(OperateURIs.IAM_AUTH_PROFILE)
public class IAMUserService extends AbstractUserService {

  private static final String EMPTY = "";

  @Override
  public UserDto getCurrentUser() {
    SecurityContext context = SecurityContextHolder.getContext();
    IAMAuthentication tokenAuth = (IAMAuthentication) context.getAuthentication();
    return buildUserDtoFrom(tokenAuth);
  }

  private UserDto buildUserDtoFrom(IAMAuthentication tokenAuth) {
    return new UserDto()
        .setFirstname(EMPTY)
        .setLastname(tokenAuth.getName())
        .setUsername(tokenAuth.getId())
        .setCanLogout(false);
  }
}
