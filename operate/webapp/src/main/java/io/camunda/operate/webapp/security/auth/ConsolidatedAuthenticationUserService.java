/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.auth;

import io.camunda.authentication.entity.CamundaPrincipal;
import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.security.Permission;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
@Profile("consolidated-auth")
public class ConsolidatedAuthenticationUserService extends AuthUserService {

  @Override
  public UserDto createUserDtoFrom(final Authentication authentication) {
    if (authentication.getPrincipal() instanceof final CamundaPrincipal camundaPrincipal) {
      return new UserDto()
          .setUserId(camundaPrincipal.getPrincipalName())
          .setDisplayName(camundaPrincipal.getDisplayName())
          .setCanLogout(true)
          .setPermissions(List.of(Permission.READ, Permission.WRITE));
    }
    throw new UsernameNotFoundException(authentication.getPrincipal().toString());
  }
}
