/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.consolidatedAuth;

import io.camunda.operate.OperateProfileService;
import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.security.AbstractUserService;
import io.camunda.operate.webapp.security.Permission;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
@Profile(OperateProfileService.CONSOLIDATED_AUTH)
public class ConsolidatedAuthenticationUserService extends AbstractUserService<Authentication> {

  private final CamundaAuthenticationProvider authenticationProvider;

  public ConsolidatedAuthenticationUserService(
      final CamundaAuthenticationProvider authenticationProvider) {
    this.authenticationProvider = authenticationProvider;
  }

  @Override
  public UserDto createUserDtoFrom(final Authentication authentication) {
    final var camundaAuthentication = authenticationProvider.getCamundaAuthentication();
    if (camundaAuthentication == null) {
      throw new UsernameNotFoundException(authentication.getPrincipal().toString());
    }
    return new UserDto()
        .setUserId(camundaAuthentication.getUsername())
        .setDisplayName(camundaAuthentication.getDisplayName())
        .setCanLogout(true)
        .setPermissions(List.of(Permission.READ, Permission.WRITE));
  }

  @Override
  public String getUserToken(final Authentication authentication) {
    throw new UnsupportedOperationException(
        "Get token is not supported for consolidated-auth authentication");
  }
}
