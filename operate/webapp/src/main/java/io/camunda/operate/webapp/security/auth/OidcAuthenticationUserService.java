/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.auth;

import io.camunda.authentication.ConditionalOnAuthenticationMethod;
import io.camunda.authentication.entity.CamundaOidcUser;
import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.security.AbstractUserService;
import io.camunda.operate.webapp.security.Permission;
import io.camunda.operate.webapp.security.sso.TokenAuthentication;
import io.camunda.security.entity.AuthenticationMethod;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
@Profile("consolidated-auth")
public class OidcAuthenticationUserService
    extends AbstractUserService<AbstractAuthenticationToken> {

  @Override
  public UserDto createUserDtoFrom(final AbstractAuthenticationToken abstractAuthentication) {
    final var camundaUser =
        Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
            .map(Authentication::getPrincipal)
            .map(principal -> principal instanceof final CamundaOidcUser user ? user : null);

    if (camundaUser.isEmpty()) {
      throw new IllegalStateException("No user found in the security context");
    }

    return getUserDtoFor(camundaUser.get());
  }

  @Override
  public String getUserToken(final AbstractAuthenticationToken authentication) {
    if (authentication instanceof TokenAuthentication) {
      return ((TokenAuthentication) authentication).getAccessToken();
    } else {
      throw new UnsupportedOperationException(
          "Not supported for token class: " + authentication.getClass().getName());
    }
  }

  private UserDto getUserDtoFor(final CamundaOidcUser camundaOidcUser) {
    return new UserDto()
        .setUserId(camundaOidcUser.getName())
        .setDisplayName(camundaOidcUser.getDisplayName())
        .setCanLogout(false)
        .setPermissions(List.of(Permission.READ, Permission.WRITE))
        .setC8Links(Collections.emptyMap());
  }
}
