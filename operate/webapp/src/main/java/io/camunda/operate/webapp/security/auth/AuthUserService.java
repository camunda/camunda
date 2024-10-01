/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.auth;

import io.camunda.authentication.entity.CamundaUser;
import io.camunda.operate.OperateProfileService;
import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.security.AbstractUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@Profile({
  "!"
      + OperateProfileService.LDAP_AUTH_PROFILE
      + " & ! "
      + OperateProfileService.SSO_AUTH_PROFILE
      + " & !"
      + OperateProfileService.IDENTITY_AUTH_PROFILE
})
public class AuthUserService extends AbstractUserService<UsernamePasswordAuthenticationToken> {

  @Autowired private RolePermissionService rolePermissionService;

  @Override
  public UserDto createUserDtoFrom(final UsernamePasswordAuthenticationToken authentication) {
    final CamundaUser user = (CamundaUser) authentication.getPrincipal();
    return new UserDto()
        .setUserId(user.getUserId())
        .setDisplayName(user.getDisplayName())
        .setCanLogout(true)
        .setPermissions(
            rolePermissionService.getPermissions(
                user.getRoles().stream().map(Role::fromString).toList()));
  }

  @Override
  public String getUserToken(final UsernamePasswordAuthenticationToken authentication) {
    throw new UnsupportedOperationException(
        "Get token is not supported for Elasticsearch authentication");
  }
}
