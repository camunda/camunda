/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.auth;

import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.security.AbstractUserService;
import io.camunda.operate.webapp.security.Permission;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
@Profile("auth-basic")
public class AuthBasicUserService extends AbstractUserService<UsernamePasswordAuthenticationToken> {
  @Override
  public UserDto createUserDtoFrom(final UsernamePasswordAuthenticationToken authentication) {
    final var user = (UserDetails) authentication.getPrincipal();

    return new UserDto()
        .setUserId(String.valueOf(user.getUsername()))
        .setDisplayName(user.getUsername())
        .setCanLogout(false)
        .setPermissions(List.of(Permission.READ, Permission.WRITE));
  }

  @Override
  public String getUserToken(final UsernamePasswordAuthenticationToken authentication) {
    throw new UnsupportedOperationException("Get token is not supported for basic authentication");
  }
}
