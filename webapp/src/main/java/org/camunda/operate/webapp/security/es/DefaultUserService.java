/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.security.es;

import org.camunda.operate.webapp.rest.dto.UserDto;
import org.camunda.operate.webapp.security.AbstractUserService;
import org.camunda.operate.webapp.security.sso.SSOWebSecurityConfig;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@Profile("!" + SSOWebSecurityConfig.SSO_AUTH_PROFILE)
public class DefaultUserService extends AbstractUserService {

  @Override
  public UserDto getCurrentUser() {
    SecurityContext context = SecurityContextHolder.getContext();
    Authentication authentication = context.getAuthentication();
    return UserDto.fromUser((User)authentication.getPrincipal());
  }
}
