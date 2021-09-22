/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.security.es;

import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.rest.exception.UserNotFoundException;
import io.camunda.operate.webapp.security.OperateURIs;
import io.camunda.operate.webapp.security.RolePermissionService;
import io.camunda.operate.webapp.security.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@Profile(OperateURIs.AUTH_PROFILE)
public class ElasticsearchUserService implements UserService<UsernamePasswordAuthenticationToken> {

  @Autowired
  RolePermissionService rolePermissionService;

  @Override
  public UserDto createUserDtoFrom(
      final UsernamePasswordAuthenticationToken authentication) {
    Object maybeUser = authentication.getPrincipal();
    if(maybeUser instanceof User) {
      User user = (User) maybeUser;
      return new UserDto()
          .setUsername(user.getUsername())
          .setFirstname(user.getFirstname())
          .setLastname(user.getLastname())
          .setCanLogout(user.isCanLogout())
          .setPermissions(rolePermissionService
              .getPermissions(user.getRoles()));
    }
    throw new UserNotFoundException(String.format("Couldn't find user in %s", authentication));
  }
}
