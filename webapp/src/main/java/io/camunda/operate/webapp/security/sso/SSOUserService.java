/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.security.sso;

import io.camunda.operate.webapp.security.UserService;
import io.camunda.operate.webapp.security.RolePermissionService;
import java.util.Map;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.security.OperateURIs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import com.auth0.jwt.interfaces.Claim;

@Component
@Profile(OperateURIs.SSO_AUTH_PROFILE)
public class SSOUserService implements UserService<TokenAuthentication> {

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  RolePermissionService rolePermissionService;

  @Override
  public UserDto createUserDtoFrom(
      final TokenAuthentication authentication) {
    Map<String, Claim> claims = authentication.getClaims();
    String name = "No name";
    if (claims.containsKey(operateProperties.getAuth0().getNameKey())) {
      name = claims.get(operateProperties.getAuth0().getNameKey()).asString();
    }
    return new UserDto()
        .setUserId(authentication.getName())
        .setDisplayName(name)
        .setCanLogout(false)
        .setPermissions(
            rolePermissionService.getPermissions(authentication.getRoles()));
  }
}
