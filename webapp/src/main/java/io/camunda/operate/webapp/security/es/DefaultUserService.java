/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.security.es;

import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.security.AbstractUserService;
import io.camunda.operate.webapp.security.OperateURIs;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@Profile("!" + OperateURIs.LDAP_AUTH_PROFILE + " & ! " + OperateURIs.SSO_AUTH_PROFILE)
public class DefaultUserService extends AbstractUserService {

  @Override
  public UserDto getCurrentUser() {
    SecurityContext context = SecurityContextHolder.getContext();
    Authentication authentication = context.getAuthentication();
    return UserDto.fromUser((User)authentication.getPrincipal());
  }
}
