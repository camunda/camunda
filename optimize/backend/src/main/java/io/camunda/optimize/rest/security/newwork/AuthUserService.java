/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.newwork;

import io.camunda.authentication.entity.CamundaUser;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@Profile({
  "!"
      + OptimizeProfileService.LDAP_AUTH_PROFILE
      + " & ! "
      + OptimizeProfileService.SSO_AUTH_PROFILE
      + " & !"
      + OptimizeProfileService.IDENTITY_AUTH_PROFILE
})
public class AuthUserService extends AbstractUserService<UsernamePasswordAuthenticationToken> {

  @Override
  public UserServiceUserDto createUserDtoFrom(
      final UsernamePasswordAuthenticationToken authentication) {
    final CamundaUser user = (CamundaUser) authentication.getPrincipal();
    return new UserServiceUserDto()
        .setUserId(user.getUserId())
        .setName(user.getName())
        .setDisplayName(user.getDisplayName())
        .setCanLogout(true)
        .setEmail(user.getEmail());
  }

  @Override
  public String getUserToken(final UsernamePasswordAuthenticationToken authentication) {
    throw new UnsupportedOperationException(
        "Get token is not supported for Elasticsearch authentication");
  }
}
