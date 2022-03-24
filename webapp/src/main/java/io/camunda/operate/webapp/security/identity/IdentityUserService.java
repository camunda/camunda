/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.security.identity;

import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.security.OperateProfileService;
import io.camunda.operate.webapp.security.UserService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile(OperateProfileService.IDENTITY_AUTH_PROFILE)
public class IdentityUserService implements UserService<IdentityAuthentication> {

  @Override
  public UserDto createUserDtoFrom(
      final IdentityAuthentication authentication) {
    return new UserDto()
        .setUserId(authentication.getId())
        .setDisplayName(authentication.getName())
        .setCanLogout(true)
        .setPermissions(authentication.getPermissions());
  }

}
