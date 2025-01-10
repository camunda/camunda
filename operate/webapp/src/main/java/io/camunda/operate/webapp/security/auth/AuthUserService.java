/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.auth;

import io.camunda.authentication.entity.CamundaUserDTO;
import io.camunda.authentication.service.CamundaUserService;
import io.camunda.operate.OperateProfileService;
import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.security.AbstractUserService;
import io.camunda.operate.webapp.security.Permission;
import java.util.List;
import java.util.Optional;
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

  @Autowired private Optional<CamundaUserService> camundaUserService;

  @Override
  public UserDto getCurrentUser() {
    final CamundaUserDTO currentUser =
        camundaUserService
            .orElseThrow(() -> new RuntimeException("CamundaUserService is missing"))
            .getCurrentUser();
    return new UserDto()
        .setUserId(currentUser.userId())
        .setDisplayName(currentUser.displayName())
        .setPermissions(List.of(Permission.READ, Permission.WRITE))
        .setCanLogout(currentUser.canLogout());
  }

  @Override
  public String getUserToken(final UsernamePasswordAuthenticationToken authentication) {
    throw new UnsupportedOperationException(
        "Get token is not supported for Elasticsearch authentication");
  }

  @Override
  public UserDto createUserDtoFrom(final UsernamePasswordAuthenticationToken authentication) {
    return null;
  }
}
