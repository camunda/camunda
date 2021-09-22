/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.security.iam;

import static io.camunda.operate.util.CollectionUtil.map;

import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.security.OperateURIs;
import io.camunda.operate.webapp.security.Role;
import io.camunda.operate.webapp.security.UserService;
import io.camunda.operate.webapp.security.RolePermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile(OperateURIs.IAM_AUTH_PROFILE)
public class IAMUserService implements UserService<IAMAuthentication> {

  private static final String EMPTY = "";

  @Autowired
  RolePermissionService rolePermissionService;
  @Override
  public UserDto createUserDtoFrom(
      final IAMAuthentication authentication) {
    return new UserDto()
        .setFirstname(EMPTY)
        .setLastname(authentication.getName())
        .setUsername(authentication.getId())
        .setCanLogout(false)
        .setPermissions(rolePermissionService
            .getPermissions(authentication.getRoles()));
  }

}
