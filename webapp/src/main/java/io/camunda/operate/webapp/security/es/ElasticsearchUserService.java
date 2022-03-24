/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

package io.camunda.operate.webapp.security.es;

import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.security.OperateProfileService;
import io.camunda.operate.webapp.security.RolePermissionService;
import io.camunda.operate.webapp.security.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@Profile(
      "!"     + OperateProfileService.LDAP_AUTH_PROFILE
    + " & ! " + OperateProfileService.SSO_AUTH_PROFILE
    + " & !"  + OperateProfileService.IAM_AUTH_PROFILE
    + " & !"  + OperateProfileService.IDENTITY_AUTH_PROFILE
)
public class ElasticsearchUserService implements UserService<UsernamePasswordAuthenticationToken> {

  @Autowired
  private RolePermissionService rolePermissionService;

  @Override
  public UserDto createUserDtoFrom(
      final UsernamePasswordAuthenticationToken authentication) {
    final User user = (User) authentication.getPrincipal();
    return new UserDto()
        .setUserId(user.getUserId())
        .setDisplayName(user.getDisplayName())
        .setCanLogout(true)
        .setPermissions(rolePermissionService.getPermissions(user.getRoles()));
  }
}
